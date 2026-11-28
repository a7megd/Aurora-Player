package com.aurora.player.ui.navigation

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aurora.player.study.StudyLockController
import com.aurora.player.media.CloudinaryMediaUploader
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class LocalStudyTask(val id: Long, val title: String, val proof: Uri? = null)

private fun loadStudyTasks(prefs: android.content.SharedPreferences): List<LocalStudyTask> {
    val array = runCatching { JSONArray(prefs.getString("tasks", "[]")) }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val proof = item.optString("proof").takeIf { it.isNotBlank() }?.let(Uri::parse)
            add(LocalStudyTask(item.optLong("id"), item.optString("title"), proof))
        }
    }
}

@Composable
fun StudyHubScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember { context.getSharedPreferences("whisper_study_local", 0) }
    val user = remember { FirebaseAuth.getInstance().currentUser }
    val uid = user?.uid.orEmpty()
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()
    var taskSyncMessage by remember { mutableStateOf<String?>(null) }
    var migratingLegacyTasks by remember { mutableStateOf(false) }
    var completedFocusMinutes by remember { mutableIntStateOf(prefs.getInt("completed_focus_minutes", 0)) }
    var completedSessions by remember { mutableIntStateOf(prefs.getInt("completed_sessions", 0)) }
    var streakDays by remember { mutableIntStateOf(prefs.getInt("streak_days", 0)) }
    var achievementCount by remember { mutableIntStateOf(prefs.getInt("achievement_count", 0)) }
    var lastAchievementAt by remember { mutableLongStateOf(prefs.getLong("last_achievement_at", 0L)) }
    var badgeClock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var avatarIndex by remember { mutableIntStateOf(prefs.getInt("avatar_index", 0).coerceIn(0, 5)) }
    var avatarMenu by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) { delay(60_000); badgeClock = System.currentTimeMillis() }
    }
    val badgeActive = lastAchievementAt > 0L && badgeClock - lastAchievementAt < 48L * 60L * 60L * 1000L
    val achievementThresholds = listOf(15, 30, 45, 60, 90, 120, 180, 240, 300, 360, 420, 480, 540, 600, 720)
    val avatars = listOf("🐣", "🦊", "🐼", "🦉", "🦅", "🐉")
    val unlockedAvatarLevel = (completedSessions / 3).coerceIn(0, avatars.lastIndex)
    val achievementTitle = when {
        achievementCount >= 15 && badgeActive -> "الوسام الذهبي — إنجاز محلي"
        streakDays >= 3 && badgeActive -> "الوسام الفضي — سلسلة 3 أيام"
        achievementCount >= 8 -> "المجتهد"
        achievementCount >= 3 -> "المثابر"
        achievementCount >= 1 -> "بداية قوية"
        else -> "طالب جديد"
    }
    var focusMinutes by remember { mutableIntStateOf(prefs.getInt("focus_minutes", 25)) }
    var breakMinutes by remember { mutableIntStateOf(prefs.getInt("break_minutes", 5)) }
    var secondsLeft by remember { mutableIntStateOf(focusMinutes * 60) }
    var running by remember { mutableStateOf(false) }
    var onBreak by remember { mutableStateOf(false) }
    var lockEnabled by remember { mutableStateOf(prefs.getBoolean("lock_enabled", false)) }
    var lockActive by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("جاهز لبدء جلسة تركيز جديدة") }
    var taskDraft by remember { mutableStateOf("") }
    var tasks by remember { mutableStateOf(loadStudyTasks(prefs)) }
    var proofTarget by remember { mutableStateOf<Long?>(null) }
    var proofUploading by remember { mutableStateOf(false) }
    val proofPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = proofTarget
        if (uri != null && id != null) {
            scope.launch {
                proofUploading = true
                try {
                    require(uid.isNotBlank()) { "سجّل الدخول أولًا لحفظ إثبات المهمة على حسابك." }
                    val uploaded = CloudinaryMediaUploader.upload(context, uri, "Whisper-Player/StudyProof")
                    val task = tasks.firstOrNull { it.id == id } ?: error("المهمة غير موجودة.")
                    db.collection("studyTasks").document(uid).collection("tasks").document(id.toString())
                        .update(mapOf("proofUrl" to uploaded.secureUrl, "proofAssetId" to uploaded.assetId,
                            "updatedAt" to System.currentTimeMillis())).await()
                    tasks = tasks.map { if (it.id == id) it.copy(proof = Uri.parse(uploaded.secureUrl)) else it }
                    status = "تم رفع صورة الإثبات وحفظها على حسابك."
                    taskSyncMessage = "تم حفظ المهمة وصورة الإثبات سحابيًا."
                } catch (e: Exception) { status = e.localizedMessage ?: "تعذر رفع صورة الإثبات." }
                proofUploading = false
            }
        }
        proofTarget = null
    }
    DisposableEffect(uid) {
        if (uid.isBlank()) return@DisposableEffect onDispose { }
        val registration = db.collection("studyTasks").document(uid).collection("tasks")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    taskSyncMessage = "تعذرت مزامنة المهام: " + (error.localizedMessage ?: "خطأ غير معروف")
                } else if (snapshot != null) {
                    if (snapshot.isEmpty && tasks.isNotEmpty() && !migratingLegacyTasks) {
                        migratingLegacyTasks = true
                        val legacyTasks = tasks.toList()
                        scope.launch {
                            try {
                                val batch = db.batch()
                                legacyTasks.forEach { task ->
                                    var proofUrl = task.proof?.toString()?.takeIf { it.startsWith("https://") }.orEmpty()
                                    if (proofUrl.isBlank() && task.proof != null) {
                                        runCatching { CloudinaryMediaUploader.upload(context, task.proof, "Whisper-Player/StudyProof").secureUrl }
                                            .onSuccess { proofUrl = it }
                                    }
                                    val ref = db.collection("studyTasks").document(uid).collection("tasks").document(task.id.toString())
                                    batch.set(ref, mapOf("id" to task.id, "uid" to uid, "title" to task.title,
                                        "proofUrl" to proofUrl, "createdAt" to System.currentTimeMillis(),
                                        "updatedAt" to System.currentTimeMillis()))
                                }
                                batch.commit().await()
                                taskSyncMessage = "تم نقل مهامك القديمة إلى حسابك ومزامنتها."
                            } catch (e: Exception) {
                                taskSyncMessage = e.localizedMessage ?: "تعذر نقل المهام القديمة إلى السحابة."
                                migratingLegacyTasks = false
                            }
                        }
                    } else {
                        tasks = snapshot.documents.mapNotNull { doc ->
                            val title = doc.getString("title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            val proofUrl = doc.getString("proofUrl")?.takeIf { it.startsWith("https://") }
                            LocalStudyTask(doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L, title, proofUrl?.let(Uri::parse))
                        }
                        taskSyncMessage = "تمت مزامنة مهامك مع حسابك."
                    }
                }
            }
        onDispose { registration.remove() }
    }
    LaunchedEffect(tasks) {
        val array = JSONArray()
        tasks.forEach { task ->
            array.put(JSONObject().put("id", task.id).put("title", task.title).put("proof", task.proof?.toString().orEmpty()))
        }
        prefs.edit().putString("tasks", array.toString()).apply()
    }
    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            if (secondsLeft > 0) secondsLeft--
            if (secondsLeft == 0) {
                running = false
                if (!onBreak) {
                    val now = System.currentTimeMillis()
                    val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now))
                    val calendar = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -1) }
                    val yesterday = SimpleDateFormat("yyyyMMdd", Locale.US).format(calendar.time)
                    val lastDay = prefs.getString("last_study_day", "").orEmpty()
                    streakDays = when (lastDay) {
                        today -> streakDays
                        yesterday -> streakDays + 1
                        else -> 1
                    }
                    if (streakDays >= 3 && lastDay == yesterday) {
                        lastAchievementAt = now
                        prefs.edit().putLong("last_achievement_at", now).apply()
                    }
                    completedSessions += 1
                    completedFocusMinutes += focusMinutes
                    val previousStart = prefs.getLong("achievement_window_start", 0L)
                    val windowStart = if (previousStart == 0L || now - previousStart > 48L * 60L * 60L * 1000L) now else previousStart
                    val windowMinutes = if (windowStart == now) focusMinutes else prefs.getInt("achievement_window_minutes", 0) + focusMinutes
                    val newAchievementCount = achievementThresholds.count { it <= windowMinutes }
                    if (windowStart == now) achievementCount = 0
                    val oldAchievementCount = if (windowStart == now) 0 else prefs.getInt("achievement_count", 0)
                    if (newAchievementCount > oldAchievementCount) {
                        achievementCount = newAchievementCount
                        lastAchievementAt = now
                        prefs.edit().putLong("last_achievement_at", now).apply()
                        status = "إنجاز جديد! حصلت على وسامك رقم " + newAchievementCount
                    }
                    prefs.edit()
                        .putInt("completed_focus_minutes", completedFocusMinutes)
                        .putInt("completed_sessions", completedSessions)
                        .putInt("streak_days", streakDays)
                        .putString("last_study_day", today)
                        .putLong("achievement_window_start", windowStart)
                        .putInt("achievement_window_minutes", windowMinutes)
                        .putInt("achievement_count", achievementCount)
                        .apply()
                    onBreak = true
                    secondsLeft = breakMinutes * 60
                    if (newAchievementCount <= oldAchievementCount) status = "انتهت جلسة التركيز. خذ استراحة."
                } else {
                    onBreak = false
                    secondsLeft = focusMinutes * 60
                    status = "انتهت الاستراحة. ابدأ جلسة جديدة."
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { if (lockActive && activity != null) StudyLockController.exit(activity) }
    }
    val total = if (onBreak) breakMinutes * 60 else focusMinutes * 60
    val progress = if (total > 0) 1f - secondsLeft.toFloat() / total else 0f
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("مساحة المذاكرة", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("ذاكر مع أصدقائك وحقق إنجازاتك خطوة بخطوة.")
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("شخصيتك وإنجازاتك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            Text(avatars[avatarIndex], style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(end = 12.dp))
                            DropdownMenu(expanded = avatarMenu, onDismissRequest = { avatarMenu = false }) {
                                avatars.forEachIndexed { index, avatar ->
                                    DropdownMenuItem(
                                        text = { Text(avatar + if (index <= unlockedAvatarLevel) " — متاح" else " — مقفل") },
                                        enabled = index <= unlockedAvatarLevel,
                                        onClick = { avatarIndex = index; prefs.edit().putInt("avatar_index", index).apply(); avatarMenu = false }
                                    )
                                }
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(achievementTitle, fontWeight = FontWeight.SemiBold)
                            Text("المستوى " + (unlockedAvatarLevel + 1) + " • " + achievementCount + " / 15 إنجاز", style = MaterialTheme.typography.bodySmall)
                            Text("جلسات مكتملة: " + completedSessions + " • دقائق تركيز: " + completedFocusMinutes, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { avatarMenu = true }) { Text("الشخصية") }
                    }
                    LinearProgressIndicator(progress = { (completedFocusMinutes.coerceAtMost(720) / 720f) }, modifier = Modifier.fillMaxWidth())
                    Text("سلسلة المذاكرة: " + streakDays + " يوم • الوسام الذهبي المحلي يتطلب 12 ساعة تركيز خلال نافذة 48 ساعة.", style = MaterialTheme.typography.bodySmall)
                    Text(if (badgeActive) "الوسام الحالي ساري لمدة 48 ساعة من آخر إنجاز." else "لا يوجد وسام مؤقت نشط حاليًا.", style = MaterialTheme.typography.bodySmall)
                    Text("الأوسمة محفوظة على هذا الجهاز وليست توثيقًا تنافسيًا موثوقًا بين الحسابات.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (onBreak) "وقت الاستراحة" else "جلسة تركيز", fontWeight = FontWeight.SemiBold)
                    Text("%02d:%02d".format(secondsLeft / 60, secondsLeft % 60), style = MaterialTheme.typography.displayLarge)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(status, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { running = !running; status = if (running) "الجلسة تعمل الآن" else "تم إيقاف المؤقت مؤقتًا" }) { Text(if (running) "إيقاف مؤقت" else "ابدأ") }
                        OutlinedButton(onClick = {
                            running = false; onBreak = false; secondsLeft = focusMinutes * 60
                            if (lockActive && activity != null) StudyLockController.exit(activity)
                            lockActive = false; status = "تم إنهاء الجلسة"
                        }) { Text("إنهاء") }
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("إعدادات التركيز", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("التركيز: " + focusMinutes + " دقيقة", modifier = Modifier.weight(1f))
                        TextButton(onClick = { focusMinutes = (focusMinutes - 5).coerceAtLeast(5); if (!running && !onBreak) secondsLeft = focusMinutes * 60 }) { Text("−") }
                        TextButton(onClick = { focusMinutes = (focusMinutes + 5).coerceAtMost(120); if (!running && !onBreak) secondsLeft = focusMinutes * 60 }) { Text("+") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الاستراحة: " + breakMinutes + " دقيقة", modifier = Modifier.weight(1f))
                        TextButton(onClick = { breakMinutes = (breakMinutes - 1).coerceAtLeast(1) }) { Text("−") }
                        TextButton(onClick = { breakMinutes = (breakMinutes + 1).coerceAtMost(30) }) { Text("+") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = lockEnabled, onCheckedChange = { lockEnabled = it; prefs.edit().putBoolean("lock_enabled", it).apply() })
                        Text("طلب تثبيت التطبيق أثناء المذاكرة")
                    }
                    Button(enabled = running && lockEnabled && !lockActive && activity != null, onClick = {
                        val result = StudyLockController.enter(activity!!)
                        lockActive = result.started
                        status = result.message
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Lock, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("تفعيل قفل جلسة المذاكرة")
                    }
                    OutlinedButton(enabled = lockActive && activity != null, onClick = {
                        lockActive = !StudyLockController.exit(activity!!)
                        if (!lockActive) status = "تم فك تثبيت التطبيق"
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.LockOpen, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("إنهاء القفل")
                    }
                    Text("تثبيت الشاشة يخضع لتأكيد Android. وضع Kiosk الكامل يحتاج إعداد الجهاز كجهاز مُدار.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { SharedStudyRoomCard() }
        item {
            Text("مهامي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = taskDraft, onValueChange = { taskDraft = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اكتب مهمة جديدة") }, trailingIcon = {
                IconButton(onClick = {
                    if (taskDraft.isNotBlank() && uid.isNotBlank()) {
                        val id = System.currentTimeMillis()
                        val title = taskDraft.trim()
                        scope.launch {
                            try {
                                db.collection("studyTasks").document(uid).collection("tasks").document(id.toString())
                                    .set(mapOf("id" to id, "uid" to uid, "title" to title, "proofUrl" to "",
                                        "createdAt" to System.currentTimeMillis(), "updatedAt" to System.currentTimeMillis())).await()
                                taskDraft = ""
                            } catch (e: Exception) { taskSyncMessage = e.localizedMessage ?: "تعذر حفظ المهمة." }
                        }
                    } else if (uid.isBlank()) taskSyncMessage = "سجّل الدخول لحفظ المهام ومزامنتها."
                }) { Icon(Icons.Rounded.Add, contentDescription = "إضافة مهمة") }
            })
        }
        items(tasks, key = { it.id }) { task ->
            Card {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(task.title, fontWeight = FontWeight.SemiBold)
                        Text(if (task.proof == null) "لم يرفق دليل بعد" else "تم إرفاق صورة دليل", style = MaterialTheme.typography.labelSmall)
                    }
                    if (task.proof == null) TextButton(enabled = !proofUploading, onClick = { proofTarget = task.id; proofPicker.launch(arrayOf("image/*")) }) { Text(if (proofUploading) "جارٍ الرفع…" else "إرفاق صورة") }
                    else {
                        Text("مكتملة ✓", color = Color(0xFF4CAF50))
                        TextButton(onClick = { task.proof?.toString()?.let { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(it))) } } }) { Text("عرض الإثبات") }
                    }
                    IconButton(onClick = {
                        scope.launch {
                            try { db.collection("studyTasks").document(uid).collection("tasks").document(task.id.toString()).delete().await() }
                            catch (e: Exception) { taskSyncMessage = e.localizedMessage ?: "تعذر حذف المهمة." }
                        }
                    }) { Text("×") }
                }
            }
        }
        item { taskSyncMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) } }
    }
}
