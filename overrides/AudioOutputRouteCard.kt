package com.aurora.player.ui.navigation

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private fun activeOutputLabel(context: Context): String {
    val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    return when {
        outputs.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || (Build.VERSION.SDK_INT >= 31 && (it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)) } -> "سماعة بلوتوث متصلة"
        outputs.any { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET } -> "سماعة سلكية متصلة"
        outputs.any { it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE } -> "مخرج صوت USB"
        else -> "مكبر صوت الهاتف"
    }
}

@Composable
fun AudioOutputRouteCard() {
    val context = LocalContext.current
    var outputLabel by remember(context) { mutableStateOf(activeOutputLabel(context)) }

    DisposableEffect(context) {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                outputLabel = activeOutputLabel(context)
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                outputLabel = activeOutputLabel(context)
            }
        }
        manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { manager.unregisterAudioDeviceCallback(callback) }
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable {
            val intent = if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_BLUETOOTH) else Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f))
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
            Spacer(Modifier.width(14.dp))
            Icon(
                imageVector = when {
                    outputLabel.contains("بلوتوث") -> Icons.Rounded.Bluetooth
                    outputLabel.contains("سلكية") -> Icons.Rounded.Headphones
                    else -> Icons.Rounded.Speaker
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("مخرج الصوت", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(outputLabel, style = MaterialTheme.typography.titleSmall)
                Text("اضغط لإدارة اتصال البلوتوث", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
        }
    }
}
