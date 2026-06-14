package idv.markkuo.cscblebridge.service

import android.content.Context
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult
import idv.markkuo.cscblebridge.service.ant.*
import idv.markkuo.cscblebridge.service.ble.BleServer
import idv.markkuo.cscblebridge.service.ble.BleServiceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.ArrayList

class AntToBleBridge {

    companion object {
        private const val PREFS = "antcast_prefs"
    }

    private val antConnectors = ArrayList<AntDeviceConnector<*, *>>()
    private var bleServer: BleServer? = null
    private var appContext: Context? = null

    val antDevices = hashMapOf<Int, AntDevice>()
    val selectedDevices = hashMapOf<BleServiceType, ArrayList<Int>>()
    var serviceCallback: (() -> Unit)? = null
    var isSearching = false
    var lock = Semaphore(1)

    @Synchronized
    fun startup(service: Context, callback: () -> Unit) {
        appContext = service.applicationContext
        serviceCallback = callback
        stop()
        isSearching = true
        antDevices.clear()
        bleServer = BleServer().apply {
            startServer(service)
        }

        runBlocking {
            lock.withPermit {
                antConnectors.add(createBsdConnector(service, callback))

                antConnectors.add(createBcConnector(service, callback))

                antConnectors.add(HRConnector(service, object: AntDeviceConnector.DeviceManagerListener<AntDevice.HRDevice> {
                    override fun onDeviceStateChanged(result: RequestAccessResult, deviceState: DeviceState) {
                    }

                    override fun onDataUpdated(data: AntDevice.HRDevice) {
                        dataUpdated(data, BleServiceType.HrService, callback) {
                            return@dataUpdated HRConnector(service, this)
                        }
                    }

                    override fun onCombinedSensor(antDeviceConnector: AntDeviceConnector<*, *>, deviceId: Int) {
                        // Not supported
                    }
                }))

                antConnectors.add(SSConnector(service, object: AntDeviceConnector.DeviceManagerListener<AntDevice.SSDevice> {
                    override fun onDeviceStateChanged(result: RequestAccessResult, deviceState: DeviceState) {
                    }

                    override fun onDataUpdated(data: AntDevice.SSDevice) {
                        dataUpdated(data, BleServiceType.RscService, callback) {
                            return@dataUpdated SSConnector(service, this)
                        }
                    }

                    override fun onCombinedSensor(antDeviceConnector: AntDeviceConnector<*, *>, deviceId: Int) {
                        // Not supported
                    }
                }))

                antConnectors.add(PwrConnector(service, object: AntDeviceConnector.DeviceManagerListener<AntDevice.PwrDevice> {
                    override fun onDeviceStateChanged(result: RequestAccessResult, deviceState: DeviceState) {
                    }

                    override fun onDataUpdated(data: AntDevice.PwrDevice) {
                        dataUpdated(data, BleServiceType.CpService, callback) {
                            return@dataUpdated PwrConnector(service, this)
                        }
                    }

                    override fun onCombinedSensor(antDeviceConnector: AntDeviceConnector<*, *>, deviceId: Int) {
                        // Not supported
                    }
                }))

                antConnectors.forEach { connector -> connector.startSearch() }
            }
        }

    }

    private fun createBsdConnector(service: Context, callback: () -> Unit, isCombinedSensor: Boolean = false): BsdConnector {
        return BsdConnector(service, object : AntDeviceConnector.DeviceManagerListener<AntDevice.BsdDevice> {
            override fun onDeviceStateChanged(result: RequestAccessResult, deviceState: DeviceState) {
            }

            override fun onDataUpdated(data: AntDevice.BsdDevice) {
                dataUpdated(data, BleServiceType.CscService, callback) {
                    return@dataUpdated BsdConnector(service, this)
                }
            }

            override fun onCombinedSensor(antDeviceConnector: AntDeviceConnector<*, *>, deviceId: Int) {
                runBlocking {
                    lock.withPermit {
                        val bcConnector = antConnectors.firstOrNull { it is BcConnector } as BcConnector?
                        if (bcConnector?.isCombinedSensor == false) {
                            bcConnector.stopSearch()
                            antConnectors.remove(bcConnector)
                            antConnectors.add(createBcConnector(service, callback, true))
                        }
                    }
                }
            }
        }, isCombinedSensor)
    }

    private fun createBcConnector(service: Context, callback: () -> Unit, isCombinedSensor: Boolean = false): BcConnector {
        return BcConnector(service, object : AntDeviceConnector.DeviceManagerListener<AntDevice.BcDevice> {
            override fun onDeviceStateChanged(result: RequestAccessResult, deviceState: DeviceState) {
            }

            override fun onDataUpdated(data: AntDevice.BcDevice) {
                dataUpdated(data, BleServiceType.CscService, callback) {
                    return@dataUpdated BcConnector(service, this)
                }
            }

            override fun onCombinedSensor(antDeviceConnector: AntDeviceConnector<*, *>, deviceId: Int) {
                runBlocking {
                    lock.withPermit {
                        val bsdConnector = antConnectors.firstOrNull { it is BsdConnector } as BsdConnector?
                        if (bsdConnector?.isCombinedSensor == false) {
                            bsdConnector.stopSearch()
                            antConnectors.remove(bsdConnector)
                            createBsdConnector(service, callback, true)
                        }
                    }
                }
            }
        }, isCombinedSensor)
    }

    @Synchronized
    private fun dataUpdated(data: AntDevice, type: BleServiceType, serviceCallback: () -> Unit, createService: () -> AntDeviceConnector<*, *>) {
        val isNew = !antDevices.containsKey(data.deviceId)
        antDevices[data.deviceId] = data
        bleServer?.updateData(type, data)
        if (isNew) {
            val connector = createService()
            runBlocking {
                lock.withPermit {
                    antConnectors.add(connector)
                }
            }
            connector.startSearch()
        }

        // Auto-select for broadcast. If a device of this exact sensor type is not yet
        // selected, pick the remembered ("preferred") device when it shows up; if there
        // is no saved preference, pick the first one seen. When a preference exists for a
        // different device, wait for it instead of auto-switching to whatever is available
        // — so the bridge never silently jumps to another sensor just because the
        // previously broadcast one is momentarily offline.
        val alreadySelected = selectedDevices[type]?.any { antDevices[it]?.typeName == data.typeName } ?: false
        if (!alreadySelected) {
            val preferred = getPreferredDevice(data.typeName)
            if (preferred == null || preferred == data.deviceId) {
                selectedDevices.getOrPut(type) { arrayListOf() }.add(data.deviceId)
                selectedDevicesUpdated()
            }
        }
        serviceCallback()
    }

    @Synchronized
    fun deviceSelected(data: AntDevice) {
        val arrayList = selectedDevices[data.bleType] ?: arrayListOf()
        val existingDevice = arrayList.firstOrNull { antDevices[it]?.typeName == data.typeName }
        if (existingDevice != null) {
            arrayList.remove(existingDevice)
        }
        arrayList.add(data.deviceId)
        selectedDevices[data.bleType] = arrayList
        // Remember this manual choice so it is restored (and not auto-overridden) later.
        setPreferredDevice(data.typeName, data.deviceId)
        selectedDevicesUpdated()
        serviceCallback?.invoke()
    }

    private fun selectedDevicesUpdated() {
        bleServer?.selectedDevices = selectedDevices
    }

    // Persisted per-sensor-type broadcast preference (keyed by typeName so speed and
    // cadence are tracked separately). Survives service/process restarts.
    private fun getPreferredDevice(typeName: String): Int? {
        val id = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.getInt(prefKey(typeName), -1) ?: -1
        return if (id >= 0) id else null
    }

    private fun setPreferredDevice(typeName: String, deviceId: Int) {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putInt(prefKey(typeName), deviceId)?.apply()
    }

    private fun prefKey(typeName: String) = "preferred_" + typeName.replace(' ', '_')

    fun stop() {
        isSearching = false

        runBlocking {
            withContext(Dispatchers.IO) {
                lock.withPermit {
                    antConnectors.forEach { connector -> connector.stopSearch() }
                    antConnectors.clear()
                    bleServer?.stopServer()

                    serviceCallback = null
                }
            }
        }
    }
}
