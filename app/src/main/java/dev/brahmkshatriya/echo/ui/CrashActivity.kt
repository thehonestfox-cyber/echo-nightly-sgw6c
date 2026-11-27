package dev.brahmkshatriya.echo.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.brahmkshatriya.echo.R

/**
 * Shows a crash report. Deliberately written with plain views (no Compose, no Koin, no
 * dependency injection) and declared in a separate process so that it cannot be taken down by
 * whatever crashed the app.
 */
class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = intent?.getStringExtra(EXTRA_TEXT).orEmpty()
        val padding = (resources.displayMetrics.density * 28).toInt()

        val title = TextView(this).apply {
            text = getString(R.string.crash_title)
            setTextColor(Color.parseColor("#FF6E6E"))
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
        }
        val hint = TextView(this).apply {
            text = getString(R.string.crash_hint, packageName)
            setTextColor(Color.LTGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER
            setPadding(0, padding / 4, 0, padding / 2)
        }
        val body = TextView(this).apply {
            text = report.ifBlank { getString(R.string.crash_empty) }
            setTextColor(Color.WHITE)
            setTypeface(Typeface.MONOSPACE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTextIsSelectable(true)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding / 2, padding, padding / 2, padding)
            addView(title)
            addView(hint)
            addView(body)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isVerticalScrollBarEnabled = true
            addView(column)
        }
        setContentView(scroll)
        scroll.requestFocus()
    }

    companion object {
        const val EXTRA_TEXT = "crash_report"

        fun intent(context: Context, report: String) =
            Intent(context, CrashActivity::class.java).apply {
                putExtra(EXTRA_TEXT, report.take(60_000))
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
    }
}
