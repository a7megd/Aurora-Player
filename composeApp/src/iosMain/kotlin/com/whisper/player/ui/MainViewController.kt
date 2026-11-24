package com.whisper.player.ui

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Entry point consumed by the small SwiftUI iOS host application. */
fun MainViewController(): UIViewController = ComposeUIViewController {
    WhisperPlayerApp()
}
