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

        // Instantaneous cadence (RPM), if the power meter provides it.
        pcc.subscribeInstantaneousCadenceEvent { estTimestamp, _, _, instantaneousCadence ->
            val device = getDevice(pcc)
            device.instantaneousCadence = instantaneousCadence
            device.pwrTimestamp = estTimestamp
            listener.onDataUpdated(device)
        }
    }

    override fun init(deviceNumber: Int, deviceName: String): AntDevice.PwrDevice {
        return AntDevice.PwrDevice(deviceNumber, deviceName, 0, 0, 0L)
    }
}
