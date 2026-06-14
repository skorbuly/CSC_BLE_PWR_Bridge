package idv.markkuo.cscblebridge

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import idv.markkuo.cscblebridge.antrecyclerview.AntDeviceRecyclerViewAdapter
import idv.markkuo.cscblebridge.service.ant.AntDevice
import idv.markkuo.cscblebridge.service.ble.BleServiceType

class MainFragment: Fragment() {

    interface ServiceStarter {
        fun startService()
        fun stopService()
        fun deviceSelected(antDevice: AntDevice)
        fun isSearching(): Boolean
    }

    private var antDeviceRecyclerViewAdapter: AntDeviceRecyclerViewAdapter? = null
    private lateinit var searchButton: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val view = inflater.inflate(R.layout.fragment_main, container)

        searchButton = view.findViewById(R.id.searchButton)
        searchButton.setOnClickListener {
            val searching = (requireActivity() as ServiceStarter).isSearching()
            if (searching) {
                (activity as ServiceStarter).stopService()
            } else {
                (activity as ServiceStarter).startService()
            }
            updateSearchButtonText(!searching)
        }
        updateSearchButtonText((requireActivity() as ServiceStarter).isSearching())

        val recyclerView = view.findViewById<RecyclerView>(R.id.main_recycler_view)
        // Responsive grid: 1 column on phones, 2-3+ on tablets depending on width.
        recyclerView.layoutManager = GridLayoutManager(view.context, calculateSpanCount(view.context))
        antDeviceRecyclerViewAdapter = AntDeviceRecyclerViewAdapter {
            (activity as ServiceStarter).deviceSelected(it)
        }
        recyclerView.adapter = antDeviceRecyclerViewAdapter
        return view
    }

    /** Derives the grid column count from the available screen width and a minimum card width. */
    private fun calculateSpanCount(context: Context): Int {
        val metrics = context.resources.displayMetrics
        val screenWidthDp = metrics.widthPixels / metrics.density
        val cardMinWidthDp = context.resources.getDimension(R.dimen.card_min_width) / metrics.density
        return maxOf(1, (screenWidthDp / cardMinWidthDp).toInt())
    }

    private fun updateSearchButtonText(searching: Boolean) {
        searchButton.text = if (searching) getString(R.string.stop_service) else getString(R.string.start_service)
    }

    fun setDevices(devices: List<AntDevice>, selectedDevices: Map<BleServiceType, List<Int>>) {
        activity?.runOnUiThread {
            antDeviceRecyclerViewAdapter?.updateDevices(devices, selectedDevices)
        }
    }

    fun searching(isSearching: Boolean) {
        updateSearchButtonText(isSearching)
    }
}