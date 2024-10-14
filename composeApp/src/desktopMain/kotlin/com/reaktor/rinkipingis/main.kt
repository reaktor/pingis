package com.reaktor.rinkipingis

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = WindowState(width = 500.dp, height = 500.dp),
        title = "Rinkipingis",
    ) {
        Box(modifier = Modifier.padding(24.dp)) {
            App()
        }
    }
}