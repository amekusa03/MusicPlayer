package com.kusa.musicplayer.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver

/**
 * Simple Car session.  Android Auto's media template handles playback UI
 * automatically via the MediaSession.  This session provides a minimal
 * "Now Playing" info pane as a supplementary screen.
 */
class MusicCarSession : Session() {

    override fun onCreateScreen(intent: android.content.Intent): Screen =
        NowPlayingScreen(carContext)
}

class NowPlayingScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val row = Row.Builder()
            .setTitle("Music Player")
            .addText("音楽を再生中 — Androidオートに対応しています")
            .build()

        val pane = Pane.Builder()
            .addRow(row)
            .build()

        return PaneTemplate.Builder(pane)
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
