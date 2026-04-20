package com.mahmutalperenunal.adaptivehz.core

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mahmutalperenunal.adaptivehz.R

/**
 * Quick Settings Tile service for toggling Adaptive Hz.
 *
 * Keeps the tile UI (icon, label, subtitle, state) in sync with the app state
 * and delegates the toggle action to [AdaptiveHzActionHandler].
 */
class AdaptiveHzTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        try {
            AdaptiveHzActionHandler.toggle(this)
        } catch (_: Throwable) {
        }

        updateTile()
    }

    // Syncs the tile UI with current app state (enabled/disabled)
    private fun updateTile() {
        val tile = qsTile ?: return
        val enabled = AdaptiveHzActionHandler.isAppEnabled(this)

        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_tile_adaptive_hz)
        tile.label = getString(R.string.app_name)
        tile.subtitle = if (enabled) {
            getString(R.string.label_on)
        } else {
            getString(R.string.label_off)
        }
        tile.state = if (enabled) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }

        tile.updateTile()
    }
}