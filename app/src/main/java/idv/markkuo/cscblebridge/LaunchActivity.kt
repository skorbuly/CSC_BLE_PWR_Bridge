package idv.markkuo.cscblebridge

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import idv.markkuo.cscblebridge.service.MainService
import idv.markkuo.cscblebridge.service.ant.AntDevice
import idv.markkuo.cscblebridge.service.ble.BleServiceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class LaunchActivity: AppCompatActivity(), MainFragment.ServiceStarter, MainService.MainServiceListener {

    private var mService: MainService? = null
    private var serviceIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launch)
    }

    private var toggleActionView: View? = null

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        toggleActionView = menu.findItem(R.id.action_toggle_bridge).actionView
        toggleActionView?.setOnClickListener {
            if (isSearching()) stopService() else startService()
            updateToggleView()
        }
        updateToggleView()
        return true
    }

    /** Updates the action-bar toggle's icon and label to match the current state. */
    private fun updateToggleView() {
        val view = toggleActionView ?: return
        val searching = isSearching()
        view.findViewById<ImageView>(R.id.toggle_icon)
                .setImageResource(if (searching) R.drawable.ic_stop_red else R.drawable.ic_play_green)
        view.findViewById<TextView>(R.id.toggle_label)
                .setText(if (searching) R.string.stop_service else R.string.start_service)
    }

    override fun onResume() {
        super.onResume()
        startService()
    }

    override fun onStop() {
        unbind()
        super.onStop()
    }

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MainService.LocalBinder
            mService = binder.service
            binder.service.addListener(this@LaunchActivity)
            updateToggleView()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mService?.removeListener(this@LaunchActivity)
            mService = null
        }
    }

    override fun startService() {
        serviceIntent = Intent(this, MainService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, connection, 0)
    }

    override fun stopService() {
        runBlocking {
            withContext(Dispatchers.IO) {
                mService?.stopSearching()
                unbind()
                stopService(serviceIntent)
            }
        }
    }

    private fun unbind() {
        try {
            unbindService(connection)
        } catch (e: IllegalArgumentException) {
            // Expected if not bound
        }
    }

    override fun deviceSelected(antDevice: AntDevice) {
        mService?.deviceSelected(antDevice)
    }

    override fun isSearching(): Boolean = mService?.isSearching ?: false
    override fun searching(isSearching: Boolean) {
        runOnUiThread { updateToggleView() }
    }

    override fun onDevicesUpdated(devices: List<AntDevice>, selectedDevices: Map<BleServiceType, List<Int>>) {
        val mainFragment = mainFragment()
        mainFragment?.setDevices(devices, selectedDevices)
    }

    private fun mainFragment() =
            supportFragmentManager.findFragmentById(R.id.main_fragment) as MainFragment?
}