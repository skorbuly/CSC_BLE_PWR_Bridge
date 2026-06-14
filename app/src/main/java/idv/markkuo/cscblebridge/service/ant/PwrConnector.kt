package idv.markkuo.cscblebridge.service.ant

import android.content.Context
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle

class PwrConnector(context: Context, listener: DeviceManagerListener<AntDevice.PwrDevice>): AntDeviceConnector<AntPlusBikePowerPcc, AntDevice.PwrDevice>(context, listener) {

    // Per-device state for synthesizing BLE crank revolution data from instantaneous cadence.
    // Most pedal/crank power meters broadcast "power-only" pages plus instantaneous cadence
    // (RPM), but no raw crank revolution count. The BLE Cycling Power consumer derives cadence
    // as RPM = 60 * deltaCrankRevolutions / deltaEventTimeSeconds, so the revolution count and
    // the event time must advance together and only on whole revolutions, exactly like a real
    // sensor. We therefore only increment the revolution counter when a full revolution has
    // actually completed, and stamp the event time at the moment that revolution finished.
    // Between completed revolutions both values stay constant, so the consumer holds the RPM.
    private val lastTimestamp = HashMap<Int, Long>()        // last ANT+ cadence event time (ms)
    private val revFraction = HashMap<Int, Double>()        // fractional progress to next revolution
    private val revCount = HashMap<Int, Long>()             // cumulative whole crank revolutions
    private val eventTime1024 = HashMap<Int, Double>()      // event time of last completed rev, in 1/1024s

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

        // Instantaneous cadence (RPM). Used for display and to synthesize the BLE crank
        // revolution count + last crank event time.
        pcc.subscribeInstantaneousCadenceEvent { estTimestamp, _, _, instantaneousCadence ->
            val device = getDevice(pcc)
            val id = pcc.antDeviceNumber
            device.instantaneousCadence = instantaneousCadence

            val prevTs = lastTimestamp[id]
            if (prevTs != null && estTimestamp > prevTs && instantaneousCadence > 0) {
                val elapsedSec = (estTimestamp - prevTs) / 1000.0
                val secPerRev = 60.0 / instantaneousCadence

                var frac = (revFraction[id] ?: 0.0) + instantaneousCadence * elapsedSec / 60.0
                var revs = revCount[id] ?: 0L
                var evt = eventTime1024[id] ?: 0.0

                // Complete whole revolutions one at a time, advancing the event time by exactly
                // one revolution's worth of time for each, so revolutions and event time stay
                // perfectly consistent.
                while (frac >= 1.0) {
                    frac -= 1.0
                    revs += 1L
                    evt += secPerRev * 1024.0
                }

                revFraction[id] = frac
                revCount[id] = revs
                eventTime1024[id] = evt

                device.cumulativeCrankRevolution = revs
                device.lastCrankEventTime = (evt.toLong() and 0xFFFFL).toInt()
            }
            lastTimestamp[id] = estTimestamp

            device.pwrTimestamp = estTimestamp
            listener.onDataUpdated(device)
        }

        // Optional left/right pedal power balance, reported by dual-sided meters.
        // Single-sided meters / trainers send an out-of-range sentinel (e.g. 0xFF)
        // instead of a real percentage; only accept a valid 0..100 value.
        pcc.subscribePedalPowerBalanceEvent { _, _, rightPedalIndicator, pedalPowerPercentage ->
            if (pedalPowerPercentage in 0..100) {
                val device = getDevice(pcc)
                if (rightPedalIndicator) {
                    device.pedalBalanceRight = pedalPowerPercentage
                    device.pedalBalanceLeft = 100 - pedalPowerPercentage
                } else {
                    device.pedalBalanceLeft = pedalPowerPercentage
                    device.pedalBalanceRight = 100 - pedalPowerPercentage
                }
                listener.onDataUpdated(device)
            }
        }

        // Common pages: manufacturer/model/firmware, serial, battery, RSSI.
        subscribeCommonPages(pcc, getDevice(pcc))
    }

    override fun init(deviceNumber: Int, deviceName: String): AntDevice.PwrDevice {
        return AntDevice.PwrDevice(deviceNumber, deviceName, 0, 0, 0L, 0, 0L)
    }
}
