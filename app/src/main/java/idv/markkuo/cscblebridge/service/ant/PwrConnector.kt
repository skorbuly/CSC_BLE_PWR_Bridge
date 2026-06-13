package idv.markkuo.cscblebridge.service.ant

import android.content.Context
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle

class PwrConnector(context: Context, listener: DeviceManagerListener<AntDevice.PwrDevice>): AntDeviceConnector<AntPlusBikePowerPcc, AntDevice.PwrDevice>(context, listener) {
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

        // Instantaneous cadence (RPM) for display purposes.
        pcc.subscribeInstantaneousCadenceEvent { estTimestamp, _, _, instantaneousCadence ->
            val device = getDevice(pcc)
            device.instantaneousCadence = instantaneousCadence
            device.pwrTimestamp = estTimestamp
            listener.onDataUpdated(device)
        }

        // Raw crank torque data carries the cumulative crank revolution count, which is what
        // the BLE Cycling Power Measurement needs so that apps (TrainerRoad/Zwift) can derive
        // cadence themselves. accumulatedCrankTicks = cumulative crank revolutions.
        pcc.subscribeRawCrankTorqueDataEvent { estTimestamp, _, _, accumulatedCrankTicks, _, _ ->
            val device = getDevice(pcc)
            device.cumulativeCrankRevolution = accumulatedCrankTicks
            // BLE "Last Crank Event Time" is in 1/1024s units (uint16, wraps around).
            // Derive it from the ANT+ estimated timestamp (milliseconds).
            device.lastCrankEventTime = ((estTimestamp * 1024L / 1000L) and 0xFFFFL).toInt()
            device.pwrTimestamp = estTimestamp
            listener.onDataUpdated(device)
        }
    }

    override fun init(deviceNumber: Int, deviceName: String): AntDevice.PwrDevice {
        return AntDevice.PwrDevice(deviceNumber, deviceName, 0, 0, 0L, 0, 0L)
    }
}
