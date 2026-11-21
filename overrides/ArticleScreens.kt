package com.aurora.player.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading=true
        try {
            items=db.collection("articles").get().await().documents.mapNotNull { d ->
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
                        TextButton(onClick={scope.launch { try { db.collection("articles").document(a.id).delete().await(); reload() } catch(e:Exception) { error=e.localizedMessage } }) { Text("حذف") }
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
            OutlinedTextField(cover,{cover=it.take(1000)},label={Text("رابط صورة الغلاف المباشر")},modifier=Modifier.fillMaxWidth())
            if(cover.startsWith("https://")) AsyncImage(model=cover,contentDescription="معاينة الغلاف",modifier=Modifier.fillMaxWidth().heightIn(max=180.dp))
            OutlinedTextField(body,{body=it.take(30000)},label={Text("نص المقال")},minLines=8,modifier=Modifier.fillMaxWidth())
            Row { Checkbox(draft,{draft=it}); Text("حفظ كمسودة (أزل العلامة للنشر)") }
        }
    },confirmButton={TextButton(enabled=!busy,onClick={scope.launch {
        busy=true
        try {
            require(title.trim().length>=3) { "العنوان يجب أن يكون 3 أحرف على الأقل." }
            require(body.isNotBlank()) { "اكتب نص المقال." }
            require(cover.isBlank() || cover.startsWith("https://")) { "رابط الغلاف يجب أن يبدأ بـ HTTPS." }
            val data=hashMapOf<String,Any>("title" to title.trim(),"subtitle" to subtitle.trim(),"body" to body.trim(),"coverUrl" to cover.trim(),"authorUid" to user.uid,"authorName" to (user.displayName ?: "كاتب Whisper"),"status" to if(draft) "draft" else "published","updatedAt" to FieldValue.serverTimestamp())
            if(selected==null) { data["createdAt"]=FieldValue.serverTimestamp(); if(!draft) data["publishedAt"]=FieldValue.serverTimestamp(); db.collection("articles").add(data).await() }
            else { if(!draft && selected!!.status!="published") data["publishedAt"]=FieldValue.serverTimestamp(); db.collection("articles").document(selected!!.id).update(data).await() }
            editor=false; reload()
        } catch(e:Exception) { error=e.localizedMessage ?: "تعذر حفظ المقال." }
        busy=false
    })}{Text(if(busy) "جارٍ الحفظ…" else if(draft) "حفظ المسودة" else "نشر المقال")}},dismissButton={TextButton(onClick={editor=false}){Text("إلغاء")}})

    selected?.takeIf { !editor }?.let { a ->
        AlertDialog(onDismissRequest={selected=null},title={Text(a.title)},text={
            Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                if(a.cover.isNotBlank()) AsyncImage(model=a.cover,contentDescription=null,modifier=Modifier.fillMaxWidth().heightIn(max=220.dp))
                if(a.subtitle.isNotBlank()) Text(a.subtitle,style=MaterialTheme.typography.titleSmall)
                Text("بقلم ${a.author}",style=MaterialTheme.typography.labelSmall)
                Text(a.body,style=MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick={
                    val send=Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,a.title+"\n\n"+a.body.take(500)) }
                    context.startActivity(Intent.createChooser(send,"مشاركة المقال"))
                }) { Text("مشاركة") }
            }
        },confirmButton={TextButton(onClick={selected=null}){Text("رجوع")}})
    }
}
