@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.xrmx.countdown_game.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.xrmx.countdown_game.AppInfo
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

actual fun platformDescription(): String =
    "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}"

actual fun platformAppId(): String = NSBundle.mainBundle.bundleIdentifier ?: AppInfo.IOS_BUNDLE_ID

private fun storedPath(name: String): String {
    val dir = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
    NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    return "$dir/$name"
}

actual fun readStoredText(name: String): String? =
    NSString.create(contentsOfFile = storedPath(name), encoding = NSUTF8StringEncoding, error = null)?.toString()

actual fun writeStoredText(name: String, text: String) {
    @Suppress("CAST_NEVER_SUCCEEDS")
    val saved = (text as NSString).writeToFile(storedPath(name), atomically = true, encoding = NSUTF8StringEncoding, error = null)
    if (!saved) throw IllegalStateException("Couldn't save $name")
}

actual fun deleteStoredText(name: String) {
    NSFileManager.defaultManager.removeItemAtPath(storedPath(name), error = null)
}

actual suspend fun downloadText(url: String): String = withContext(Dispatchers.Default) {
    require(url.startsWith("https://", ignoreCase = true)) { "Only https:// links are supported" }
    val nsUrl = NSURL.URLWithString(url) ?: throw IllegalArgumentException("That isn't a valid link")
    NSString.create(contentsOfURL = nsUrl, encoding = NSUTF8StringEncoding, error = null)?.toString()
        ?: throw IllegalStateException("Download failed. Check the link and your connection.")
}

private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) controller = controller.presentedViewController
    return controller
}

@Composable
actual fun rememberTextFilePicker(onPicked: (Result<String>?) -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onPicked)
    // UIKit holds the delegate weakly, so keep it alive for as long as the composable is.
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                if (url == null) {
                    latest(null)
                    return
                }
                val scoped = url.startAccessingSecurityScopedResource()
                val text = NSString.create(contentsOfURL = url, encoding = NSUTF8StringEncoding, error = null)?.toString()
                if (scoped) url.stopAccessingSecurityScopedResource()
                latest(
                    if (text != null) Result.success(text) else Result.failure(IllegalStateException("Couldn't read that file as UTF-8 text")),
                )
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                latest(null)
            }
        }
    }
    return {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOfNotNull(UTTypePlainText), asCopy = true)
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    DisposableEffect(enabled) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}

/** iOS system sounds: a short tick each second and a longer tone when time is up. Silent mode mutes them. */
actual fun playBeep(final: Boolean) {
    platform.AudioToolbox.AudioServicesPlaySystemSound(if (final) 1005u else 1103u)
}

/** A firm tap each second and the full vibration when time is up. */
actual fun vibrate(final: Boolean) {
    if (final) {
        platform.AudioToolbox.AudioServicesPlaySystemSound(platform.AudioToolbox.kSystemSoundID_Vibrate)
    } else {
        platform.UIKit.UIImpactFeedbackGenerator(platform.UIKit.UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy).impactOccurred()
    }
}
