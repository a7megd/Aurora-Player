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
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

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
    val proofPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = proofTarget
        if (uri != null && id != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            tasks = tasks.map { if (it.id == id) it.copy(proof = uri) else it }
            status = "تم إرفاق الدليل. أصبحت المهمة مؤهلة للإكمال."
        }
        proofTarget = null
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
                    onBreak = true
                    secondsLeft = breakMinutes * 60
                    status = "انتهت جلسة التركيز. خذ استراحة."
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
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("غرفة المذاكرة التجريبية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("نموذج واجهة محلي؛ مزامنة الأصدقاء بين الأجهزة قيد التطوير.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        listOf("📘", "🦊", "🐼", "🚀", "🌙").forEachIndexed { index, avatar ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .15f), CircleShape), contentAlignment = Alignment.Center) { Text(avatar) }
                                Text(if (index == 0) "أنت" else "طالب " + (index + 1), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text("5 / 10 مقاعد")
                }
            }
        }
        item {
            Text("مهامي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = taskDraft, onValueChange = { taskDraft = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اكتب مهمة جديدة") }, trailingIcon = {
                IconButton(onClick = { if (taskDraft.isNotBlank()) { tasks = tasks + LocalStudyTask(System.currentTimeMillis(), taskDraft.trim()); taskDraft = "" } }) { Icon(Icons.Rounded.Add, contentDescription = "إضافة مهمة") }
            })
        }
        items(tasks, key = { it.id }) { task ->
            Card {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(task.title, fontWeight = FontWeight.SemiBold)
                        Text(if (task.proof == null) "لم يرفق دليل بعد" else "تم إرفاق صورة دليل", style = MaterialTheme.typography.labelSmall)
                    }
                    if (task.proof == null) TextButton(onClick = { proofTarget = task.id; proofPicker.launch(arrayOf("image/*")) }) { Text("إرفاق صورة") }
                    else Text("مكتملة ✓", color = Color(0xFF4CAF50))
                }
            }
        }
        item { Text("يلزم ربط الغرف والمهام والإنجازات بـ Firebase لتصبح مشتركة وموثوقة بين الطلاب.", style = MaterialTheme.typography.bodySmall) }
    }
}
