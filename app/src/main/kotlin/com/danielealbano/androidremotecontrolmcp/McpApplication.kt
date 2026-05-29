package com.danielealbano.androidremotecontrolmcp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.danielealbano.androidremotecontrolmcp.services.apps.AppIconCache
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject

@HiltAndroidApp
class McpApplication : Application() {
    @Inject
    lateinit var appIconCache: AppIconCache

    override fun onCreate() {
        installCrashRecorder()
        super.onCreate()
        runCatching { createNotificationChannels() }
            .onFailure { Log.e(TAG, "Failed to create notification channels", it) }
        runCatching { configureOsmdroid() }
            .onFailure { Log.e(TAG, "Failed to configure osmdroid", it) }
        runCatching { appIconCache.preload() }
            .onFailure { Log.e(TAG, "Failed to start app icon preload", it) }
        Log.i(TAG, "Application initialized")
    }

    private fun installCrashRecorder() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { saveCrash(thread, throwable) }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrash(
        thread: Thread,
        throwable: Throwable,
    ) {
        val stackTrace =
            StringWriter().also { writer ->
                PrintWriter(writer).use { printWriter ->
                    printWriter.println("Thread: ${thread.name}")
                    throwable.printStackTrace(printWriter)
                }
            }.toString()

        getSharedPreferences(CRASH_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(CRASH_PREF_LAST_CRASH, stackTrace)
            .apply()
    }

    private fun configureOsmdroid() {
        val osmConfig = Configuration.getInstance()
        osmConfig.userAgentValue = packageName
        osmConfig.osmdroidBasePath = filesDir
        osmConfig.osmdroidTileCache = cacheDir.resolve("osmdroid")
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val mcpServerChannel =
            NotificationChannel(
                MCP_SERVER_CHANNEL_ID,
                getString(R.string.notification_channel_mcp_server_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Notification for the running MCP server"
            }

        notificationManager.createNotificationChannel(mcpServerChannel)
    }

    companion object {
        private const val TAG = "MCP:Application"
        const val MCP_SERVER_CHANNEL_ID = "mcp_server_channel"
        const val CRASH_PREFS_NAME = "mcp_crash_diagnostics"
        const val CRASH_PREF_LAST_CRASH = "last_crash"
    }
}
