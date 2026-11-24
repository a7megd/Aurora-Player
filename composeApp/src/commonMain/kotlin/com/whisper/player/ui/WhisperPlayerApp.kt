package com.whisper.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whisper.player.shared.platform.CommunityService

private data class AppDestination(val title: String, val symbol: String)

private val destinations = listOf(
    AppDestination("الصوت", "♫"),
    AppDestination("المكتبة", "▤"),
    AppDestination("قوائم التشغيل", "≡"),
    AppDestination("بحث", "⌕"),
    AppDestination("المجتمع", "♡"),
    AppDestination("الإعدادات", "⚙"),
)

/**
 * Shared Compose Multiplatform entry UI. Platform adapters are injected at the app boundary;
 * this shared shell intentionally contains no Android Context, Activity, MediaStore or AVFoundation.
 *
 * The existing Android screens are not replaced by this initial shell. Feature screens are
 * migrated incrementally after their platform dependencies have been moved behind interfaces.
 */
@Composable
fun WhisperPlayerApp(
    communityService: CommunityService? = null,
    currentUserId: String? = null,
    currentUserName: String = "",
) {
    var selected by remember { mutableIntStateOf(0) }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    destinations.forEachIndexed { index, destination ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = { Text(destination.symbol) },
                            label = { Text(destination.title) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(22.dp))
                Text(
                    text = "WHISPER",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = destinations[selected].title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(18.dp))
                when (selected) {
                    0 -> NowPlayingPlaceholder()
                    1 -> FeatureList("مكتبتك الصوتية", "الأغاني", "الفنانون", "الألبومات", "المجلدات")
                    2 -> FeatureList("قوائم التشغيل", "قائمة التشغيل الحالية", "إنشاء قائمة", "المفضلة")
                    3 -> FeatureList("ابحث في الصوت", "الأغاني", "الفنانون", "الألبومات")
                    4 -> CommunityScreen(communityService, currentUserId, currentUserName)
                    else -> FeatureList("تخصيص Whisper", "المظهر والألوان", "ترتيب الأصوات", "إعدادات التشغيل", "إعدادات الصوت")
                }
            }
        }
    }
}

@Composable
private fun NowPlayingPlaceholder() {
    Surface(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("♫", style = MaterialTheme.typography.displayLarge)
            Text("Whisper Player", style = MaterialTheme.typography.titleLarge)
            Text("مشغّل الصوت الخاص بك", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FeatureList(title: String, vararg rows: String) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        items(rows.size) { index ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(rows[index], style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
