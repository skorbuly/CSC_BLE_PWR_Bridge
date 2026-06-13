package idv.markkuo.cscblebridge.service.ant

import android.content.Context
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle

class PwrConnector(context: Context, listener: DeviceManagerListener<AntDevice.PwrDevice>): AntDeviceConnector<AntPlusBikePowerPcc, AntDevice.PwrDevice>(context, listener) {

    // Per-device state for synthesizing crank revolution data from instantaneous cadence.
    // Most pedal/crank power meters only broadcast "power-only" pages and instantaneous
    // cadence (RPM), without raw crank torque, so we accumulate revolutions ourselves.
    private val lastCadenceTimestamp = HashMap<Int, Long>()
    private val crankRevAccumulator = HashMap<Int, Double>()

    override fun requestAccess(context: Context, resultReceiver: AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikePowerPcc>, stateChangedReceiver: AntPluginPcc.IDeviceStateChangeReceiver, deviceNumber: Int): PccReleaseHandle<AntPlusBikePowerPcc> {
        return AntPlusBikePowerPcc.requestAccess(context, deviceNumber, 0, resultReceiver, stateChangedReceiver)
    }

    override fun subscribeToEvents(pcc: AntPlusBikePowerPcc) {
        // Calculated (instantaneous) power in watts. Works across all power meter data sources.
        pcc.subscribeCalculatedPowerEvent { estTimestamp, _, _, calculatedPower ->
            val device = getDevice(pcc)
            device.instantaneousPower = calculatedPower.toInt()
            device.pwrTimestamp = estTimestamp
            listener.onDataUpdated(device)
        }

        // Instantaneous cadence (RPM). We use this both for display and to synthesize the
        // cumulative crank revolution count + last crank event time that BLE Cycling Power
        // needs, so that TrainerRoad/Zwift can show cadence.
        pcc.subscribeInstantaneousCadenceEvent { estTimestamp, _, _, instantaneousCadence ->
            val device = getDevice(pcc)
            val id = pcc.antDeviceNumber
            device.instantaneousCadence = instantaneousCadence

            val prevTs = lastCadenceTimestamp[id]
            if (prevTs != null && estTimestamp > prevTs && instantaneousCadence > 0) {
                val elapsedSec = (estTimestamp - prevTs) / 1000.0
                // revolutions added over the elapsed interval at the current RPM
                val added = instantaneousCadence * elapsedSec / 60.0
                val acc = (crankRevAccumulator[id] ?: 0.0) + added
                crankRevAccumulator[id] = acc
                device.cumulativeCrankRevolution = acc.toLong()
                // Last Crank Event Time, unit 1/1024s, uint16 (wraps). Advance it by the same
                // number of revolutions so RPM = deltaRev / deltaTime stays consistent.
                device.lastCrankEventTime = ((acc * 1024.0).toLong() and 0xFFFFL).toInt()
            }
            lastCadenceTimestamp[id] = estTimestamp

            device.pwrTimestamp = estTimestamp
            listener.onDataUpdated(device)
        }
    }

    override fun init(deviceNumber: Int, deviceName: String): AntDevice.PwrDevice {
        return AntDevice.PwrDevice(deviceNumber, deviceName, 0, 0, 0L, 0, 0L)
    }
}
