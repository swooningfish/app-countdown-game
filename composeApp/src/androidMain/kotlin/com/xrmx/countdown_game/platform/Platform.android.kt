package com.xrmx.countdown_game.platform

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Set from MainActivity before the UI starts. */
object AndroidPlatform {
    @SuppressLint("StaticFieldLeak") // application context only
    lateinit var context: Context
        private set

    fun init(context: Context) {
        this.context = context.applicationContext
    }
}

actual fun platformDescription(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

actual fun platformAppId(): String = AndroidPlatform.context.packageName

private fun storedFile(name: String) = File(AndroidPlatform.context.filesDir, name)

actual fun readStoredText(name: String): String? = storedFile(name).takeIf { it.exists() }?.readText()

actual fun writeStoredText(name: String, text: String) = writeFileAtomically(storedFile(name), text)

actual fun deleteStoredText(name: String) {
    storedFile(name).delete()
}

actual suspend fun downloadText(url: String): String = withContext(Dispatchers.IO) { httpGetText(url) }

@Composable
actual fun rememberTextFilePicker(onPicked: (Result<String>?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            latest(null)
        } else {
            latest(
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        ?: error("Couldn't open that file")
                },
            )
        }
    }
    return { launcher.launch("text/*") }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/** Created on first use; plays on the media volume so it follows the phone's volume buttons. */
private val beeper: android.media.ToneGenerator? by lazy {
    runCatching { android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 80) }.getOrNull()
}

actual fun playBeep(final: Boolean) {
    runCatching { beeper?.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, if (final) 600 else 120) }
}
