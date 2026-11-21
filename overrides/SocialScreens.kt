package com.aurora.player.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private data class WhisperSocialPost(val id:String,val uid:String,val name:String,val kind:String,val body:String,val createdAt:Long)

@Composable
fun SocialPostsScreen(user: FirebaseUser) {
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf("اقتباس") }
    var draft by remember { mutableStateOf("") }
    var posts by remember { mutableStateOf<List<WhisperSocialPost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    suspend fun reload() {
        loading = true
        try {
            posts = db.collection("communityPosts").orderBy("createdAt", Query.Direction.DESCENDING).limit(80)
                .get().await().documents.map { d ->
                    WhisperSocialPost(d.id,d.getString("uid").orEmpty(),d.getString("authorName") ?: "عضو Whisper",
                        d.getString("kind") ?: "منشور",d.getString("body").orEmpty(),d.getTimestamp("createdAt")?.toDate()?.time ?: 0L)
                }
            message = null
        } catch (e:Exception) { message = e.localizedMessage ?: "تعذر تحميل المنشورات." }
        loading = false
    }
    LaunchedEffect(user.uid) { reload() }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("مساحة Whisper", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("قصائد واقتباسات وأفكار قصيرة من مجتمع الكتّاب", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("شارك شيئًا يستحق القراءة", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("قصيدة","اقتباس","منشور").forEach { value ->
                            FilterChip(selected = kind == value, onClick = { kind = value }, label = { Text(value) })
                        }
                    }
                    OutlinedTextField(draft,{draft=it.take(5000)},modifier=Modifier.fillMaxWidth(),
                        minLines=4,placeholder={Text("اكتب كلماتك هنا…")},label={Text(kind)})
                    Button(enabled = !busy && draft.isNotBlank(), onClick = {
                        busy = true
                        scope.launch {
                            try {
                                db.collection("communityPosts").add(mapOf("uid" to user.uid,
                                    "authorName" to (user.displayName ?: "عضو Whisper"),"kind" to kind,
                                    "body" to draft.trim(),"createdAt" to FieldValue.serverTimestamp())).await()
                                draft = ""; reload()
                            } catch(e:Exception) { message=e.localizedMessage ?: "تعذر نشر المشاركة." }
                            busy = false
                        }
                    }, modifier=Modifier.fillMaxWidth()) { Text(if(busy) "جارٍ النشر…" else "نشر في المجتمع") }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text("أحدث المشاركات",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                TextButton(onClick={scope.launch{reload()}},enabled=!loading){Text("تحديث")}
            }
            if(loading) CircularProgressIndicator()
            message?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            posts.forEach { post ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text(post.name,fontWeight=FontWeight.SemiBold)
                            AssistChip(onClick={},label={Text(post.kind)})
                        }
                        Text(post.body,style=MaterialTheme.typography.bodyLarge,
                            fontWeight=if(post.kind=="اقتباس") FontWeight.Medium else FontWeight.Normal)
                        if(post.uid==user.uid) TextButton(onClick={
                            scope.launch { try { db.collection("communityPosts").document(post.id).delete().await(); reload() }
                                catch(e:Exception){message=e.localizedMessage} }
                        }) { Text("حذف المشاركة") }
                    }
                }
            }
            if(!loading && posts.isEmpty()) Text("لا توجد مشاركات بعد. ابدأ أول مشاركة.")
        }
    }
}

private data class WhisperChatMessage(val id:String,val uid:String,val name:String,val body:String)

@Composable
fun ChatScreen(user: FirebaseUser) {
    val db= remember { FirebaseFirestore.getInstance() }
    val scope= rememberCoroutineScope()
    var threads by remember { mutableStateOf<List<Pair<String,String>>>(emptyList()) }
    var selected by remember { mutableStateOf<Pair<String,String>?>(null) }
    var messages by remember { mutableStateOf<List<WhisperChatMessage>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun reloadThreads() {
        try {
            val rows=FirebaseCommunity.approvedChats(user.uid)
            val profiles=rows.map { row -> row["otherUid"].orEmpty() }.distinct().associateWith { uid ->
                db.collection("users").document(uid).get().await().getString("displayName") ?: "صديق"
            }
            threads=rows.map { row -> row["threadId"].orEmpty() to profiles[row["otherUid"].orEmpty()].orEmpty() }
        } catch(e:Exception){error=e.localizedMessage ?: "تعذر تحميل المحادثات."}
    }
    suspend fun reloadMessages(threadId:String) {
        try {
            messages=db.collection("chatThreads").document(threadId).collection("messages")
                .orderBy("createdAt",Query.Direction.ASCENDING).limit(150).get().await().documents.map { d ->
                    WhisperChatMessage(d.id,d.getString("uid").orEmpty(),d.getString("authorName") ?: "عضو",d.getString("body").orEmpty())
                }
        } catch(e:Exception){error=e.localizedMessage ?: "تعذر تحميل الرسائل."}
    }
    LaunchedEffect(user.uid){reloadThreads()}
    LaunchedEffect(selected?.first) {
        val thread=selected?.first
        if(thread!=null) reloadMessages(thread)
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                if(selected!=null) TextButton(onClick={selected=null}){Text("المحادثات")}
                Text(if(selected==null) "محادثات الأصدقاء" else selected!!.second,
                    style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick={scope.launch{if(selected==null)reloadThreads() else reloadMessages(selected!!.first)}}){Text("↻")}
            }
            if(selected==null) {
                Text("لن تظهر المحادثة إلا بعد مراجعة الإدارة والموافقة على طلب التواصل.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                threads.forEach { thread ->
                    Card(onClick={selected=thread}) { Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(thread.second,Modifier.weight(1f),fontWeight=FontWeight.SemiBold); Text("فتح المحادثة")
                    } }
                }
                if(threads.isEmpty()) Text("لا توجد محادثات معتمدة حتى الآن.")
            } else {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    messages.forEach { msg ->
                        Card(colors=CardDefaults.cardColors(containerColor=if(msg.uid==user.uid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(if(msg.uid==user.uid) "أنت" else msg.name,style=MaterialTheme.typography.labelSmall)
                                Text(msg.body)
                            }
                        }
                    }
                }
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(draft,{draft=it.take(2000)},modifier=Modifier.weight(1f),placeholder={Text("اكتب رسالة…")},maxLines=4)
                    Button(enabled=!busy&&draft.isNotBlank(),onClick={
                        val body=draft.trim();val thread=selected!!.first;busy=true
                        scope.launch { try {
                            db.collection("chatThreads").document(thread).collection("messages").add(mapOf(
                                "uid" to user.uid,"authorName" to (user.displayName ?: "عضو Whisper"),
                                "body" to body,"createdAt" to FieldValue.serverTimestamp())).await()
                            draft="";reloadMessages(thread)
                        } catch(e:Exception){error=e.localizedMessage ?: "تعذر إرسال الرسالة."};busy=false }
                    }){Text("إرسال")}
                }
            }
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    }
}
