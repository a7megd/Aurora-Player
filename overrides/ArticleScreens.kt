package com.aurora.player.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts


import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import android.content.Intent
import android.media.MediaPlayer
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.File
import android.net.Uri
import com.aurora.player.media.CloudinaryMediaUploader
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private data class WhisperArticle(val id:String,val title:String,val subtitle:String,val body:String,val cover:String,val uid:String,val author:String,val status:String)

@Composable
fun ArticleHubDialog(user:FirebaseUser,onDismiss:()->Unit) {
    val db= remember { FirebaseFirestore.getInstance() }
    val scope= rememberCoroutineScope()
    val context= LocalContext.current
    var items by remember { mutableStateOf<List<WhisperArticle>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<WhisperArticle?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var articleRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var articleRecordingFile by remember { mutableStateOf<File?>(null) }
    var articleRecording by remember { mutableStateOf(false) }
    fun startArticleAudioRecording() {
        try {
            val file = File(context.cacheDir, "whisper-article-" + System.currentTimeMillis() + ".m4a")
            val mediaRecorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context)
                else @Suppress("DEPRECATION") MediaRecorder()
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioEncodingBitRate(128000)
            mediaRecorder.setAudioSamplingRate(44100)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
            articleRecorder = mediaRecorder
            articleRecordingFile = file
            articleRecording = true
            message = "جارٍ تسجيل صوت المقال…"
        } catch (e: Exception) { message = e.localizedMessage ?: "تعذر بدء تسجيل المقال." }
    }
    fun stopArticleAudioRecording() {
        val active = articleRecorder
        val file = articleRecordingFile
        try {
            active?.stop()
            if (file != null && file.exists() && file.length() > 0L) {
                audioUrl = Uri.fromFile(file).toString()
                message = "اكتمل التسجيل؛ سيتم رفعه عند حفظ المقال."
            } else message = "التسجيل فارغ؛ حاول مرة أخرى."
        } catch (e: Exception) { message = e.localizedMessage ?: "تعذر إنهاء التسجيل." }
        runCatching { active?.release() }
        articleRecorder = null
        articleRecordingFile = null
        articleRecording = false
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startArticleAudioRecording() else message = "اسمح باستخدام الميكروفون لتسجيل المقال."
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { articleRecorder?.stop() }
            runCatching { articleRecorder?.release() }
            articleRecordingFile?.delete()
        }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            cover = uri.toString()
        }
    }

    suspend fun reload() {
        loading=true
        try {
            val docs=(db.collection("articles").whereEqualTo("status","published").get().await().documents +
                db.collection("articles").whereEqualTo("authorUid",user.uid).get().await().documents).distinctBy { it.id }
            items=docs.mapNotNull { d ->
                val uid=d.getString("authorUid") ?: return@mapNotNull null
                WhisperArticle(d.id,d.getString("title").orEmpty(),d.getString("subtitle").orEmpty(),d.getString("body").orEmpty(),d.getString("coverUrl").orEmpty(),uid,d.getString("authorName") ?: "كاتب Whisper",d.getString("status") ?: "draft")
            }.filter { it.status=="published" || it.uid==user.uid }.sortedByDescending { it.id }
            error=null
        } catch(e:Exception) { error=e.localizedMessage ?: "تعذر تحميل المقالات." }
        loading=false
    }
    fun newArticle() { selected=null; title=""; subtitle=""; body=""; cover=""; draft=true; editor=true }
    fun editArticle(a:WhisperArticle) { selected=a; title=a.title; subtitle=a.subtitle; body=a.body; cover=a.cover; draft=a.status!="published"; editor=true }
    LaunchedEffect(user.uid) { reload() }

    AlertDialog(onDismissRequest=onDismiss,title={Text("منشورات Whisper")},text={
        Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("مساحة مقالات مستوحاة من Substack: اكتب، احفظ مسودة، وانشر للقراء.")
            Button(onClick={newArticle()},modifier=Modifier.fillMaxWidth()){Text("اكتب مقالًا جديدًا")}
            if(loading) CircularProgressIndicator()
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            items.forEach { a ->
                Card { Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    if(a.cover.isNotBlank()) AsyncImage(model=a.cover,contentDescription=null,modifier=Modifier.fillMaxWidth().heightIn(max=180.dp))
                    Text(a.title,style=MaterialTheme.typography.titleMedium)
                    if(a.subtitle.isNotBlank()) Text(a.subtitle,style=MaterialTheme.typography.bodySmall)
                    Text("بقلم ${a.author}"+if(a.status=="draft") " • مسودة" else "",style=MaterialTheme.typography.labelSmall)
                    TextButton(onClick={selected=a}) { Text("قراءة المقال") }
                    if(a.uid==user.uid) Row {
                        TextButton(onClick={editArticle(a)}) { Text("تعديل") }
                        TextButton(onClick = { scope.launch { try { db.collection("articles").document(a.id).delete().await(); reload() } catch(e:Exception) { error=e.localizedMessage } } }) { Text("حذف") }
                    }
                } }
            }
            if(!loading && items.isEmpty()) Text("لا توجد مقالات بعد. كن أول من ينشر مقالًا.")
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("إغلاق")}})

    if(editor) AlertDialog(onDismissRequest={if(!busy) editor=false},title={Text("محرر المقالات")},text={
        Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title,{title=it.take(120)},label={Text("عنوان المقال")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(subtitle,{subtitle=it.take(220)},label={Text("عنوان فرعي")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(cover,{cover=it.take(1000)},label={Text("رابط صورة الغلاف (اختياري)")},modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={ coverPicker.launch(arrayOf("image/*")) }) { Text("اختيار صورة من الهاتف") }
                if (cover.startsWith("content://")) TextButton(onClick={cover=""}) { Text("إزالة الصورة") }
            }
            if(cover.startsWith("https://") || cover.startsWith("content://")) AsyncImage(model=cover,contentDescription="معاينة الغلاف",modifier=Modifier.fillMaxWidth().heightIn(max=180.dp))
            OutlinedTextField(body,{body=it.take(30000)},label={Text("نص المقال")},minLines=8,modifier=Modifier.fillMaxWidth())
            Row { Checkbox(draft,{draft=it}); Text("حفظ كمسودة (أزل العلامة للنشر)") }
        }
    },confirmButton={TextButton(enabled=!busy,onClick={scope.launch {
        busy=true
        try {
            require(title.trim().length>=3) { "العنوان يجب أن يكون 3 أحرف على الأقل." }
            require(body.isNotBlank()) { "اكتب نص المقال." }
            require(cover.isBlank() || cover.startsWith("https://") || cover.startsWith("content://")) { "استخدم رابط HTTPS أو اختر صورة من الهاتف." }
            val data=hashMapOf<String,Any>("title" to title.trim(),"subtitle" to subtitle.trim(),"body" to body.trim(),"coverUrl" to cover.trim(),"authorUid" to user.uid,"authorName" to (user.displayName ?: "كاتب Whisper"),"status" to if(draft) "draft" else "published","updatedAt" to FieldValue.serverTimestamp())
            if(selected==null) { data["createdAt"]=FieldValue.serverTimestamp(); if(!draft) data["publishedAt"]=FieldValue.serverTimestamp(); db.collection("articles").add(data).await() }
            else { if(!draft && selected!!.status!="published") data["publishedAt"]=FieldValue.serverTimestamp(); db.collection("articles").document(selected!!.id).update(data).await() }
            editor=false; reload()
        } catch(e:Exception) { error=e.localizedMessage ?: "تعذر حفظ المقال." }
        busy=false
        } }
    ) { Text(if(busy) "جارٍ الحفظ…" else if(draft) "حفظ المسودة" else "نشر المقال") } }, dismissButton = { TextButton(onClick = { editor=false }) { Text("إلغاء") } })

    selected?.takeIf { !editor }?.let { a ->
        AlertDialog(onDismissRequest={selected=null},title={Text(a.title)},text={
            Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                if(a.cover.isNotBlank()) AsyncImage(model=a.cover,contentDescription=null,modifier=Modifier.fillMaxWidth().heightIn(max=220.dp))
                if(a.subtitle.isNotBlank()) Text(a.subtitle,style=MaterialTheme.typography.titleSmall)
                Text("بقلم ${a.author}",style=MaterialTheme.typography.labelSmall)
                Text(a.body,style=MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick={
                    val send=Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,a.title+"\n"+a.body.take(500)) }
                    context.startActivity(Intent.createChooser(send,"مشاركة المقال"))
                }) { Text("مشاركة") }
            }
        },confirmButton={TextButton(onClick={selected=null}){Text("رجوع")}})
    }
}


private data class FullWhisperArticle(
    val id: String, val title: String, val subtitle: String, val body: String,
    val cover: String, val uid: String, val author: String, val fontSize: Int,
    val bold: Boolean, val align: String, val fontColor: String = "#F4F1E8", val audioUrl: String = ""
)

private fun articleAlignment(value: String): TextAlign = when(value) {
    "center" -> TextAlign.Center
    "start" -> TextAlign.Start
    "justify" -> TextAlign.Justify
    else -> TextAlign.Right
}

@Composable
fun ArticleWriterScreen(user: FirebaseUser) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf("") }
    var audioUrl by remember { mutableStateOf("") }
    var fontSize by remember { mutableIntStateOf(19) }
    var bold by remember { mutableStateOf(false) }
    var align by remember { mutableStateOf("right") }
    var bodyColor by remember { mutableStateOf("#F4F1E8") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var myArticles by remember { mutableStateOf<List<FullWhisperArticle>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            cover = uri.toString()
            message = "تم اختيار صورة الغلاف؛ سيتم رفعها عند حفظ المقال."
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            audioUrl = uri.toString()
            message = "تم اختيار الملف الصوتي؛ سيتم رفعه عند حفظ المقال."
        }
    }

    suspend fun uploadPendingArticleMedia(): Pair<String, String> {
        var finalCover = cover.trim()
        var finalAudio = audioUrl.trim()
        if (finalCover.startsWith("content://")) {
            finalCover = CloudinaryMediaUploader.upload(context, Uri.parse(finalCover), "Whisper-Player/Articles").secureUrl
            cover = finalCover
        }
        if (finalAudio.startsWith("content://") || finalAudio.startsWith("file://")) {
            finalAudio = CloudinaryMediaUploader.upload(context, Uri.parse(finalAudio), "Whisper-Player/Voice").secureUrl
            audioUrl = finalAudio
        }
        require(finalCover.isBlank() || finalCover.startsWith("https://")) { "تعذر التحقق من صورة الغلاف." }
        require(finalAudio.isBlank() || finalAudio.startsWith("https://")) { "تعذر التحقق من الملف الصوتي." }
        return finalCover to finalAudio
    }

    suspend fun reloadMine() {
        myArticles = db.collection("articles").whereEqualTo("authorUid", user.uid).get().await().documents.map { d ->
            FullWhisperArticle(d.id, d.getString("title").orEmpty(), d.getString("subtitle").orEmpty(),
                d.getString("body").orEmpty(), d.getString("coverUrl").orEmpty(), user.uid,
                d.getString("authorName") ?: user.displayName.orEmpty(),
                (d.getLong("bodyFontSize") ?: 19L).toInt(), d.getBoolean("bodyBold") == true,
                d.getString("bodyAlign") ?: "right", d.getString("bodyColor") ?: "#F4F1E8", d.getString("audioUrl").orEmpty())
        }.sortedByDescending { it.id }
    }
    LaunchedEffect(user.uid) { try { reloadMine() } catch (e: Exception) { message = e.localizedMessage } }

    fun clearEditor() {
        title = ""; subtitle = ""; body = ""; cover = ""; audioUrl = ""; fontSize = 19; bold = false; align = "right"; bodyColor = "#F4F1E8"; editingId = null
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (editingId == null) "اكتب مقالك" else "تعديل المقال", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("مساحة كتابة هادئة وكاملة الشاشة. يمكنك حفظ مسودة والعودة إليها وقتما تشاء.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = title, onValueChange = { title = it.take(120) }, label = { Text("عنوان المقال") },
                modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = subtitle, onValueChange = { subtitle = it.take(220) }, label = { Text("عنوان فرعي (اختياري)") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { coverPicker.launch(arrayOf("image/*")) }) { Text("اختيار صورة من الهاتف") }
                if (cover.startsWith("content://")) TextButton(onClick = { cover = "" }) { Text("إزالة الصورة") }
            }
            if (cover.startsWith("https://") || cover.startsWith("content://")) {
                AsyncImage(model = cover, contentDescription = "معاينة صورة الغلاف", modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(enabled = !articleRecording && !busy, onClick = { audioPicker.launch(arrayOf("audio/*")) }) { Text("اختيار صوت") }
                OutlinedButton(enabled = !busy, onClick = {
                    if (articleRecording) stopArticleAudioRecording()
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startArticleAudioRecording()
                    else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text(if (articleRecording) "إيقاف التسجيل" else "تسجيل المقال") }
                if (audioUrl.isNotBlank()) TextButton(onClick = { audioUrl = ""; articleRecordingFile?.delete() }) { Text("إزالة الصوت") }
            }
            Text(if (audioUrl.isBlank()) "يمكنك إرفاق ملف صوتي من جهازك ليصبح متاحًا للقراء." else "تم اختيار ملف صوتي للمقال.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("لون الخط:")
                listOf("#222222" to "أسود", "#6D28D9" to "بنفسجي", "#1565C0" to "أزرق", "#B23A48" to "أحمر", "#16805D" to "أخضر").forEach { (hex,label) ->
                    FilterChip(selected = bodyColor == hex, onClick = { bodyColor = hex }, label = { Text(label) })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { fontSize = (fontSize - 1).coerceAtLeast(14) }) { Text("أ−") }
                    Text("${fontSize}sp", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { fontSize = (fontSize + 1).coerceAtMost(32) }) { Text("أ+") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { bold = !bold }) { Text(if (bold) "عادي" else "عريض", fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { align = when(align) { "right" -> "center"; "center" -> "justify"; "justify" -> "start"; else -> "right" } }) {
                        Text(when(align) { "center" -> "توسيط"; "justify" -> "ضبط"; "start" -> "يسار"; else -> "يمين" })
                    }
                }
            }
            Surface(Modifier.fillMaxWidth().heightIn(min = 520.dp), shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("نص المقال", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = body, onValueChange = { body = it.take(30000) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 480.dp),
                        placeholder = { Text("ابدأ الكتابة هنا…") }, minLines = 12,
                        textStyle = TextStyle(fontSize = fontSize.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                            textAlign = articleAlignment(align), lineHeight = (fontSize + 10).sp,
                            color = runCatching { Color(android.graphics.Color.parseColor(bodyColor)) }.getOrDefault(MaterialTheme.colorScheme.onSurface)))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            require(title.trim().length >= 3) { "اكتب عنوانًا من 3 أحرف على الأقل." }
                            require(body.isNotBlank()) { "اكتب نص المقال أولًا." }
                            val (uploadedCover, uploadedAudio) = uploadPendingArticleMedia()
                            val data = hashMapOf<String, Any>("title" to title.trim(), "subtitle" to subtitle.trim(),
                                "body" to body.trim(), "coverUrl" to uploadedCover, "authorUid" to user.uid,
                                "authorName" to (user.displayName ?: "كاتب Whisper"), "status" to "draft",
                                "bodyFontSize" to fontSize.toLong(), "bodyBold" to bold, "bodyAlign" to align, "bodyColor" to bodyColor, "audioUrl" to uploadedAudio,
                                "updatedAt" to FieldValue.serverTimestamp())
                            if (editingId == null) { data["createdAt"] = FieldValue.serverTimestamp(); db.collection("articles").add(data).await() }
                            else db.collection("articles").document(editingId!!).update(data - "authorUid").await()
                            message = "تم حفظ المسودة."; reloadMine()
                        } catch (e: Exception) { message = e.localizedMessage ?: "تعذر حفظ المسودة." }
                        busy = false
                    }
                }) { Text("حفظ مسودة") }
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            require(title.trim().length >= 3) { "اكتب عنوانًا من 3 أحرف على الأقل." }
                            require(body.isNotBlank()) { "اكتب نص المقال أولًا." }
                            val (uploadedCover, uploadedAudio) = uploadPendingArticleMedia()
                            val data = hashMapOf<String, Any>("title" to title.trim(), "subtitle" to subtitle.trim(),
                                "body" to body.trim(), "coverUrl" to uploadedCover, "authorUid" to user.uid,
                                "authorName" to (user.displayName ?: "كاتب Whisper"), "status" to "published",
                                "bodyFontSize" to fontSize.toLong(), "bodyBold" to bold, "bodyAlign" to align, "bodyColor" to bodyColor, "audioUrl" to uploadedAudio,
                                "updatedAt" to FieldValue.serverTimestamp(), "publishedAt" to FieldValue.serverTimestamp())
                            if (editingId == null) { data["createdAt"] = FieldValue.serverTimestamp(); db.collection("articles").add(data).await() }
                            else db.collection("articles").document(editingId!!).update(data - "authorUid").await()
                            message = "تم نشر المقال."; clearEditor(); reloadMine()
                        } catch (e: Exception) { message = e.localizedMessage ?: "تعذر نشر المقال." }
                        busy = false
                    }
                }) { Text(if (busy) "جارٍ الحفظ…" else "نشر المقال") }
                TextButton(enabled = !busy, onClick = { clearEditor() }) { Text("جديد") }
            }
            Divider()
            Text("مسوداتي ومقالاتي", style = MaterialTheme.typography.titleLarge)
            if (myArticles.isEmpty()) Text("لم تكتب مقالات بعد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            myArticles.forEach { article ->
                Card {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(article.title, fontWeight = FontWeight.SemiBold)
                            Text(if (article.id == editingId) "قيد التحرير" else "اضغط تعديل للمتابعة", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = {
                            editingId = article.id; title = article.title; subtitle = article.subtitle; body = article.body
                            cover = article.cover; fontSize = article.fontSize; bold = article.bold; align = article.align; bodyColor = article.fontColor; audioUrl = article.audioUrl
                        }) { Text("تعديل") }
                    }
                }
            }
        }
    }
    message?.let { msg -> AlertDialog(onDismissRequest = { message = null }, text = { Text(msg) },
        confirmButton = { TextButton(onClick = { message = null }) { Text("حسنًا") } }) }
}

@Composable
fun ArticleReaderScreen(user: FirebaseUser) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    var articles by remember { mutableStateOf<List<FullWhisperArticle>>(emptyList()) }
    var selected by remember { mutableStateOf<FullWhisperArticle?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(user.uid) {
        loading = true
        try {
            articles = db.collection("articles").whereEqualTo("status", "published").get().await().documents.mapNotNull { d ->
                val uid = d.getString("authorUid") ?: return@mapNotNull null
                FullWhisperArticle(d.id, d.getString("title").orEmpty(), d.getString("subtitle").orEmpty(),
                    d.getString("body").orEmpty(), d.getString("coverUrl").orEmpty(), uid,
                    d.getString("authorName") ?: "كاتب Whisper", (d.getLong("bodyFontSize") ?: 19L).toInt(),
                    d.getBoolean("bodyBold") == true, d.getString("bodyAlign") ?: "right", d.getString("bodyColor") ?: "auto", d.getString("audioUrl").orEmpty())
            }.sortedByDescending { it.id }
        } catch (e: Exception) { error = e.localizedMessage ?: "تعذر تحميل المقالات." }
        loading = false
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (selected == null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("مقالات Whisper", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("اقرأ أفكار وتجارب كتّاب المجتمع في صفحات مريحة وكاملة.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (loading) CircularProgressIndicator()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!loading && articles.isEmpty() && error == null) Text("لا توجد مقالات منشورة بعد.")
                articles.forEach { article ->
                    Card(onClick = { selected = article }) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            if (article.cover.isNotBlank()) AsyncImage(model = article.cover, contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 210.dp))
                            Text(article.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (article.subtitle.isNotBlank()) Text(article.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("بقلم ${article.author}", style = MaterialTheme.typography.labelSmall)
                            Text(article.body.take(190) + if (article.body.length > 190) "…" else "", maxLines = 4, style = MaterialTheme.typography.bodyMedium)
                            Text("اقرأ المقال كاملًا ←", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        } else {
            val article = selected!!
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selected = null }) { Text("رجوع للمقالات") }
                    Spacer(Modifier.weight(1f))
                    Text("Whisper", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (article.cover.isNotBlank()) AsyncImage(model = article.cover, contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp))
                    Text(article.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    if (article.subtitle.isNotBlank()) Text(article.subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("بقلم ${article.author}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Divider()
                    if (article.audioUrl.isNotBlank()) {
                        var playingAudio by remember(article.id) { mutableStateOf(false) }
                        OutlinedButton(onClick = {
                            try {
                                val player = MediaPlayer()
                                player.setDataSource(article.audioUrl)
                                player.setOnPreparedListener { it.start(); playingAudio = true }
                                player.setOnCompletionListener { playingAudio = false; it.release() }
                                player.prepareAsync()
                            } catch (_: Exception) { playingAudio = false }
                        }, modifier = Modifier.fillMaxWidth()) { Text(if (playingAudio) "جاري تشغيل المقال الصوتي…" else "استمع إلى المقال") }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                    ) {
                        Text(
                            article.body,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                            style = TextStyle(
                                fontSize = article.fontSize.coerceIn(16, 32).sp,
                                fontWeight = if (article.bold) FontWeight.Bold else FontWeight.Normal,
                                textAlign = articleAlignment(article.align),
                                lineHeight = (article.fontSize.coerceIn(16, 32) + 13).sp,
                                letterSpacing = 0.15.sp,
                                color = if (article.fontColor.equals("auto", true) ||
                                    article.fontColor.equals("#222222", true) ||
                                    article.fontColor.equals("#F4F1E8", true))
                                    MaterialTheme.colorScheme.onSurface
                                else runCatching { Color(android.graphics.Color.parseColor(article.fontColor)) }
                                    .getOrDefault(MaterialTheme.colorScheme.onSurface)
                            )
                        )
                    }
                    OutlinedButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, article.title + "\n" + article.body) }
                        context.startActivity(Intent.createChooser(send, "مشاركة المقال"))
                    }) { Text("مشاركة المقال") }
                    ArticleComments(article.id, user)
                }
            }
        }
    }
}


private data class ArticleComment(val author: String, val body: String, val audioUrl: String)

@Composable
private fun ArticleAudioComment(url: String) {
    var playing by remember(url) { mutableStateOf(false) }
    var player by remember(url) { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(url) {
        onDispose { runCatching { player?.release() }; player = null }
    }
    OutlinedButton(onClick = {
        if (playing && player != null) {
            runCatching { player?.pause() }
            playing = false
        } else {
            runCatching { player?.release() }
            try {
                player = MediaPlayer().apply {
                    setDataSource(url)
                    setOnPreparedListener { it.start(); playing = true }
                    setOnCompletionListener { playing = false; it.seekTo(0) }
                    setOnErrorListener { _, _, _ -> playing = false; true }
                    prepareAsync()
                }
            } catch (_: Exception) { playing = false }
        }
    }) { Text(if (playing) "إيقاف التعليق الصوتي" else "▶ تشغيل التعليق الصوتي") }
}

@Composable
private fun ArticleComments(articleId: String, user: FirebaseUser) {
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var comments by remember { mutableStateOf<List<ArticleComment>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recording by remember { mutableStateOf(false) }
    var audioUploading by remember { mutableStateOf(false) }
    var audioUrl by remember { mutableStateOf("") }

    suspend fun reload() {
        try {
            comments = db.collection("articles").document(articleId).collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING).limit(100).get().await().documents.map { d ->
                    ArticleComment(d.getString("authorName") ?: "قارئ", d.getString("body").orEmpty(),
                        d.getString("audioUrl").orEmpty())
                }
        } catch (e: Exception) { error = e.localizedMessage ?: "تعذر تحميل التعليقات." }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            try {
                val file = File(context.cacheDir, "article-comment-" + System.currentTimeMillis() + ".m4a")
                val mediaRecorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context)
                    else @Suppress("DEPRECATION") MediaRecorder()
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mediaRecorder.setAudioEncodingBitRate(96000)
                mediaRecorder.setAudioSamplingRate(44100)
                mediaRecorder.setOutputFile(file.absolutePath)
                mediaRecorder.prepare()
                mediaRecorder.start()
                recorder = mediaRecorder
                recordingFile = file
                recording = true
                error = null
            } catch (e: Exception) { error = e.localizedMessage ?: "تعذر بدء التسجيل الصوتي." }
        } else error = "اسمح باستخدام الميكروفون لإضافة تعليق صوتي."
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { recorder?.release() }; recordingFile?.delete() }
    }
    LaunchedEffect(articleId) { reload() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("التعليقات (" + comments.size + ")", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        comments.forEach { item ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.author, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    if (item.body.isNotBlank()) Text(item.body)
                    if (item.audioUrl.isNotBlank()) ArticleAudioComment(item.audioUrl)
                }
            }
        }
        OutlinedTextField(draft, { draft = it.take(2000) }, modifier = Modifier.fillMaxWidth(),
            label = { Text("اكتب تعليقك") }, minLines = 2)
        Text("التعليقات الصوتية غير متاحة حاليًا؛ يمكنك إضافة تعليق نصي.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = !busy && !audioUploading && (draft.isNotBlank() || audioUrl.isNotBlank()), onClick = {
            val body = draft.trim()
            busy = true
            scope.launch {
                try {
                    db.collection("articles").document(articleId).collection("comments").add(mapOf(
                        "uid" to user.uid,
                        "authorName" to (user.displayName ?: "قارئ Anti-dopamine"),
                        "body" to body,
                        "audioUrl" to audioUrl,
                        "createdAt" to FieldValue.serverTimestamp()
                    )).await()
                    draft = ""
                    audioUrl = ""
                    error = null
                    reload()
                } catch (e: Exception) { error = e.localizedMessage ?: "تعذر نشر التعليق." }
                busy = false
            }
        }) { Text(if (busy) "جارٍ الإرسال…" else "إضافة التعليق") }
    }
}
