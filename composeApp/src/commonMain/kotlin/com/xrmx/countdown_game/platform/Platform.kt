package com.xrmx.countdown_game.platform

import androidx.compose.runtime.Composable

/** e.g. "Android 15 (API 35)" or "iOS 18.2". */
expect fun platformDescription(): String

/** The installed app's identifier (Android application ID or iOS bundle ID). */
expect fun platformAppId(): String

/** Small text files kept in the app's private storage. */
expect fun readStoredText(name: String): String?

expect fun writeStoredText(name: String, text: String)

expect fun deleteStoredText(name: String)

/** Downloads a text file over HTTPS. Throws on failure. */
expect suspend fun downloadText(url: String): String

/**
 * Returns a function that opens the system file picker for a text file. [onPicked] receives the
 * file's contents, a failure if it couldn't be read, or null if the user cancelled.
 */
@Composable
expect fun rememberTextFilePicker(onPicked: (Result<String>?) -> Unit): () -> Unit

/** Handles the system back gesture where the platform has one. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** Stops the screen dimming and locking while [enabled], e.g. during a two-player game. */
@Composable
expect fun KeepScreenOn(enabled: Boolean)

/** A short beep for the clock's final seconds; a longer one when [final] (time is up). */
expect fun playBeep(final: Boolean)

/** A short buzz for the clock's final seconds; a longer one when [final] (time is up). */
expect fun vibrate(final: Boolean)
