package com.xrmx.countdown_game.platform

import androidx.compose.runtime.Composable

/** The desktop build is for development only and has no Bluetooth games. */
actual fun bluetoothGames(): BluetoothGames? = null

@Composable
actual fun rememberBluetoothPermission(onResult: (Boolean) -> Unit): () -> Unit = { onResult(true) }
