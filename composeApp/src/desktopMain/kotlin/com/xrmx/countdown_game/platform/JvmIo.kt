package com.xrmx.countdown_game.platform

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

private const val MAX_DOWNLOAD_BYTES = 20 * 1024 * 1024

internal fun httpGetText(url: String): String {
    require(url.startsWith("https://", ignoreCase = true)) { "Only https:// links are supported" }
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        val code = connection.responseCode
        if (code !in 200..299) throw IOException("The server replied with HTTP $code")
        connection.inputStream.use { input ->
            val bytes = input.readNBytesCompat(MAX_DOWNLOAD_BYTES + 1)
            if (bytes.size > MAX_DOWNLOAD_BYTES) throw IOException("That file is larger than 20 MB")
            return bytes.decodeToString()
        }
    } finally {
        connection.disconnect()
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (out.size() < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

internal fun writeFileAtomically(file: File, text: String) {
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, file.name + ".tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(file)) {
        file.delete()
        if (!tmp.renameTo(file)) throw IOException("Couldn't save ${file.name}")
    }
}
