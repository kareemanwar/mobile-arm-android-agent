package com.danielealbano.androidremotecontrolmcp.services.channel.listeners

import com.danielealbano.androidremotecontrolmcp.data.model.ChannelEventFactory
import com.danielealbano.androidremotecontrolmcp.data.model.GeofenceChannelConfig
import com.danielealbano.androidremotecontrolmcp.services.channel.EventDispatcher
import com.danielealbano.androidremotecontrolmcp.services.channel.geofence.GeofenceManager
import com.danielealbano.androidremotecontrolmcp.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class GeofenceEventListener(
    private val eventDispatcher: EventDispatcher,
    private val geofenceManager: GeofenceManager,
    private val scope: CoroutineScope,
) {
    @Volatile
    private var currentConfig: GeofenceChannelConfig = GeofenceChannelConfig()

    fun start(config: GeofenceChannelConfig) {
        currentConfig = config
        scope.launch {
            geofenceManager.syncGeofences(config.zones)
        }
    }

    fun stop() {
        scope.launch {
            geofenceManager.removeAllGeofences()
        }
    }

    fun updateConfig(config: GeofenceChannelConfig) {
        currentConfig = config
        scope.launch {
            geofenceManager.syncGeofences(config.zones)
        }
    }

    suspend fun handleTransition(
        zoneId: String,
        transition: String,
    ) {
        val zone =
            currentConfig.zones.find { it.id == zoneId } ?: run {
                Logger.w(TAG, "Geofence zone not found: $zoneId")
                return
            }
        val event = ChannelEventFactory.geofence(zone, transition)
        eventDispatcher.dispatch(event)
    }

    companion object {
        private const val TAG = "MCP:GeofenceListener"
    }
}
