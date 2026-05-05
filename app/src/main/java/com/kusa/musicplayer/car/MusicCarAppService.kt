package com.kusa.musicplayer.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Entry-point for Android Auto / Android Automotive OS.
 * The MediaSession published by MusicPlaybackService (Media3) is automatically
 * picked up by Android Auto's built-in media template, so this service just
 * needs to exist and validate the host.
 */
class MusicCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR   // Relax for debug; tighten for release

    override fun onCreateSession(): Session = MusicCarSession()
}
