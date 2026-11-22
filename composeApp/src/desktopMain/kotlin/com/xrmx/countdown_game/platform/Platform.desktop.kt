package com.xrmx.countdown_game.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.xrmx.countdown_game.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual fun platformDescription(): String =
    "Desktop (${System.getProperty("os.name")} ${System.getProperty("os.version")})"

actual fun platformAppId(): String = AppInfo.ANDROID_ID

private val storageDir = File(System.getProperty("user.home"), ".countdown-game")

private fun storedFile(name: String) = File(storageDir, name)

actual fun readStoredText(name: String): String? = storedFile(name).takeIf { it.exists() }?.readText()

actual fun writeStoredText(name: String, text: String) = writeFileAtomically(storedFile(name), text)

actual fun deleteStoredText(name: String) {
    storedFile(name).delete()
}

actual suspend fun downloadText(url: String): String = withContext(Dispatchers.IO) { httpGetText(url) }

@Composable
actual fun rememberTextFilePicker(onPicked: (Result<String>?) -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onPicked)
    return {
        val dialog = FileDialog(null as Frame?, "Choose a word list", FileDialog.LOAD)
        dialog.isVisible = true
        val name = dialog.file
        if (name == null) latest(null) else latest(runCatching { File(dialog.directory, name).readText() })
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
