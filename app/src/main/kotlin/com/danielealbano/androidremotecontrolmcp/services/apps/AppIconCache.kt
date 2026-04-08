package com.danielealbano.androidremotecontrolmcp.services.apps

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import com.danielealbano.androidremotecontrolmcp.utils.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-scoped cache for app icons.
 *
 * Preloads the first [PRELOAD_COUNT] launchable app icons at app startup
 * so they're ready before the user reaches the notification filter screen.
 * Remaining icons are loaded on demand and cached for the app's lifetime.
 */
@Singleton
class AppIconCache
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val cache = ConcurrentHashMap<String, Bitmap>()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Preloads icons for the first [count] launchable apps (alphabetically).
         * Called from [com.danielealbano.androidremotecontrolmcp.McpApplication.onCreate].
         */
        fun preload(count: Int = PRELOAD_COUNT) {
            scope.launch {
                val pm = context.packageManager
                val apps = getLaunchableAppsSorted(pm)
                for (app in apps.take(count)) {
                    loadAndCache(pm, app.first)
                }
                Logger.d(TAG, "Preloaded ${cache.size} app icons")
            }
        }

        /**
         * Returns the cached icon for [packageId], or null if not yet loaded.
         */
        operator fun get(packageId: String): Bitmap? = cache[packageId]

        /**
         * Returns the full icon cache as an immutable map.
         */
        fun getAll(): Map<String, Bitmap> = cache.toMap()

        /**
         * Loads icons for all [packageIds] not already in the cache, in the background.
         * Calls [onBatchLoaded] after each chunk completes so the UI can recompose.
         */
        fun loadMissing(
            packageIds: List<String>,
            onBatchLoaded: () -> Unit = {},
        ) {
            val uncached = packageIds.filter { !cache.containsKey(it) }
            if (uncached.isEmpty()) return

            scope.launch {
                val pm = context.packageManager
                for (chunk in uncached.chunked(LOAD_CHUNK_SIZE)) {
                    for (packageId in chunk) {
                        loadAndCache(pm, packageId)
                    }
                    onBatchLoaded()
                }
            }
        }

        private fun loadAndCache(
            pm: PackageManager,
            packageId: String,
        ) {
            if (cache.containsKey(packageId)) return
            try {
                val bitmap = pm.getApplicationIcon(packageId).toBitmap(ICON_SIZE_PX, ICON_SIZE_PX)
                cache[packageId] = bitmap
            } catch (_: Exception) {
                // App may have been uninstalled or icon unavailable
            }
        }

        /**
         * Returns launchable apps sorted alphabetically by name.
         * Each pair is (packageId, appName).
         */
        private fun getLaunchableAppsSorted(pm: PackageManager): List<Pair<String, String>> {
            val launchIntent =
                android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                    addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                }
            return pm
                .queryIntentActivities(launchIntent, PackageManager.ResolveInfoFlags.of(0))
                .mapNotNull { resolveInfo ->
                    val pkgName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                    val label = resolveInfo.loadLabel(pm)?.toString() ?: return@mapNotNull null
                    if (label.isBlank() || label == pkgName) return@mapNotNull null
                    pkgName to label
                }.distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
        }

        companion object {
            private const val TAG = "MCP:AppIconCache"
            private const val PRELOAD_COUNT = 10
            private const val LOAD_CHUNK_SIZE = 10
            private const val ICON_SIZE_PX = 96
        }
    }
