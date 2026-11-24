from pathlib import Path
root = Path("AuroraPlayer/app/src/main/java/com/aurora/player")

def replace_once(path, old, new):
    file = root / path
    source = file.read_text()
    if old not in source:
        raise SystemExit("Patch anchor missing in " + path + ": " + old[:80])
    file.write_text(source.replace(old, new, 1))

replace_once("ui/navigation/Routes.kt",
    '    const val CHAT = "chat"',
    '    const val CHAT = "chat"\n    const val FILE_MANAGER = "file_manager"\n    const val HIDDEN_RECORDINGS = "hidden_recordings"\n    const val DOWNLOADS = "downloads"')

replace_once("ui/navigation/AppRoot.kt",
    '    Routes.WRITE_ARTICLE to "كتابة مقال", Routes.ARTICLES to "المقالات", Routes.FRIEND_REQUESTS to "طلبات الصداقة", Routes.POSTS to "المنشورات", Routes.CHAT to "المحادثات",',
    '    Routes.WRITE_ARTICLE to "كتابة مقال", Routes.ARTICLES to "المقالات", Routes.FRIEND_REQUESTS to "طلبات الصداقة", Routes.POSTS to "المنشورات", Routes.CHAT to "المحادثات",\n    Routes.FILE_MANAGER to "إدارة الملفات", Routes.HIDDEN_RECORDINGS to "المجلد المخفي", Routes.DOWNLOADS to "التنزيلات",')

replace_once("ui/navigation/AppRoot.kt",
    "    val songs by repository.songs.collectAsState()",
    "    val songs by repository.songs.collectAsState()\n    val recordings by repository.recordings.collectAsState()")

replace_once("ui/navigation/AppRoot.kt",
    "                        r == Routes.SETTINGS -> WhisperSettingsScreen(app.container.settingsRepository, controller, onWallpaperChanged = { wallpaperUri = it })",
    '                        r == Routes.SETTINGS -> WhisperSettingsScreen(app.container.settingsRepository, controller, recordingsCount = recordings.size, onWallpaperChanged = { wallpaperUri = it }, onNavigate = { target -> nav.navigate(target) { launchSingleTop = true } })\n                        r == Routes.FILE_MANAGER -> WhisperFileManagerScreen(songs + recordings, onRefresh = { repository.refresh() })\n                        r == Routes.HIDDEN_RECORDINGS -> WhisperFileManagerScreen(recordings, recordingsOnly = true, onRefresh = { repository.refresh() })\n                        r == Routes.DOWNLOADS -> WhisperDownloadsScreen(onOpenConverter = { nav.navigate(Routes.MEDIA_CONVERTER) { launchSingleTop = true } })\n                        r == Routes.MEDIA_CONVERTER -> WhisperMediaConverterScreen(incomingShareUri, incomingShareText)')

replace_once("ui/navigation/WhisperScreens.kt",
    "fun WhisperSettingsScreen(repository: SettingsRepository, controller: MediaController?, onWallpaperChanged: (String?) -> Unit = {}) {",
    "fun WhisperSettingsScreen(repository: SettingsRepository, controller: MediaController?, recordingsCount: Int = 0, onWallpaperChanged: (String?) -> Unit = {}, onNavigate: (String) -> Unit = {}) {")

replace_once("ui/navigation/WhisperScreens.kt",
    '    val wallpaperPrefs = remember { context.getSharedPreferences("whisper_preferences", Context.MODE_PRIVATE) }',
    '    val wallpaperPrefs = remember { context.getSharedPreferences("whisper_preferences", Context.MODE_PRIVATE) }\n    val recordingPrefs = remember { context.getSharedPreferences("whisper_recordings", Context.MODE_PRIVATE) }\n    var showHiddenRecordings by remember { mutableStateOf(recordingPrefs.getBoolean("show_hidden_folder", false)) }')

tools = '''        Card(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tune, null)
                    Spacer(Modifier.width(8.dp))
                    Text("أدوات التشغيل", style = MaterialTheme.typography.titleMedium)
                }
                Text("يمكنك ضبط مؤقت النوم من شاشة تشغيل الأغنية.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
'''
cards = tools + '''        Card(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("المجلد المخفي", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = showHiddenRecordings, onCheckedChange = {
                        showHiddenRecordings = it
                        recordingPrefs.edit().putBoolean("show_hidden_folder", it).apply()
                    })
                }
                Text("التسجيلات الصوتية المكتشفة تُفصل عن مكتبة الأغاني ولا تظهر بين باقي الأصوات. يمكنك إظهار مجلدها هنا عند الحاجة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$recordingsCount تسجيلًا صوتيًا", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                if (showHiddenRecordings) OutlinedButton(onClick = { onNavigate(Routes.HIDDEN_RECORDINGS) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("فتح المجلد المخفي")
                }
            }
        }
        Card(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("إدارة الصوت والتنزيلات", style = MaterialTheme.typography.titleMedium)
                Text("تحكم في الملفات الموجودة على الهاتف، أو أضف ملفات صوتية من روابط تنزيل مباشرة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { onNavigate(Routes.FILE_MANAGER) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("إدارة الملفات الصوتية")
                }
                Button(onClick = { onNavigate(Routes.DOWNLOADS) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(8.dp)); Text("صفحة التنزيلات")
                }
            }
        }
'''
replace_once("ui/navigation/WhisperScreens.kt", tools, cards)

replace_once("ui/navigation/WhisperScreens.kt",
    '                IconButton(onClick = { onNavigate(Routes.SETTINGS) }) { Icon(Icons.Rounded.Settings, contentDescription = "الإعدادات") }',
    '                IconButton(onClick = { onNavigate(Routes.DOWNLOADS) }) { Icon(Icons.Rounded.Download, contentDescription = "التنزيلات") }\n                IconButton(onClick = { onNavigate(Routes.SETTINGS) }) { Icon(Icons.Rounded.Settings, contentDescription = "الإعدادات") }')
