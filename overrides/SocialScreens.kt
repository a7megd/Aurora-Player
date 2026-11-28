package com.aurora.player.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.content.Intent
import com.aurora.player.media.CloudinaryMediaUploader
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.zIndex
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import com.aurora.player.community.BlockedMember
import com.aurora.player.community.WhisperMessagingService
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File


private data class WhisperSocialPost(val id:String,val uid:String,val name:String,val kind:String,val body:String,val photoUrl:String = "")
private data class WhisperComment(val id:String,val uid:String,val name:String,val body:String)
private data class WhisperChatMessage(val id:String,val uid:String,val name:String,val body:String,val type:String,val mediaUrl:String)
private data class WhisperProfileSummary(val name:String,val bio:String,val favoriteSounds:String,val favoriteSoundUrl:String,val favoriteSoundName:String,val photoUrl:String,val followers:Int,val following:Int,val isFollowing:Boolean,val totalPlays:Long,val listeningMs:Long,val rankTitle:String,val achievements:List<String>,val posts:List<WhisperSocialPost>)

@Composable
fun SocialPostsScreen(user: FirebaseUser, onNavigate: (String) -> Unit = {}) {
    val db= remember { FirebaseFirestore.getInstance() }
    val scope= rememberCoroutineScope()
    val context = LocalContext.current
    var favoriteSoundUploading by remember { mutableStateOf(false) }
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
    var showComposer by remember { mutableStateOf(false) }
    var profileUid by remember { mutableStateOf<String?>(null) }
    var profileData by remember { mutableStateOf<WhisperProfileSummary?>(null) }
    var profileLoading by remember { mutableStateOf(false) }
    val favoriteSoundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && profileUid == user.uid) {
            favoriteSoundUploading = true
            scope.launch {
                try {
                    val uploaded = CloudinaryMediaUploader.upload(context, uri, "Whisper-Player/ProfileSounds")
                    val soundName = uploaded.originalName.ifBlank { "الصوت المفضل" }
                    db.collection("users").document(user.uid).update(mapOf(
                        "favoriteSoundUrl" to uploaded.secureUrl, "favoriteSoundName" to soundName
                    )).await()
                    profileData = profileData?.copy(favoriteSoundUrl = uploaded.secureUrl, favoriteSoundName = soundName)
                } catch (e: Exception) { message = e.localizedMessage ?: "تعذر رفع الصوت المفضل." }
                favoriteSoundUploading = false
            }
        }
    }

    suspend fun reload() {
        loading=true
        try {
            val postDocuments = db.collection("communityPosts").orderBy("createdAt",Query.Direction.DESCENDING).limit(80).get().await().documents
            val authorPhotoCache = postDocuments.filter { it.getString("authorPhotoUrl").isNullOrBlank() }
                .map { it.getString("uid").orEmpty() }.filter(String::isNotBlank).distinct()
                .associateWith { authorUid ->
                    runCatching { db.collection("users").document(authorUid).get().await().getString("photoUrl").orEmpty() }.getOrDefault("")
                }
            posts = postDocuments.map { d ->
                val authorUid = d.getString("uid").orEmpty()
                WhisperSocialPost(d.id,authorUid,d.getString("authorName") ?: "عضو Whisper",
                    d.getString("kind") ?: "منشور",d.getString("body").orEmpty(),
                    d.getString("authorPhotoUrl").orEmpty().ifBlank { authorPhotoCache[authorUid].orEmpty() })
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
    LaunchedEffect(profileUid) {
        val uid = profileUid ?: return@LaunchedEffect
        profileLoading = true
        profileData = null
        try {
            val doc = db.collection("users").document(uid).get().await()
            if (doc.exists()) {
                val followers = db.collection("users").document(uid).collection("followers").get().await().size()
                val following = db.collection("users").document(uid).collection("following").get().await().size()
                val isFollowing = uid != user.uid && db.collection("users").document(user.uid).collection("following").document(uid).get().await().exists()
                val authoredPosts = db.collection("communityPosts").whereEqualTo("uid", uid).limit(30).get().await().documents
                    .sortedByDescending { it.getTimestamp("createdAt")?.toDate()?.time ?: 0L }
                    .map { d -> WhisperSocialPost(d.id, uid, d.getString("authorName") ?: doc.getString("displayName") ?: "عضو Whisper",
                        d.getString("kind") ?: "منشور", d.getString("body").orEmpty(), d.getString("authorPhotoUrl").orEmpty()) }
                val achievements = ((doc.get("achievementHistory") as? List<*>) ?: (doc.get("achievements") as? List<*>))
                    ?.filterIsInstance<String>().orEmpty()
                profileData = WhisperProfileSummary(
                    name = doc.getString("displayName") ?: authoredPosts.firstOrNull()?.name ?: "عضو Whisper",
                    bio = doc.getString("bio").orEmpty(),
                    favoriteSounds = doc.getString("favoriteSounds").orEmpty(),
                    favoriteSoundUrl = doc.getString("favoriteSoundUrl").orEmpty(),
                    favoriteSoundName = doc.getString("favoriteSoundName").orEmpty(),
                    photoUrl = doc.getString("photoUrl").orEmpty().ifBlank { authoredPosts.firstOrNull()?.photoUrl.orEmpty() }.ifBlank { if (uid == user.uid) user.photoUrl?.toString().orEmpty() else "" },
                    followers = followers,
                    following = following,
                    isFollowing = isFollowing,
                    totalPlays = doc.getLong("totalPlays") ?: 0L,
                    listeningMs = doc.getLong("totalListeningMs") ?: 0L,
                    rankTitle = doc.getString("rankTitle") ?: "عضو المجتمع",
                    achievements = achievements,
                    posts = authoredPosts
                )
            }
        } catch (e: Exception) {
            message = e.localizedMessage ?: "تعذر تحميل الملف الشخصي."
        }
        profileLoading = false
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
      Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=14.dp,vertical=12.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("مساحة Whisper",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
            Text("قصائد واقتباسات وأفكار من مجتمع الكتّاب",color=MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(42.dp).clip(CircleShape).clickable { profileUid = post.uid }, contentAlignment=Alignment.Center) {
                                    if (post.photoUrl.isNotBlank()) AsyncImage(model=post.photoUrl,contentDescription="فتح الملف الشخصي",
                                        modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                                    else Text(post.name.firstOrNull()?.toString() ?: "؟",style=MaterialTheme.typography.titleMedium)
                                }
                                TextButton(onClick={ profileUid = post.uid }) { Text(post.name,fontWeight=FontWeight.SemiBold) }
                            }
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
                                    TextButton(onClick={ if (c.uid.isNotBlank()) profileUid = c.uid },contentPadding=PaddingValues(0.dp)) { Text(c.name,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold) }
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
        FloatingActionButton(
            onClick = { showComposer = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).zIndex(2f),
            containerColor = MaterialTheme.colorScheme.primary
        ) { Icon(Icons.Rounded.Add, contentDescription = "إضافة منشور أو مقال") }
      }
      BackHandler(enabled = profileUid != null) { profileUid = null; profileData = null }
      if (profileUid != null) {
        val profile = profileData
        Surface(
          modifier = Modifier.fillMaxSize().zIndex(5f),
          color = MaterialTheme.colorScheme.background
        ) {
          Column(Modifier.fillMaxSize()) {
            Row(
              Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              TextButton(onClick = { profileUid = null; profileData = null }) { Text("‹ رجوع") }
              Text(profile?.name ?: "الملف الشخصي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
            Column(
              Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              if (profileLoading) {
                CircularProgressIndicator()
                Text("جارٍ تحميل الملف الشخصي…")
              } else if (profile == null) {
                Text("تعذر العثور على بيانات هذا المستخدم.")
              } else {
                if (profile.photoUrl.isNotBlank()) {
                  AsyncImage(model=profile.photoUrl,contentDescription="الصورة الشخصية",
                    modifier=Modifier.size(112.dp).clip(CircleShape),contentScale=ContentScale.Crop)
                } else {
                  Surface(modifier=Modifier.size(112.dp),shape=CircleShape,color=MaterialTheme.colorScheme.secondaryContainer) {
                    Box(contentAlignment=Alignment.Center) {
                      Text(profile.name.firstOrNull()?.toString() ?: "؟",style=MaterialTheme.typography.displayMedium)
                    }
                  }
                }
                Text(profile.name,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Text(profile.bio.ifBlank { "لا توجد نبذة شخصية بعد." },color=MaterialTheme.colorScheme.onSurfaceVariant)
                if (profileUid != user.uid) {
                  OutlinedButton(enabled = !busy, onClick = {
                    val targetUid = profileUid ?: return@OutlinedButton
                    busy = true
                    scope.launch {
                      try {
                        val followingRef = db.collection("users").document(user.uid).collection("following").document(targetUid)
                        val followerRef = db.collection("users").document(targetUid).collection("followers").document(user.uid)
                        val batch = db.batch()
                        if (profile.isFollowing) { batch.delete(followingRef); batch.delete(followerRef) }
                        else {
                          batch.set(followingRef, mapOf("targetUid" to targetUid, "createdAt" to FieldValue.serverTimestamp()))
                          batch.set(followerRef, mapOf("uid" to user.uid, "createdAt" to FieldValue.serverTimestamp()))
                        }
                        batch.commit().await()
                        profileData = profile.copy(
                          isFollowing = !profile.isFollowing,
                          followers = (profile.followers + if (profile.isFollowing) -1 else 1).coerceAtLeast(0)
                        )
                      } catch (e: Exception) { message = e.localizedMessage ?: "تعذر تحديث المتابعة." }
                      busy = false
                    }
                  }) { Text(if (profile.isFollowing) "إلغاء المتابعة" else "متابعة") }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                  Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(profile.followers.toString(),fontWeight=FontWeight.Bold)
                    Text("المتابعون")
                  }
                  Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(profile.following.toString(),fontWeight=FontWeight.Bold)
                    Text("يتابع")
                  }
                  Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(profile.posts.size.toString(),fontWeight=FontWeight.Bold)
                    Text("المنشورات")
                  }
                }
                Card(Modifier.fillMaxWidth()) {
                  Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("عن العضو",fontWeight=FontWeight.Bold)
                    Text("الرتبة: ${profile.rankTitle}")
                    Text("التشغيلات: ${profile.totalPlays}  •  وقت الاستماع: ${profile.listeningMs / 3_600_000L} ساعة")
                    if (profile.favoriteSounds.isNotBlank()) Text("الأصوات المفضلة: ${profile.favoriteSounds}")
                    Text("الصوت المفضل",fontWeight=FontWeight.SemiBold)
                    if (profile.favoriteSoundUrl.isNotBlank()) {
                      Text(profile.favoriteSoundName.ifBlank { "تسجيل صوتي مفضل" })
                      WhisperAudioBubble(profile.favoriteSoundUrl)
                    } else Text("لم يرفع هذا العضو صوتًا مفضلًا بعد.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if (profileUid == user.uid) {
                      OutlinedButton(enabled = !favoriteSoundUploading, onClick = { favoriteSoundPicker.launch(arrayOf("audio/*")) }) {
                        Text(if (favoriteSoundUploading) "جارٍ رفع الصوت…" else "رفع / تغيير الصوت المفضل")
                      }
                    }
                  }
                }
                Card(Modifier.fillMaxWidth()) {
                  Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("الإنجازات السابقة",fontWeight=FontWeight.Bold)
                    if (profile.achievements.isEmpty()) Text("لا توجد إنجازات مسجلة على الحساب حتى الآن.")
                    else profile.achievements.forEach { Text("• $it") }
                  }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                  Text("منشورات العضو",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                  Text(profile.posts.size.toString(),color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (profile.posts.isEmpty()) {
                  Text("لا توجد مشاركات منشورة حتى الآن.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                } else profile.posts.forEach { post ->
                  Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                      AssistChip(onClick={},label={Text(post.kind)})
                      Text(post.body,style=MaterialTheme.typography.bodyLarge)
                    }
                  }
                }
              }
            }
          }
        }
      }
      if (showComposer) AlertDialog(
        onDismissRequest = { showComposer = false },
        title = { Text("إضافة محتوى جديد") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("اختر نوع المشاركة")
            TextButton(onClick = { showComposer = false; onNavigate(Routes.WRITE_ARTICLE) }) { Text("أو ابدأ كتابة مقال طويل") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              listOf("قصيدة","اقتباس","منشور").forEach { v ->
                FilterChip(selected = kind == v, onClick = { kind = v }, label = { Text(v) })
              }
            }
            OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth(), minLines = 4, label = { Text("اكتب محتواك") })
          }
        },
        confirmButton = {
          TextButton(enabled = !busy && draft.isNotBlank(), onClick = {
            busy = true
            scope.launch {
              try {
                db.collection("communityPosts").add(mapOf("uid" to user.uid, "authorName" to (user.displayName ?: "عضو Whisper"), "authorPhotoUrl" to (user.photoUrl?.toString() ?: ""), "kind" to kind, "body" to draft.trim(), "createdAt" to FieldValue.serverTimestamp())).await()
                draft = ""; showComposer = false; reload()
              } catch (e: Exception) { message = e.localizedMessage ?: "تعذر نشر المشاركة." }
              busy = false
            }
          }) { Text(if (busy) "جارٍ النشر…" else "نشر") }
        },
        dismissButton = { TextButton(onClick = { showComposer = false }) { Text("إلغاء") } }
      )
    }
}

@Composable
private fun WhisperAudioBubble(url:String) {
    var playing by remember(url){mutableStateOf(false)}
    var preparing by remember(url){mutableStateOf(false)}
    var player by remember(url){mutableStateOf<MediaPlayer?>(null)}
    DisposableEffect(url) {
        onDispose { runCatching { player?.release() }; player=null }
    }
    OutlinedButton(enabled=!preparing,onClick={
        if(playing) {
            runCatching { player?.pause() }
            playing=false
        } else {
            runCatching { player?.release() }
            try {
                preparing=true
                player=MediaPlayer().apply {
                    setDataSource(url)
                    setOnPreparedListener { it.start(); preparing=false; playing=true }
                    setOnCompletionListener { playing=false; runCatching { it.seekTo(0) } }
                    setOnErrorListener { _, _, _ -> preparing=false; playing=false; true }
                    prepareAsync()
                }
            } catch(e:Exception) {
                preparing=false
                playing=false
            }
        }
    }) { Text(when { preparing -> "جارٍ تحميل الصوت…"; playing -> "إيقاف الصوت"; else -> "▶ تشغيل رسالة صوتية" }) }
}

@OptIn(ExperimentalFoundationApi::class)
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
    var inboxFilter by remember { mutableStateOf("الكل") }
    val inboxPrefs = remember(user.uid) { context.getSharedPreferences("whisper_chat_inbox_${user.uid}", 0) }
    var archivedThreadIds by remember(user.uid) {
        mutableStateOf(inboxPrefs.getStringSet("archived", emptySet())?.toSet() ?: emptySet())
    }
    var unreadThreadIds by remember(user.uid) { mutableStateOf<Set<String>>(emptySet()) }
    var messages by remember { mutableStateOf<List<WhisperChatMessage>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reactionTarget by remember { mutableStateOf<WhisperChatMessage?>(null) }
    var reactionsByMessage by remember { mutableStateOf<Map<String, Map<String, Int>>>(emptyMap()) }
    var blockedUsers by remember { mutableStateOf<List<BlockedMember>>(emptyList()) }
    var showBlockedDialog by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var blockedByMe by remember { mutableStateOf(false) }
    var blockedEitherWay by remember { mutableStateOf(false) }
    var attachmentUri by remember { mutableStateOf<Uri?>(null) }
    var attachmentName by remember { mutableStateOf("") }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recording by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var pendingMediaUrl by remember { mutableStateOf("") }
    var pendingMediaType by remember { mutableStateOf("") }
    var pendingMediaName by remember { mutableStateOf("") }
    var pendingMediaMime by remember { mutableStateOf("") }
    var pendingMediaAssetId by remember { mutableStateOf("") }
    fun startVoiceRecording() {
        try {
            val file = File(context.cacheDir, "whisper-chat-" + System.currentTimeMillis() + ".m4a")
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
        } catch (e: Exception) { error = e.localizedMessage ?: "تعذر بدء التسجيل." }
    }
    fun stopVoiceRecording() {
        val active = recorder
        val file = recordingFile
        try {
            active?.stop()
            if (file != null && file.exists() && file.length() > 0L) {
                attachmentUri = Uri.fromFile(file)
                attachmentName = "تسجيل صوتي.m4a"
                pendingMediaUrl = ""; pendingMediaType = ""; pendingMediaName = ""; pendingMediaMime = ""; pendingMediaAssetId = ""
            } else error = "التسجيل فارغ؛ حاول مرة أخرى."
        } catch (e: Exception) { error = e.localizedMessage ?: "تعذر إنهاء التسجيل." }
        runCatching { active?.release() }
        recorder = null
        recordingFile = null
        recording = false
    }
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            attachmentUri = uri
            attachmentName = uri.lastPathSegment?.substringAfterLast('/') ?: "ملف مرفق"
            pendingMediaUrl = ""; pendingMediaType = ""; pendingMediaName = ""; pendingMediaMime = ""; pendingMediaAssetId = ""
        }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceRecording() else error = "اسمح باستخدام الميكروفون لتسجيل رسالة صوتية."
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            recordingFile?.delete()
        }
    }
    val quickReactions = listOf("❤️", "😂", "😮", "😢", "👍", "🙏")
    suspend fun reloadThreads() {
        try {
            val rows=FirebaseCommunity.approvedChats(user.uid)
            val visibleRows = rows.filter { row ->
                val otherUid = row["otherUid"].orEmpty()
                otherUid.isNotBlank() && !FirebaseCommunity.isBlocked(user.uid, otherUid)
            }
            val profiles=visibleRows.map{it["otherUid"].orEmpty()}.distinct().associateWith{uid->
                db.collection("users").document(uid).get().await().getString("displayName") ?: "صديق"
            }
            val loaded = visibleRows.map{it["threadId"].orEmpty() to profiles[it["otherUid"].orEmpty()].orEmpty()}
            threads = loaded
            val unread = mutableSetOf<String>()
            loaded.forEach { (threadId, _) ->
                val latest = db.collection("chatThreads").document(threadId).collection("messages")
                    .orderBy("createdAt", Query.Direction.DESCENDING).limit(1).get().await().documents.firstOrNull()
                val latestAt = latest?.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                val lastReadAt = inboxPrefs.getLong("read_$threadId", 0L)
                if (latest != null && latest.getString("uid") != user.uid && latestAt > lastReadAt) unread.add(threadId)
            }
            unreadThreadIds = unread
        }catch(e:Exception){
            val detail = e.localizedMessage.orEmpty()
            error = if (detail.contains("PERMISSION_DENIED", true) || detail.contains("permission", true))
                "رفضت قاعدة بيانات Firebase الوصول للمحادثات. يلزم نشر قواعد Firestore المحدثة من ملف firestore.rules في مشروع Firebase."
            else detail.ifBlank { "تعذر تحميل المحادثات." }
        }
    }
    suspend fun reloadBlockedUsers() {
        try { blockedUsers = FirebaseCommunity.blockedUsers(user.uid) }
        catch (e: Exception) { error = e.localizedMessage ?: "تعذر تحميل قائمة المحظورين." }
    }
    LaunchedEffect(user.uid){ reloadThreads(); reloadBlockedUsers() }
    LaunchedEffect(selected?.first) {
        val otherUid = selected?.first?.split("_")?.firstOrNull { it != user.uid }.orEmpty()
        if (otherUid.isNotBlank()) {
            runCatching {
                blockedByMe = FirebaseCommunity.isBlockedByMe(user.uid, otherUid)
                blockedEitherWay = FirebaseCommunity.isBlocked(user.uid, otherUid)
            }.onFailure { error = it.localizedMessage ?: "تعذر التحقق من حالة الحظر." }
        } else { blockedByMe = false; blockedEitherWay = false }
    }
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
    

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                if(selected!=null) TextButton(onClick={selected=null}){Text("المحادثات")}
                Text(if(selected==null)"محادثات الأصدقاء" else selected!!.second,
                    style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold, modifier=Modifier.weight(1f))
                TextButton(onClick={showBlockedDialog=true}) { Text("المحظورون") }
                if (selected != null) {
                    val otherUid = selected!!.first.split("_").firstOrNull { it != user.uid }.orEmpty()
                    TextButton(enabled=otherUid.isNotBlank() && !blockedByMe, onClick={showBlockConfirm=true}) {
                        Text(if(blockedByMe) "محظور" else "حظر", color=MaterialTheme.colorScheme.error)
                    }
                }
                IconButton(onClick={scope.launch{if(selected==null){reloadThreads();reloadBlockedUsers()} else reloadChatMessages(db,selected!!.first){messages=it}}}){Text("↻")}
            }
            if(selected==null) {
                Text("تظهر المحادثات بعد موافقة الإدارة على طلب التواصل.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("الكل", "غير مقروء", "مؤرشف").forEach { tab ->
                        FilterChip(selected = inboxFilter == tab, onClick = { inboxFilter = tab }, label = { Text(tab) })
                    }
                }
                val filteredThreads = threads.filter { row ->
                    when (inboxFilter) {
                        "غير مقروء" -> row.first in unreadThreadIds && row.first !in archivedThreadIds
                        "مؤرشف" -> row.first in archivedThreadIds
                        else -> row.first !in archivedThreadIds
                    }
                }
                filteredThreads.forEach { t ->
                    Card(onClick = {
                        selected = t
                        inboxPrefs.edit().putLong("read_${t.first}", System.currentTimeMillis()).apply()
                        unreadThreadIds = unreadThreadIds - t.first
                    }) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.second, fontWeight=FontWeight.SemiBold)
                                Text(if (t.first in unreadThreadIds) "رسائل غير مقروءة" else "اضغط لفتح المحادثة",
                                    style=MaterialTheme.typography.bodySmall,
                                    color=if (t.first in unreadThreadIds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = {
                                val next = archivedThreadIds.toMutableSet()
                                if (t.first in next) next.remove(t.first) else next.add(t.first)
                                archivedThreadIds = next
                                inboxPrefs.edit().putStringSet("archived", next).apply()
                            }) { Text(if (t.first in archivedThreadIds) "إلغاء الأرشفة" else "أرشفة") }
                        }
                    }
                }
                if(filteredThreads.isEmpty()) Text(when(inboxFilter) {
                    "غير مقروء" -> "لا توجد رسائل غير مقروءة."
                    "مؤرشف" -> "لا توجد محادثات مؤرشفة."
                    else -> "لا توجد محادثات معتمدة حتى الآن."
                })
            }else if (blockedEitherWay) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment=Alignment.Center) {
                    Text(if (blockedByMe) "لقد حظرت هذا المستخدم. فك الحظر من قائمة المحظورين لاستعادة المحادثة." else "لا يمكن استخدام هذه المحادثة لأن أحد الطرفين حظر الآخر.", color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }else{
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    messages.forEach{msg->
                        val reactionCollection = db.collection("chatThreads").document(selected!!.first)
                            .collection("messages").document(msg.id).collection("reactions")
                        DisposableEffect(selected?.first, msg.id) {
                            val registration = reactionCollection.addSnapshotListener { snapshot, exception ->
                                if (exception == null) {
                                    val counts = snapshot?.documents.orEmpty()
                                        .mapNotNull { it.getString("emoji") }
                                        .groupingBy { it }.eachCount()
                                    reactionsByMessage = reactionsByMessage + (msg.id to counts)
                                }
                            }
                            onDispose { registration.remove() }
                        }
                        Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            Box {
                                Card(
                                    modifier=Modifier.combinedClickable(
                                        onClick = { },
                                        onLongClick = { reactionTarget = msg }
                                    ),
                                    colors=CardDefaults.cardColors(containerColor=if(msg.uid==user.uid)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                ){
                                    Column(Modifier.fillMaxWidth().padding(10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                                        Text(if(msg.uid==user.uid)"أنت" else msg.name,style=MaterialTheme.typography.labelSmall)
                                        if(msg.type=="image"&&msg.mediaUrl.isNotBlank()) AsyncImage(model=msg.mediaUrl,contentDescription="صورة مرسلة",modifier=Modifier.fillMaxWidth().heightIn(max=280.dp))
                                        else if(msg.type=="audio"&&msg.mediaUrl.isNotBlank()) WhisperAudioBubble(msg.mediaUrl)
                                        else if(msg.type=="file"&&msg.mediaUrl.isNotBlank()) {
                                            Text(msg.body.ifBlank { "ملف مرفق" })
                                            TextButton(onClick={runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(msg.mediaUrl))) }.onFailure { error=it.localizedMessage ?: "تعذر فتح الملف." }}) { Text("فتح / تنزيل الملف") }
                                        }
                                        else if(msg.body.isNotBlank()) Text(msg.body)
                                        Text("اضغط مطولًا للتفاعل",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                DropdownMenu(
                                    expanded = reactionTarget?.id == msg.id,
                                    onDismissRequest = { reactionTarget = null }
                                ) {
                                    Row(Modifier.padding(horizontal=8.dp), horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                                        quickReactions.forEach { emoji ->
                                            TextButton(onClick = {
                                                scope.launch {
                                                    try {
                                                        reactionCollection.document(user.uid).set(mapOf(
                                                            "uid" to user.uid,
                                                            "emoji" to emoji,
                                                            "updatedAt" to FieldValue.serverTimestamp()
                                                        )).await()
                                                    } catch(e:Exception) { error = e.localizedMessage ?: "تعذر حفظ التفاعل." }
                                                }
                                                reactionTarget = null
                                            }) { Text(emoji, style=MaterialTheme.typography.titleLarge) }
                                        }
                                    }
                                }
                            }
                            val counts = reactionsByMessage[msg.id].orEmpty()
                            if(counts.isNotEmpty()) {
                                Row(horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.CenterVertically) {
                                    counts.forEach { (emoji, count) ->
                                        Surface(shape=MaterialTheme.shapes.large, color=MaterialTheme.colorScheme.surfaceVariant) {
                                            Text("$emoji $count", modifier=Modifier.padding(horizontal=8.dp, vertical=3.dp), style=MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (attachmentUri != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                        Text("مرفق: $attachmentName", Modifier.weight(1f), style=MaterialTheme.typography.bodySmall)
                        TextButton(onClick={attachmentUri=null;attachmentName="";pendingMediaUrl="";pendingMediaType="";pendingMediaName="";pendingMediaMime="";pendingMediaAssetId=""},enabled=!busy){Text("إزالة")}
                    }
                    if (busy) LinearProgressIndicator(progress={uploadProgress},modifier=Modifier.fillMaxWidth())
                }
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
                    OutlinedTextField(value=draft,onValueChange={draft=it},modifier=Modifier.weight(1f),
                        placeholder={Text("اكتب رسالة…")},maxLines=4)
                    IconButton(enabled=!busy&&(draft.isNotBlank()||attachmentUri!=null),onClick={
                        val body=draft.trim();val thread=selected!!.first;val selectedUri=attachmentUri;busy=true
                        scope.launch{try{
                            if(selectedUri==null) {
                                db.collection("chatThreads").document(thread).collection("messages").add(mapOf(
                                    "uid" to user.uid,"authorName" to (user.displayName ?: "عضو Whisper"),
                                    "type" to "text","body" to body,"mediaUrl" to "",
                                    "createdAt" to FieldValue.serverTimestamp())).await()
                            } else {
                                if (pendingMediaUrl.isBlank()) {
                                    val uploaded=CloudinaryMediaUploader.upload(context,selectedUri,"Whisper-Player/Chat") { sent,total ->
                                        uploadProgress=(sent.toFloat()/total.coerceAtLeast(1L)).coerceIn(0f,1f)
                                    }
                                    pendingMediaUrl=uploaded.secureUrl
                                    pendingMediaType=when {
                                        uploaded.mimeType.startsWith("image/")->"image"
                                        uploaded.mimeType.startsWith("audio/")->"audio"
                                        else->"file"
                                    }
                                    pendingMediaName=uploaded.originalName
                                    pendingMediaMime=uploaded.mimeType
                                    pendingMediaAssetId=uploaded.assetId
                                }
                                db.collection("chatThreads").document(thread).collection("messages").add(mapOf(
                                    "uid" to user.uid,"authorName" to (user.displayName ?: "عضو Whisper"),
                                    "type" to pendingMediaType,"body" to pendingMediaName,"mediaUrl" to pendingMediaUrl,
                                    "mimeType" to pendingMediaMime,"assetId" to pendingMediaAssetId,
                                    "createdAt" to FieldValue.serverTimestamp())).await()
                                if (selectedUri.scheme == "file") runCatching { selectedUri.path?.let { File(it).delete() } }
                                attachmentUri=null;attachmentName=""
                                pendingMediaUrl="";pendingMediaType="";pendingMediaName="";pendingMediaMime="";pendingMediaAssetId=""
                            }
                            draft="";uploadProgress=0f
                        }catch(e:Exception){error=e.localizedMessage ?: "تعذر إرسال الرسالة."};busy=false}
                    }){Text("➤")}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    OutlinedButton(enabled=!busy&&!recording,onClick={attachmentPicker.launch(arrayOf("*/*"))}) { Text("إرفاق ملف") }
                    OutlinedButton(enabled=!busy&&!recording,onClick={
                        if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) startVoiceRecording()
                        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text("تسجيل صوت") }
                    if(recording) Button(onClick={stopVoiceRecording()}) { Text("إيقاف التسجيل") }
                }
            }
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    }
    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest={showBlockConfirm=false},
            title={Text("حظر هذا المستخدم؟")},
            text={Text("لن تتمكن أنت وهذا المستخدم من متابعة المحادثة أو إرسال الرسائل لبعضكما. يمكنك فك الحظر لاحقًا من قائمة المحظورين.")},
            confirmButton={
                TextButton(onClick={
                    showBlockConfirm=false
                    val otherUid=selected?.first?.split("_")?.firstOrNull { it != user.uid }.orEmpty()
                    scope.launch {
                        try {
                            FirebaseCommunity.blockUser(user.uid, otherUid)
                            selected=null
                            blockedByMe=true
                            blockedEitherWay=true
                            reloadBlockedUsers()
                            reloadThreads()
                            error="تم حظر المستخدم ومنع التواصل معه."
                        } catch(e:Exception) { error=e.localizedMessage ?: "تعذر حظر المستخدم." }
                    }
                }) { Text("حظر", color=MaterialTheme.colorScheme.error) }
            },
            dismissButton={TextButton(onClick={showBlockConfirm=false}){Text("إلغاء")}}
        )
    }
    if (showBlockedDialog) {
        AlertDialog(
            onDismissRequest={showBlockedDialog=false},
            title={Text("المستخدمون المحظورون")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if (blockedUsers.isEmpty()) Text("لا يوجد مستخدمون محظورون.")
                    blockedUsers.forEach { blocked ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                            Text(blocked.displayName, Modifier.weight(1f))
                            TextButton(onClick={
                                scope.launch {
                                    try {
                                        FirebaseCommunity.unblockUser(user.uid, blocked.uid)
                                        reloadBlockedUsers()
                                        reloadThreads()
                                        val activeOther = selected?.first?.split("_")?.firstOrNull { it != user.uid }.orEmpty()
                                        if (activeOther == blocked.uid) {
                                            blockedByMe=false
                                            blockedEitherWay=FirebaseCommunity.isBlocked(user.uid, activeOther)
                                        }
                                    } catch(e:Exception) { error=e.localizedMessage ?: "تعذر فك الحظر." }
                                }
                            }) { Text("فك الحظر") }
                        }
                    }
                }
            },
            confirmButton={TextButton(onClick={showBlockedDialog=false}){Text("إغلاق")}}
        )
    }
}

private suspend fun reloadChatMessages(
    db:FirebaseFirestore,thread:String,onLoaded:(List<WhisperChatMessage>)->Unit
){
    val docs=db.collection("chatThreads").document(thread).collection("messages")
        .orderBy("createdAt",Query.Direction.ASCENDING).limit(150).get().await().documents
    onLoaded(docs.map{d->WhisperChatMessage(d.id,d.getString("uid").orEmpty(),d.getString("authorName") ?: "عضو",
        d.getString("body").orEmpty(),d.getString("type") ?: "text",d.getString("mediaUrl").orEmpty())})
}
