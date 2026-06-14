package idv.markkuo.cscblebridge.antrecyclerview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import idv.markkuo.cscblebridge.R

class BroadcastButtonView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class BroadcastButtonViewState {
        NotSelected,
        Broadcasting,
        Switching
    }

    private val broadcastButtonBroadcast: Button
    private val broadcastingIndicator: LinearLayout

    init {
        inflate(context, R.layout.broadcast_view, this)
        broadcastButtonBroadcast = findViewById(R.id.broadcast_button_broadcast)
        broadcastingIndicator = findViewById(R.id.broadcasting_indicator)
    }

    fun setClickListener(clickListener: () -> Unit) {
        broadcastButtonBroadcast.setOnClickListener {
            clickListener()
        }
    }

    fun setState(state: BroadcastButtonViewState) {
        when (state) {
            BroadcastButtonViewState.NotSelected -> {
                broadcastButtonBroadcast.visibility = View.VISIBLE
                broadcastButtonBroadcast.isEnabled = true
                broadcastButtonBroadcast.setText(R.string.broadcast)
                broadcastingIndicator.visibility = View.GONE
            }
            BroadcastButtonViewState.Broadcasting -> {
                broadcastButtonBroadcast.visibility = View.GONE
                broadcastingIndicator.visibility = View.VISIBLE
            }
            BroadcastButtonViewState.Switching -> {
                // Optimistic feedback shown the instant the button is tapped, until
                // the broadcast selection actually changes.
                broadcastButtonBroadcast.visibility = View.VISIBLE
                broadcastButtonBroadcast.isEnabled = false
                broadcastButtonBroadcast.setText(R.string.switching)
                broadcastingIndicator.visibility = View.GONE
            }
        }
    }
}