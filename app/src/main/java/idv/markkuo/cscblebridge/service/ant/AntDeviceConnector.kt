package idv.markkuo.cscblebridge.service.ant

import android.content.Context
import android.util.Log
import com.dsi.ant.plugins.antplus.pcc.defines.BatteryStatus
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc.IDeviceStateChangeReceiver
import com.dsi.ant.plugins.antplus.pccbase.AntPlusCommonPcc
import com.dsi.ant.plugins.antplus.pccbase.AntPlusLegacyCommonPcc
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle
import java.util.concurrent.ConcurrentHashMap

abstract class AntDeviceConnector<T: AntPluginPcc, Data: AntDevice>(private val context: Context, internal val listener: DeviceManagerListener<Data>) {

    interface DeviceManagerListener<Data> {
        fun onDeviceStateChanged(result: RequestAccessResult, deviceState: DeviceState)
        fun onDataUpdated(data: Data)
        fun onCombinedSensor(antDeviceConnector: AntDeviceConnector<*, *>, deviceId: Int)
    }

    companion object {
        private const val TAG = "AntDeviceManager"
    }

    private val devices = ConcurrentHashMap<Int, Data>()

    private var releaseHandle: PccReleaseHandle<T>? = null

    private var deviceStateChangedReceiver: IDeviceStateChangeReceiver = IDeviceStateChangeReceiver { state ->
        Log.d(TAG, "Device State Changed ${state.name}")
        // Propagate the link state to every known device of this connector so the UI
        // can show whether the sensor is connected, searching or lost.
        val readable = readableDeviceState(state)
        devices.values.forEach { device ->
            device.connectionState = readable
            listener.onDataUpdated(device)
        }
    }

    private fun readableDeviceState(state: DeviceState): String = when (state) {
        DeviceState.TRACKING -> "Connected"
        DeviceState.SEARCHING -> "Searching"
        DeviceState.DEAD -> "Signal lost"
        DeviceState.CLOSED -> "Closed"
        DeviceState.PROCESSING_REQUEST -> "Connecting"
        else -> state.name
    }

    private val resultReceiver = AntPluginPcc.IPluginAccessResultReceiver {
        pcc: T?, requestAccessResult: RequestAccessResult, deviceState: DeviceState ->
        when (requestAccessResult) {
            RequestAccessResult.SUCCESS -> {
                if (pcc != null) {
                    Log.d(TAG, "${pcc.deviceName}: ${deviceState})")
                    subscribeToEvents(pcc)
                }
            }
            RequestAccessResult.USER_CANCELLED -> {
                Log.d(TAG, "Ant Device Closed: $requestAccessResult")
            }
            else -> {
                Log.w(TAG, "Ant Device State changed: $deviceState, resultCode: $requestAccessResult")
            }
        }
        listener.onDeviceStateChanged(requestAccessResult, deviceState)
    }

    abstract fun requestAccess(
            context: Context,
            resultReceiver: AntPluginPcc.IPluginAccessResultReceiver<T>,
            stateChangedReceiver: IDeviceStateChangeReceiver,
            deviceNumber: Int = 0
    ): PccReleaseHandle<T>

    abstract fun subscribeToEvents(pcc: T)
    abstract fun init(deviceNumber: Int, deviceName: String): Data

    fun startSearch() {
        stopSearch()
        releaseHandle = requestAccess(context, resultReceiver, deviceStateChangedReceiver)
    }

    fun stopSearch() {
        releaseHandle?.close()
        devices.clear()
    }

    internal fun getDevice(pcc: T): Data {
        if (!devices.containsKey(pcc.antDeviceNumber)) {
            devices[pcc.antDeviceNumber] = init(pcc.antDeviceNumber, pcc.deviceName)
        }
        return devices[pcc.antDeviceNumber]!!
    }

    /**
     * Subscribes to the common data pages available on modern ANT+ sensors
     * (heart rate, bike power, stride). Fills in manufacturer/model/firmware,
     * serial number, battery status and RSSI on the given device. The device is
     * a stable reference held in [devices], so the closures keep updating the
     * same object the measurement callbacks use.
     */
    protected fun subscribeCommonPages(pcc: AntPlusCommonPcc, device: Data) {
        pcc.subscribeManufacturerIdentificationEvent { _, _, hardwareRevision, manufacturerID, modelNumber ->
            device.hardwareRevision = hardwareRevision
            device.manufacturerId = manufacturerID
            device.modelNumber = modelNumber
            listener.onDataUpdated(device)
        }
        pcc.subscribeProductInformationEvent { _, _, _, mainSoftwareRevision, serialNumber ->
            device.softwareRevision = mainSoftwareRevision
            device.serialNumber = serialNumber
            listener.onDataUpdated(device)
        }
        pcc.subscribeBatteryStatusEvent { _, _, _, batteryVoltage, batteryStatus, _, _, _ ->
            applyBattery(device, batteryVoltage, batteryStatus)
            listener.onDataUpdated(device)
        }
        pcc.subscribeRssiEvent { _, _, rssi ->
            device.rssi = rssi
            listener.onDataUpdated(device)
        }
    }

    /**
     * Subscribes to the legacy common data pages used by the older bike speed
     * and bike cadence profiles. These report manufacturer/serial and
     * version/model on different pages than the modern common profile.
     */
    protected fun subscribeLegacyCommonPages(pcc: AntPlusLegacyCommonPcc, device: Data) {
        pcc.subscribeManufacturerAndSerialEvent { _, _, manufacturerID, serialNumber ->
            device.manufacturerId = manufacturerID
            device.serialNumber = serialNumber.toLong()
            listener.onDataUpdated(device)
        }
        pcc.subscribeVersionAndModelEvent { _, _, hardwareVersion, softwareVersion, modelNumber ->
            device.hardwareRevision = hardwareVersion
            device.softwareRevision = softwareVersion
            device.modelNumber = modelNumber
            listener.onDataUpdated(device)
        }
        pcc.subscribeRssiEvent { _, _, rssi ->
            device.rssi = rssi
            listener.onDataUpdated(device)
        }
    }

    private fun applyBattery(device: Data, voltage: java.math.BigDecimal?, status: BatteryStatus?) {
        // The ANT+ radio reports an "invalid" battery status when the sensor does
        // not broadcast battery data; ignore both fields in that case.
        if (status == null || status == BatteryStatus.INVALID || status == BatteryStatus.UNRECOGNIZED) {
            return
        }
        device.batteryStatus = status.name
        val v = voltage?.toDouble()
        // A plausible single-cell/coin-cell voltage; filter out the sentinel values.
        device.batteryVoltage = if (v != null && v in 0.5..6.0) v else null
    }
}