package com.mahmutalperenunal.adaptivehz.core

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mahmutalperenunal.adaptivehz.R

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