package idv.markkuo.cscblebridge.antrecyclerview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import idv.markkuo.cscblebridge.R
import idv.markkuo.cscblebridge.service.ant.AntDevice

class AntDeviceView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val nameView: TextView
    private val typeView: TextView
    private val dataView: TextView
    private val detailsView: TextView
    private val background: LinearLayout
    private val broadcastButtonView: BroadcastButtonView

    init {
        inflate(context, R.layout.ant_list_item, this)
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        nameView = findViewById(R.id.ant_device_name)
        typeView = findViewById(R.id.ant_device_type)
        dataView = findViewById(R.id.ant_device_data)
        detailsView = findViewById(R.id.ant_device_details)
        background = findViewById(R.id.ant_device_background)
        broadcastButtonView = findViewById(R.id.broadcast_button_view)
    }

    fun bind(antDevice: AntDevice, isSelected: Boolean, isSwitching: Boolean, onClickListener: (antDevice: AntDevice) -> Unit) {
        val color = if (isSelected) {
            broadcastButtonView.setState(BroadcastButtonView.BroadcastButtonViewState.Broadcasting)
            context.resources.getColor(R.color.textAccent)
        } else {
            broadcastButtonView.setState(
                    if (isSwitching) BroadcastButtonView.BroadcastButtonViewState.Switching
                    else BroadcastButtonView.BroadcastButtonViewState.NotSelected)
            context.resources.getColor(R.color.textPrimary)
        }

        nameView.text = antDevice.deviceName
        nameView.setTextColor(color)

        // Type, with the connection state appended when known: "ANT+ Bike Power · Connected"
        typeView.text = antDevice.connectionState?.let { "${antDevice.typeName} · $it" } ?: antDevice.typeName

        dataView.text = antDevice.getDataString()

        val details = buildDetails(antDevice)
        if (details.isEmpty()) {
            detailsView.visibility = View.GONE
        } else {
            detailsView.visibility = View.VISIBLE
            detailsView.text = details
        }

        background.setOnClickListener { onClickListener(antDevice) }
        broadcastButtonView.setClickListener { onClickListener(antDevice) }
    }

    /** Builds the multi-line detail block, skipping any field the sensor did not report. */
    private fun buildDetails(d: AntDevice): String {
        val lines = ArrayList<String>()
        fun add(labelRes: Int, value: String?) {
            if (!value.isNullOrEmpty()) lines.add("${context.getString(labelRes)}: $value")
        }

        add(R.string.label_manufacturer, d.manufacturerName)
        if (d.modelNumber > 0) add(R.string.label_model, "#${d.modelNumber}")
        // 255 (0xFF) is the "not reported" sentinel for the revision byte.
        if (d.softwareRevision in 1..254) add(R.string.label_firmware, "v${d.softwareRevision}")
        if (d.hardwareRevision in 1..254) add(R.string.label_hardware, "v${d.hardwareRevision}")
        add(R.string.label_device_id, d.deviceId.toString())
        if (d.serialNumber > 0) add(R.string.label_serial, d.serialNumber.toString())

        val battery = batteryText(d)
        add(R.string.label_battery, battery)
        d.rssi?.let { add(R.string.label_signal, "$it dBm") }

        if (d is AntDevice.PwrDevice) {
            val left = d.pedalBalanceLeft
            val right = d.pedalBalanceRight
            if (left != null && right != null) {
                add(R.string.label_balance, "$left% / $right%")
            }
        }

        return lines.joinToString("\n")
    }

    private fun batteryText(d: AntDevice): String? {
        val status = d.batteryStatus ?: return null
        val voltage = d.batteryVoltage
        return if (voltage != null) {
            String.format("%s (%.2fV)", status, voltage)
        } else {
            status
        }
    }
}
