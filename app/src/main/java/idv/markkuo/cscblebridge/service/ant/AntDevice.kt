package idv.markkuo.cscblebridge.service.ant

import idv.markkuo.cscblebridge.service.ble.BleServiceType

sealed class AntDevice(val deviceId: Int, val deviceName: String, val typeName: String, val bleType: BleServiceType) {

    // Common device metadata, populated from the ANT+ common data pages when the
    // sensor broadcasts them. They are not part of the data class constructors so the
    // existing init() signatures stay unchanged; they default to "not reported".
    var connectionState: String? = null   // human readable link state (e.g. "Connected", "Searching")
    var manufacturerId: Int = 0           // FIT manufacturer id, 0 = not reported
    var modelNumber: Int = 0
    var hardwareRevision: Int = 0
    var softwareRevision: Int = 0
    var serialNumber: Long = 0L
    var batteryVoltage: Double? = null    // volts, null = not reported
    var batteryStatus: String? = null     // e.g. "GOOD", "LOW"; null = not reported
    var rssi: Int? = null                 // dBm, null = not reported by the radio

    /** Manufacturer display name, or null when not reported. */
    val manufacturerName: String?
        get() = AntManufacturer.name(manufacturerId)

    data class BsdDevice(
            private val id: Int,
            private val name: String,
            var lastSpeed: Float,
            var cumulativeWheelRevolution: Long,
            var lastWheelEventTime: Int,
            var lastSpeedTimestamp: Long
    ): AntDevice(id, name, "ANT+ Bike Speed", BleServiceType.CscService) {
        override fun getDataString(): String {
            return "Speed: $lastSpeed, RPM: $cumulativeWheelRevolution"
        }
    }

    data class BcDevice(
            private val id: Int,
            private val name: String,
            var cadence: Int,
            var cumulativeCrankRevolution: Long,
            var crankEventTime: Long,
            var cadenceTimestamp: Long
    ) : AntDevice(id, name, "ANT+ Bike Cadence", BleServiceType.CscService) {
        override fun getDataString(): String {
            return "Cadence: $cadence, Crank Revolution: $cumulativeCrankRevolution"
        }
    }

    data class SSDevice(
            private val id: Int,
            private val name: String,
            var ssDistance: Long,
            var ssDistanceTimestamp: Long,
            var ssSpeed: Float,
            var ssSpeedTimestamp: Long,
            var stridePerMinute: Long,
            var stridePerMinuteTimestamp: Long
    ) : AntDevice(id, name, "ANT+ Stride SDM", BleServiceType.RscService) {
        override fun getDataString(): String {
            return "Speed: $ssSpeed, Stride/Min: $stridePerMinute"
        }
    }

    data class HRDevice(
            private val id: Int,
            private val name: String,
            var hr: Int,
            var hrTimestamp: Long
    ) : AntDevice(id, name, "ANT+ Heart Rate", BleServiceType.HrService) {
        override fun getDataString(): String {
            return "Heart Rate: $hr"
        }
    }

    data class PwrDevice(
            private val id: Int,
            private val name: String,
            var instantaneousPower: Int,
            var instantaneousCadence: Int,
            var cumulativeCrankRevolution: Long,
            var lastCrankEventTime: Int,
            var pwrTimestamp: Long
    ) : AntDevice(id, name, "ANT+ Bike Power", BleServiceType.CpService) {
        // Left/right pedal power balance in percent, null = not reported by the meter.
        var pedalBalanceLeft: Int? = null
        var pedalBalanceRight: Int? = null

        override fun getDataString(): String {
            return "Power: $instantaneousPower W, Cadence: $instantaneousCadence"
        }
    }

    abstract fun getDataString(): String
}
