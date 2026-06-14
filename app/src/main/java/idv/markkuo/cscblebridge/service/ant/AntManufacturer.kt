package idv.markkuo.cscblebridge.service.ant

/**
 * Maps ANT+/FIT manufacturer IDs (as reported on the common "manufacturer
 * identification" data page) to human readable names.
 *
 * Only a subset of the official FIT SDK manufacturer list is included here -
 * the brands most commonly seen on cycling/training sensors. Unknown IDs fall
 * back to "Manufacturer #<id>" so a wrong name is never shown. Extend the map
 * below when a new manufacturer ID shows up in the wild.
 */
object AntManufacturer {
    private val names = mapOf(
            1 to "Garmin",
            6 to "SRM",
            7 to "Quarq",
            9 to "Saris (CycleOps)",
            13 to "Dynastream",
            15 to "Dynastream",
            16 to "Timex",
            23 to "Suunto",
            29 to "Peaksware",
            32 to "Wahoo Fitness",
            40 to "Concept2",
            63 to "Hammerhead",
            64 to "Kinetic by Kurt",
            65 to "Shimano",
            68 to "ThinkRider",
            69 to "Stages Cycling",
            76 to "Moxy",
            89 to "Tacx",
            123 to "Polar",
            260 to "Zwift",
            263 to "Favero Electronics",
            267 to "Bryton"
    )

    /** Returns a display name for the given manufacturer ID, or null if id <= 0 (unknown/not reported). */
    fun name(manufacturerId: Int): String? {
        if (manufacturerId <= 0) return null
        return names[manufacturerId] ?: "Manufacturer #$manufacturerId"
    }
}
