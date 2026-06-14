package idv.markkuo.cscblebridge.antrecyclerview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import idv.markkuo.cscblebridge.R
import idv.markkuo.cscblebridge.service.ant.AntDevice
import idv.markkuo.cscblebridge.service.ble.BleServiceType

class AntDeviceRecyclerViewAdapter(private val deviceSelected: (device: AntDevice) -> Unit): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_DEVICE = 1
    }

    // A flat list mixing section headers and device cards, rebuilt on every update.
    private sealed class Row {
        data class Header(val titleRes: Int) : Row()
        data class Device(val device: AntDevice, val isBroadcasting: Boolean) : Row()
    }

    private val rows = ArrayList<Row>()

    // Device the user just tapped, shown as "Switching…" until the selection
    // actually flips to it (optimistic feedback, independent of backend timing).
    private var switchingDeviceId: Int? = null

    // Wraps the external callback so a tap immediately shows feedback before the
    // (throttled) backend update comes back.
    private val onDeviceTapped: (AntDevice) -> Unit = { device ->
        switchingDeviceId = device.deviceId
        notifyDataSetChanged()
        deviceSelected(device)
    }

    fun updateDevices(devices: List<AntDevice>, selectedDevices: Map<BleServiceType, List<Int>>) {
        val broadcastIds = selectedDevices.values.flatten().toHashSet()
        val broadcasting = devices.filter { broadcastIds.contains(it.deviceId) }
        val available = devices.filter { !broadcastIds.contains(it.deviceId) }

        // The switch completed (or the device vanished): clear the pending feedback.
        switchingDeviceId?.let { id ->
            if (broadcastIds.contains(id) || devices.none { it.deviceId == id }) {
                switchingDeviceId = null
            }
        }

        rows.clear()
        if (broadcasting.isNotEmpty()) {
            rows.add(Row.Header(R.string.section_broadcasting))
            broadcasting.forEach { rows.add(Row.Device(it, true)) }
        }
        if (available.isNotEmpty()) {
            rows.add(Row.Header(R.string.section_available))
            available.forEach { rows.add(Row.Device(it, false)) }
        }
        // could do this better, but it seems to perform well enough with so few items
        notifyDataSetChanged()
    }

    /** True when the item at [position] is a full-width section header (used by the grid span lookup). */
    fun isHeader(position: Int): Boolean = position in rows.indices && rows[position] is Row.Header

    override fun getItemViewType(position: Int): Int =
            if (rows[position] is Row.Header) TYPE_HEADER else TYPE_DEVICE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.section_header, parent, false)
            HeaderViewHolder(view)
        } else {
            AntDeviceViewHolder(AntDeviceView(parent.context))
        }
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderViewHolder).title.setText(row.titleRes)
            is Row.Device -> {
                val isSwitching = !row.isBroadcasting && row.device.deviceId == switchingDeviceId
                (holder as AntDeviceViewHolder).view.bind(row.device, row.isBroadcasting, isSwitching, onDeviceTapped)
            }
        }
    }

    private class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.section_header_title)
    }
}
