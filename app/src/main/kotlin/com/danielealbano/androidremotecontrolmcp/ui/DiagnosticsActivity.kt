package com.danielealbano.androidremotecontrolmcp.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.danielealbano.androidremotecontrolmcp.McpApplication

/**
 * A minimal, non-Compose, non-Hilt launcher screen.
 *
 * This lets users open the APK and inspect the last crash even if the main
 * Compose/Hilt UI fails on a specific physical device.
 */
class DiagnosticsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val crashText = readLastCrash()
        val bodyText =
            buildString {
                appendLine("Android MCP Test Launcher")
                appendLine()
                appendLine("This screen is a safe diagnostics launcher.")
                appendLine("Use Open Main UI to test the full app screen.")
                appendLine()
                if (crashText.isBlank()) {
                    appendLine("Last crash: none recorded")
                } else {
                    appendLine("Last crash:")
                    appendLine(crashText)
                }
            }

        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }

        val textView =
            TextView(this).apply {
                text = bodyText
                textSize = 14f
                setTextIsSelectable(true)
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f,
                    )
            }

        val openMainButton =
            Button(this).apply {
                text = "Open Main UI"
                setOnClickListener {
                    startActivity(Intent(this@DiagnosticsActivity, MainActivity::class.java))
                }
            }

        val copyButton =
            Button(this).apply {
                text = "Copy Last Crash"
                isEnabled = crashText.isNotBlank()
                setOnClickListener { copyToClipboard(crashText) }
            }

        val clearButton =
            Button(this).apply {
                text = "Clear Last Crash"
                setOnClickListener {
                    getSharedPreferences(McpApplication.CRASH_PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .clear()
                        .apply()
                    render()
                }
            }

        val buttons =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(openMainButton)
                addView(copyButton)
                addView(clearButton)
            }

        root.addView(textView)
        root.addView(buttons)

        setContentView(
            ScrollView(this).apply {
                addView(root)
            },
        )
    }

    private fun readLastCrash(): String =
        getSharedPreferences(McpApplication.CRASH_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(McpApplication.CRASH_PREF_LAST_CRASH, "")
            .orEmpty()

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Last crash", text))
    }
}
