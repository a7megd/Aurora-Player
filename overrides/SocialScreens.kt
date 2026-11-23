package com.aurora.player.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseUser
import com.aurora.player.community.FirebaseCommunity
import com.aurora.player.community.WhisperMessagingService
import com.aurora.player.community.WhisperCloudinary
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File


private data class WhisperSocialPost(val id:String,val uid:String,val name:String,val kind:String,val body:String)
private data class WhisperComment(val id:String,val uid:String,val name:String,val body:String)
private data class WhisperChatMessage(val id:String,val uid:String,val name:String,val body:String,val type:String,val mediaUrl:String)

private suspend fun uploadWhisperMedia(context: android.content.Context, path:String, uri:Uri, contentType:String):String {
    return WhisperCloudinary.uploadUri(context, uri, "Whisper-Player/$path", "image", contentType)
}
private suspend fun uploadWhisperFile(path:String,file:File,contentType:String):String {
    return WhisperCloudinary.uploadFile(file, "Whisper-Player/$path", "video", contentType)
}

@Composable
fun SocialPostsScreen(user: FirebaseUser) {
    val db= remember { FirebaseFirestore.getInstance() }
    val scope= rememberCoroutineScope()
    var kind by remember { mutableStateOf("اقتباس") }
    var draft by remember { mutableStateOf("") }
    var posts by remember { mutableStateOf<List<WhisperSocialPost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var expandedComments by remember { mutableStateOf<String?>(null) }
    var comments by remember { mutableStateOf<List<WhisperComment>>(emptyList()) }
    var commentDraft by remember { mutableStateOf("") }
    var commentBusy by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading=true
        try {
            posts=db.collection("communityPosts").orderBy("createdAt",Query.Direction.DESCENDING).limit(80).get().await().documents.map { d ->
                WhisperSocialPost(d.id,d.getString("uid").orEmpty(),d.getString("authorName") ?: "عضو Whisper",
                    d.getString("kind") ?: "منشور",d.getString("body").orEmpty())
            }
            message=null
        } catch(e:Exception){message=e.localizedMessage ?: "تعذر تحميل المنشورات."}
        loading=false
    }
    suspend fun loadComments(postId:String) {
        try {
            comments=db.collection("communityPosts").document(postId).collection("comments")
                .orderBy("createdAt",Query.Direction.ASCENDING).limit(100).get().await().documents.map { d ->
                    WhisperComment(d.id,d.getString("uid").orEmpty(),d.getString("authorName") ?: "عضو",d.getString("body").orEmpty())
                }
        } catch(e:Exception){message=e.localizedMessage ?: "تعذر تحميل التعليقات."}
    }
    LaunchedEffect(user.uid){reload()}
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=14.dp,vertical=12.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("مساحة Whisper",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
            Text("قصائد واقتباسات وأفكار من مجتمع الكتّاب",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("شارك شيئًا يستحق القراءة",fontWeight=FontWeight.SemiBold)
                    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        listOf("قصيدة","اقتباس","منشور").forEach { v -> FilterChip(selected=kind==v,onClick={kind=v},label={Text(v)}) }
                    }
                    OutlinedTextField(value=draft,onValueChange={draft=it},modifier=Modifier.fillMaxWidth(),
                        minLines=3,placeholder={Text("بماذا تفكر؟")},label={Text(kind)})
                    Button(enabled=!busy&&draft.isNotBlank(),onClick={
                        busy=true
                        scope.launch { try {
                            db.collection("communityPosts").add(mapOf("uid" to user.uid,"authorName" to (user.displayName ?: "عضو Whisper"),
                                "kind" to kind,"body" to draft.trim(),"createdAt" to FieldValue.serverTimestamp())).await()
                            draft="";reload()
                        } catch(e:Exception){message=e.localizedMessage ?: "تعذر نشر المشاركة."};busy=false }
                    },modifier=Modifier.fillMaxWidth()){Text(if(busy)"جارٍ النشر…" else "نشر المشاركة")}
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text("أحدث المشاركات",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                TextButton(onClick={scope.launch{reload()}},enabled=!loading){Text("تحديث")}
            }
            if(loading) CircularProgressIndicator()
            message?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            posts.forEach { post ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                            Text(post.name,fontWeight=FontWeight.SemiBold)
                            AssistChip(onClick={},label={Text(post.kind)})
                        }
                        Text(post.body,style=MaterialTheme.typography.bodyLarge)
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            TextButton(onClick={
                                if(expandedComments==post.id) expandedComments=null else {
                                    expandedComments=post.id
                                    scope.launch{loadComments(post.id)}
                                }
                            }){Text(if(expandedComments==post.id)"إخفاء التعليقات" else "التعليقات")}
                            Spacer(Modifier.weight(1f))
                            if(post.uid==user.uid) TextButton(onClick={scope.launch{
                                try{db.collection("communityPosts").document(post.id).delete().await();reload()}
                                catch(e:Exception){message=e.localizedMessage}
                            }}){Text("حذف",color=MaterialTheme.colorScheme.error)}
                        }
                        if(expandedComments==post.id) {
                            HorizontalDivider()
                            comments.forEach { c ->
                                Column(Modifier.fillMaxWidth().padding(vertical=3.dp)) {
                                    Text(c.name,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold)
                                    Text(c.body,style=MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if(comments.isEmpty()) Text("كن أول من يعلّق.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(value=commentDraft,onValueChange={commentDraft=it},modifier=Modifier.weight(1f),
                                    placeholder={Text("اكتب تعليقًا…")},maxLines=3)
                                IconButton(enabled=!commentBusy&&commentDraft.isNotBlank(),onClick={
                                    val body=commentDraft.trim();commentBusy=true
                                    scope.launch { try {
                                        db.collection("communityPosts").document(post.id).collection("comments").add(mapOf(
                                            "uid" to user.uid,"authorName" to (user.displayName ?: "عضو Whisper"),
                                            "body" to body,"createdAt" to FieldValue.serverTimestamp())).await()
                                        commentDraft="";loadComments(post.id)
                                    } catch(e:Exception){message=e.localizedMessage ?: "تعذر إرسال التعليق."};commentBusy=false }
                                }){Text("➤")}
                            }
                        }
                    }
                }
            }
            if(!loading&&posts.isEmpty()) Text("لا توجد مشاركات بعد. ابدأ أول مشاركة.")
        }
    }
}

@Composable
private fun WhisperAudioBubble(url:String) {
    var playing by remember(url){mutableStateOf(false)}
    var player by remember(url){mutableStateOf<MediaPlayer?>(null)}
    DisposableEffect(url) { onDispose { try{player?.release()}catch(_:Exception){};player=null } }
    OutlinedButton(onClick={
        if(playing){try{player?.pause()}catch(_:Exception){};playing=false}
        else {
            try {
                player?.release()
                player=MediaPlayer().apply {
                    setDataSource(url)
                    setOnCompletionListener { playing=false }
                    prepareAsync()
                    setOnPreparedListener { it.start();playing=true }
                }
            } catch(_:Exception){playing=false}
        }
    }){Text(if(playing)"إيقاف الصوت" else "▶ تشغيل رسالة صوتية")}
}

@Composable
fun ChatScreen(user: FirebaseUser) {
    val db= remember { FirebaseFirestore.getInstance() }
    val scope= rememberCoroutineScope()
    val context= LocalContext.current
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(user.uid) {
        WhisperMessagingService.syncToken(context,user.uid)
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    var threads by remember { mutableStateOf<List<Pair<String,String>>>(emptyList()) }
    var selected by remember { mutableStateOf<Pair<String,String>?>(null) }
    var messages by remember { mutableStateOf<List<WhisperChatMessage>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recording by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }

    val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        val thread=selected?.first
        if(uri!=null&&thread!=null) scope.launch {
            uploading=true
            try {
                val type=context.contentResolver.getType(uri) ?: "image/jpeg"
                require(type.startsWith("image/")){"الملف المختار ليس صورة."}
                val url=uploadWhisperMedia(context,"Chat/${user.uid}/$thread",uri,type)
                db.collection("chatThreads").document(thread).collection("messages").add(mapOf(
                    "uid" to user.uid,"authorName" to (user.displayName ?: "عضو Whisper"),"type" to "image",
                    "body" to "","mediaUrl" to url,"createdAt" to FieldValue.serverTimestamp())).await()
                reloadChatMessages(db,thread){messages=it}
            }catch(e:Exception){error=e.localizedMessage ?: "تعذر إرسال الصورة."}
            uploading=false
        }
    }
    val micPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if(granted) startWhisperRecording(context,user.uid,selected?.first.orEmpty(),{r->recorder=r.first;recordingFile=r.second;recording=true},{error=it})
        else error="يجب السماح باستخدام الميكروفون لتسجيل رسالة صوتية."
    }

    suspend fun reloadThreads() {
        try {
            val rows=FirebaseCommunity.approvedChats(user.uid)
            val profiles=rows.map{it["otherUid"].orEmpty()}.distinct().associateWith{uid->
                db.collection("users").document(uid).get().await().getString("displayName") ?: "صديق"
            }
            threads=rows.map{it["threadId"].orEmpty() to profiles[it["otherUid"].orEmpty()].orEmpty()}
        }catch(e:Exception){error=e.localizedMessage ?: "تعذر تحميل المحادثات."}
    }
    LaunchedEffect(user.uid){reloadThreads()}
    DisposableEffect(selected?.first) {
        val thread=selected?.first
        if(thread==null) {
            onDispose { }
        } else {
            val reg=db.collection("chatThreads").document(thread).collection("messages")
                .orderBy("createdAt",Query.Direction.ASCENDING).limit(150)
                .addSnapshotListener { snap,e ->
                    if(e!=null){error=e.localizedMessage;return@addSnapshotListener}
                    messages=snap?.documents.orEmpty().map{d->WhisperChatMessage(d.id,d.getString("uid").orEmpty(),
                        d.getString("authorName") ?: "عضو",d.getString("body").orEmpty(),
                        d.getString("type") ?: "text",d.getString("mediaUrl").orEmpty())}
                }
            onDispose { reg.remove() }
        }
    }
    DisposableEffect(Unit){onDispose{try{recorder?.release()}catch(_:Exception){};recordingFile?.delete()}}

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                if(selected!=null) TextButton(onClick={selected=null}){Text("المحادثات")}
                Text(if(selected==null)"محادثات الأصدقاء" else selected!!.second,
                    style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick={scope.launch{if(selected==null)reloadThreads() else reloadChatMessages(db,selected!!.first){messages=it}}}){Text("↻")}
            }
            if(selected==null) {
                Text("تظهر المحادثات بعد موافقة الإدارة على طلب التواصل.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                threads.forEach{t->Card(onClick={selected=t}){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                    Text(t.second,Modifier.weight(1f),fontWeight=FontWeight.SemiBold);Text("فتح المحادثة")
                }}}
                if(threads.isEmpty()) Text("لا توجد محادثات معتمدة حتى الآن.")
            }else{
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    messages.forEach{msg->
                        Card(colors=CardDefaults.cardColors(containerColor=if(msg.uid==user.uid)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)){
                            Column(Modifier.fillMaxWidth().padding(10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                                Text(if(msg.uid==user.uid)"أنت" else msg.name,style=MaterialTheme.typography.labelSmall)
                                if(msg.type=="image"&&msg.mediaUrl.isNotBlank()) AsyncImage(model=msg.mediaUrl,contentDescription="صورة مرسلة",modifier=Modifier.fillMaxWidth().heightIn(max=280.dp))
                                else if(msg.type=="audio"&&msg.mediaUrl.isNotBlank()) WhisperAudioBubble(msg.mediaUrl)
                                else if(msg.body.isNotBlank()) Text(msg.body)
                                TextButton(onClick={scope.launch{try{
                                    db.collection("chatThreads").document(selected!!.first).collection("messages").document(msg.id)
                                        .collection("reactions").document(user.uid).set(mapOf("uid" to user.uid,"emoji" to "❤️",
                                            "updatedAt" to FieldValue.serverTimestamp())).await()
                                }catch(e:Exception){error=e.localizedMessage}}}){Text("❤️ تفاعل")}
                            }
                        }
                    }
                }
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
                    OutlinedTextField(value=draft,onValueChange={draft=it},modifier=Modifier.weight(1f),
                        placeholder={Text("اكتب رسالة…")},maxLines=4)
                    IconButton(enabled=!uploading,onClick={imagePicker.launch("image/*")}){Text("＋")}
                    IconButton(enabled=!uploading,onClick={
                        if(recording){
                            try{
                                recorder?.stop();recorder?.release();recorder=null;recording=false
                                val f=recordingFile
                                val thread=selected!!.first
                                if(f!=null&&f.exists()) scope.launch{
                                    uploading=true
                                    try{
                                        val url=uploadWhisperFile("Voice/${user.uid}/$thread",f,"audio/mp4")
                                        db.collection("chatThreads").document(thread).collection("messages").add(mapOf(
                                            "uid" to user.uid,"authorName" to (user.displayName ?: "عضو Whisper"),
                                            "type" to "audio","body" to "","mediaUrl" to url,
                                            "createdAt" to FieldValue.serverTimestamp())).await()
                                    }catch(e:Exception){error=e.localizedMessage ?: "تعذر إرسال التسجيل."}
                                    f.delete();recordingFile=null;uploading=false
                                }
                            }catch(e:Exception){error=e.localizedMessage ?: "تعذر إنهاء التسجيل.";recording=false}
                        }else{
                            if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)
                                startWhisperRecording(context,user.uid,selected!!.first,{r->recorder=r.first;recordingFile=r.second;recording=true},{error=it})
                            else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }){Text(if(recording)"■" else "🎙")}
                    IconButton(enabled=!busy&&!uploading&&draft.isNotBlank(),onClick={
                        val body=draft.trim();val thread=selected!!.first;busy=true
                        scope.launch{try{
                            db.collection("chatThreads").document(thread).collection("messages").add(mapOf(
                                "uid" to user.uid,"authorName" to (user.displayName ?: "عضو Whisper"),
                                "type" to "text","body" to body,"mediaUrl" to "",
                                "createdAt" to FieldValue.serverTimestamp())).await()
                            draft=""
                        }catch(e:Exception){error=e.localizedMessage ?: "تعذر إرسال الرسالة."};busy=false}
                    }){Text("➤")}
                }
                if(recording) Text("جارٍ تسجيل رسالة صوتية… اضغط ■ للإيقاف والإرسال.",color=MaterialTheme.colorScheme.primary)
                if(uploading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    }
}

private fun startWhisperRecording(
    context:android.content.Context,uid:String,threadId:String,
    onStarted:(Pair<MediaRecorder,File>)->Unit,onError:(String)->Unit
) {
    try {
        require(threadId.isNotBlank()){"افتح محادثة أولًا."}
        val file=File(context.cacheDir,"whisper-${System.currentTimeMillis()}.m4a")
        val mediaRecorder=if(Build.VERSION.SDK_INT>=31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setAudioEncodingBitRate(96000)
        mediaRecorder.setAudioSamplingRate(44100)
        mediaRecorder.setOutputFile(file.absolutePath)
        mediaRecorder.prepare();mediaRecorder.start()
        onStarted(mediaRecorder to file)
    }catch(e:Exception){onError(e.localizedMessage ?: "تعذر بدء التسجيل.")}
}

private suspend fun reloadChatMessages(
    db:FirebaseFirestore,thread:String,onLoaded:(List<WhisperChatMessage>)->Unit
){
    val docs=db.collection("chatThreads").document(thread).collection("messages")
        .orderBy("createdAt",Query.Direction.ASCENDING).limit(150).get().await().documents
    onLoaded(docs.map{d->WhisperChatMessage(d.id,d.getString("uid").orEmpty(),d.getString("authorName") ?: "عضو",
        d.getString("body").orEmpty(),d.getString("type") ?: "text",d.getString("mediaUrl").orEmpty())})
}
