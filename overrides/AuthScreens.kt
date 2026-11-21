package com.aurora.player.ui.navigation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aurora.player.community.FirebaseCommunity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AuthScreen(onAuthenticated: (FirebaseUser) -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    val scope = rememberCoroutineScope()
    var signup by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(auth.currentUser?.isEmailVerified == false) }
    var name by remember { mutableStateOf(auth.currentUser?.displayName.orEmpty()) }
    var email by remember { mutableStateOf(auth.currentUser?.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(if (verifying) "افتح رسالة التحقق في بريدك ثم ارجع إلى هنا." else "") }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(Modifier.fillMaxSize().background(Color(0xFF090A0F)).padding(22.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth().widthIn(max = 460.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF202436), modifier = Modifier.size(72.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = Color(0xFFFFD9A8), modifier = Modifier.size(40.dp)) }
                }
                Spacer(Modifier.height(14.dp))
                Text("Whisper Player", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("مساحتك الخاصة للاستماع والمشاركة", color = Color(0xFFB9BBC8))
                Spacer(Modifier.height(24.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF171923)), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (verifying) "تأكيد البريد الإلكتروني" else if (signup) "إنشاء حساب جديد" else "تسجيل الدخول", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        if (verifying) {
                            Text("لو كتبت بريدك الإلكتروني غلط، صححه هنا وسنرسل رابط التحقق إلى العنوان الجديد.", color = Color(0xFFD0D2DC))
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it.trim() },
                                label = { Text("البريد الإلكتروني الصحيح") },
                                leadingIcon = { Icon(Icons.Rounded.Mail, null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(onClick = { scope.launch {
                                busy = true
                                message = ""
                                try {
                                    require(email.contains("@") && email.substringAfter("@").contains(".")) {
                                        "اكتب بريدًا إلكترونيًا صحيحًا."
                                    }
                                    val current = auth.currentUser ?: error("انتهت جلسة الحساب. سجّل الدخول مرة أخرى.")
                                    if (!current.email.equals(email.trim(), ignoreCase = true)) {
                                        current.updateEmail(email.trim()).await()
                                    }
                                    current.sendEmailVerification().await()
                                    message = "تم اعتماد البريد الجديد وإرسال رابط تحقق إليه. راجع صندوق الوارد والرسائل غير المرغوب فيها."
                                } catch (e: Exception) {
                                    message = e.localizedMessage ?: "تعذر تغيير البريد. حاول تسجيل الدخول مرة أخرى ثم أعد المحاولة."
                                }
                                busy = false
                            } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                else Text("تغيير البريد وإرسال رابط التحقق")
                            }
                            Text("البريد الحالي للحساب: ${auth.currentUser?.email.orEmpty()}", color = Color(0xFFB9BBC8), style = MaterialTheme.typography.bodySmall)
                            Text("بعد فتح رابط التحقق، ارجع واضغط «تحققت من البريد الإلكتروني».", color = Color(0xFFD0D2DC))
                            Button(onClick = { scope.launch {
                                busy = true
                                try {
                                    auth.currentUser?.reload()?.await()
                                    val u = auth.currentUser
                                    if (u?.isEmailVerified == true) {
                                        u.getIdToken(true).await()
                                        FirebaseCommunity.ensureProfile(u, name)
                                        onAuthenticated(u)
                                    } else message = "لم يتم تأكيد البريد بعد."
                                } catch (e: Exception) { message = e.localizedMessage ?: "تعذر التحقق." }
                                busy = false
                            } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("تحققت من البريد الإلكتروني") }
                            OutlinedButton(onClick = { scope.launch {
                                busy = true
                                try { auth.currentUser?.sendEmailVerification()?.await(); message = "أعدنا إرسال رسالة التحقق." }
                                catch (e: Exception) { message = e.localizedMessage ?: "تعذر إرسال الرسالة." }
                                busy = false
                            } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("إعادة إرسال رسالة التحقق") }
                        } else {
                            if (signup) OutlinedTextField(name, { name = it.take(30) }, label = { Text("اسم المستخدم") }, leadingIcon = { Icon(Icons.Rounded.Person, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(email, { email = it.trim() }, label = { Text("البريد الإلكتروني") }, leadingIcon = { Icon(Icons.Rounded.Mail, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(password, { password = it }, label = { Text("كلمة المرور") }, leadingIcon = { Icon(Icons.Rounded.Lock, null) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
                            if (signup) OutlinedTextField(confirm, { confirm = it }, label = { Text("تأكيد كلمة المرور") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                            Button(onClick = { scope.launch {
                                busy = true; message = ""
                                try {
                                    require(email.contains("@")) { "اكتب بريدًا إلكترونيًا صحيحًا." }
                                    require(password.length >= 6) { "كلمة المرور يجب ألا تقل عن 6 أحرف." }
                                    if (signup) {
                                        require(name.trim().length >= 2) { "اكتب اسمًا من حرفين على الأقل." }
                                        require(password == confirm) { "كلمتا المرور غير متطابقتين." }
                                        val u = auth.createUserWithEmailAndPassword(email, password).await().user ?: error("تعذر إنشاء الحساب.")
                                        u.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
                                        u.sendEmailVerification().await()
                                        verifying = true
                                        message = "تم إنشاء الحساب. أرسلنا رابط التحقق إلى بريدك."
                                    } else {
                                        val u = auth.signInWithEmailAndPassword(email, password).await().user ?: error("تعذر تسجيل الدخول.")
                                        u.reload().await()
                                        val current = auth.currentUser ?: u
                                        if (!current.isEmailVerified) {
                                            current.sendEmailVerification().await()
                                            verifying = true
                                            message = "حسابك غير مؤكّد. أرسلنا رابط تحقق جديدًا."
                                        } else {
                                            current.getIdToken(true).await()
                                            FirebaseCommunity.ensureProfile(current)
                                            onAuthenticated(current)
                                        }
                                    }
                                } catch (e: Exception) { message = e.localizedMessage ?: "حدث خطأ، حاول مرة أخرى." }
                                busy = false
                            } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                else Text(if (signup) "إنشاء الحساب" else "دخول")
                            }
                            TextButton(onClick = { signup = !signup; message = "" }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                                Text(if (signup) "لديك حساب؟ تسجيل الدخول" else "ليس لديك حساب؟ أنشئ حسابًا")
                            }
                            if (!signup) TextButton(onClick = { scope.launch {
                                if (email.isBlank()) message = "اكتب بريدك الإلكتروني أولًا."
                                else try { auth.sendPasswordResetEmail(email).await(); message = "أرسلنا رابط استعادة كلمة المرور." }
                                catch (e: Exception) { message = e.localizedMessage ?: "تعذر إرسال الرابط." }
                            } }, modifier = Modifier.fillMaxWidth()) { Text("نسيت كلمة المرور؟") }
                        }
                        if (message.isNotBlank()) Text(message, color = Color(0xFFFFD4B5), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("ملفاتك الصوتية المحلية وبريدك الإلكتروني لا يظهران لأعضاء المجتمع.", color = Color(0xFF999CAB), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun profilePhotoModel(photoData: String, photoUrl: String): Any? {
    if (photoData.isNotBlank()) {
        val bitmap = runCatching {
            val bytes = Base64.decode(photoData, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
        if (bitmap != null) return bitmap
    }
    return photoUrl.takeIf { it.startsWith("https://") }
}

@Composable
fun CommunityScreen(user: FirebaseUser, onSignOut: () -> Unit, onNavigate: (String) -> Unit) {
 val scope=rememberCoroutineScope()
 var members by remember { mutableStateOf<List<com.aurora.player.community.CommunityMember>>(emptyList()) }
 var following by remember { mutableStateOf<Set<String>>(emptySet()) }
 var requested by remember { mutableStateOf<Set<String>>(emptySet()) }
 var ownFollowers by remember { mutableIntStateOf(0) }
 var ownFollowing by remember { mutableIntStateOf(0) }
 var ownFriends by remember { mutableIntStateOf(0) }
 var selectedIsFriend by remember { mutableStateOf(false) }
 var selectedFollowers by remember { mutableIntStateOf(0) }
 var selectedFollowing by remember { mutableIntStateOf(0) }
 var selectedFriends by remember { mutableIntStateOf(0) }
 var loading by remember { mutableStateOf(true) }
 var busyUid by remember { mutableStateOf<String?>(null) }
 var errorText by remember { mutableStateOf<String?>(null) }
 var editing by remember { mutableStateOf(false) }
 var saving by remember { mutableStateOf(false) }
 var displayName by remember { mutableStateOf(user.displayName.orEmpty()) }
 var photoUrl by remember { mutableStateOf("") }
 var photoData by remember { mutableStateOf("") }
 val context = LocalContext.current
 val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
  if (uri != null) try {
   val source = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: error("تعذر قراءة الصورة.")
   val scale = minOf(1f, 320f / maxOf(source.width, source.height))
   val resized = Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true)
   val bytes = ByteArrayOutputStream().use { out -> resized.compress(Bitmap.CompressFormat.JPEG, 68, out); out.toByteArray() }
   photoData = Base64.encodeToString(bytes, Base64.NO_WRAP)
   photoUrl = ""
   if (resized !== source) resized.recycle()
   if (source !== resized) source.recycle()
  } catch (e: Exception) { errorText = e.localizedMessage ?: "تعذر تجهيز الصورة." }
 }
 var bio by remember { mutableStateOf("") }
 var favorites by remember { mutableStateOf("") }
 var selected by remember { mutableStateOf<com.aurora.player.community.CommunityMember?>(null) }
 var showAdminPanel by remember { mutableStateOf(false) }
 val adminBuild=LocalContext.current.packageName.endsWith(".test") && FirebaseCommunity.isAdmin(user.uid)
 fun refresh(){ scope.launch {
  loading=true; errorText=null
  try {
   FirebaseCommunity.ensureProfile(user)
   val all=FirebaseCommunity.communityMembers()
   members=all.filter{it.uid!=user.uid}
   following=FirebaseCommunity.followingIds(user.uid).intersect(members.map{it.uid}.toSet())
   requested=FirebaseCommunity.outgoingFriendRequests(user.uid).filter{it.status=="pending" || it.status=="accepted"}.map{it.uid}.toSet()+FirebaseCommunity.friendIds(user.uid)
   ownFollowers=FirebaseCommunity.followerCount(user.uid); ownFollowing=FirebaseCommunity.followingCount(user.uid); ownFriends=FirebaseCommunity.friendCount(user.uid)
   all.firstOrNull{it.uid==user.uid}?.let{displayName=it.displayName;photoUrl=it.photoUrl;photoData=it.photoData;bio=it.bio;favorites=it.favoriteSounds}
  } catch(e:Exception){errorText=(e.localizedMessage ?: "تعذر تحميل المستخدمين.")+"\nتأكد من نشر قواعد Firestore المحدّثة ومن تسجيل الدخول بحساب موثّق."}
  loading=false
 } }
 LaunchedEffect(user.uid){refresh()}
 CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
    Column{Text("مجتمع Whisper",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("تعرّف على الكتّاب والأعضاء وتابع ما يعجبك",color=MaterialTheme.colorScheme.onSurfaceVariant)}
    TextButton(onClick=onSignOut){Text("تسجيل الخروج")}
   }
   Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){
    Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
     Text("مساحة المقالات",fontWeight=FontWeight.Bold);Text("اكتب مقالك أو استكشف كتابات أعضاء Whisper.",style=MaterialTheme.typography.bodySmall)
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={onNavigate(Routes.WRITE_ARTICLE)}){Text("اكتب مقالًا")};OutlinedButton(onClick={onNavigate(Routes.ARTICLES)}){Text("تصفح المقالات")}}
    }
   }
   if(adminBuild) Card{Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("لوحة تحكم المدير",fontWeight=FontWeight.Bold);Button(onClick={showAdminPanel=true}){Text("فتح اللوحة")}}}
   Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){
    Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
     Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
      if(photoData.isNotBlank() || photoUrl.isNotBlank()) AsyncImage(model=profilePhotoModel(photoData,photoUrl),contentDescription="صورتك الشخصية",modifier=Modifier.size(62.dp).clip(CircleShape))
      else Surface(modifier=Modifier.size(62.dp),shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer){Box(contentAlignment=Alignment.Center){Icon(Icons.Rounded.Person,null,modifier=Modifier.size(32.dp))}}
      Column(Modifier.weight(1f)){Text(displayName.ifBlank{"عضو Whisper"},style=MaterialTheme.typography.titleLarge);Text("ملفك الشخصي",style=MaterialTheme.typography.bodySmall)}
      TextButton(onClick={editing=true}){Text("تعديل")}
     }
     Text("متابعون $ownFollowers   •   تتابع $ownFollowing   •   أصدقاء $ownFriends",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
     if(bio.isNotBlank()) Text(bio)
     if(favorites.isNotBlank()) Text("الأصوات المفضلة: $favorites",style=MaterialTheme.typography.bodySmall)
    }
   }
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("مستخدمو Whisper",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);TextButton(onClick={refresh()},enabled=!loading){Text("تحديث")}}
   when{
    loading->Box(Modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()}
    errorText!=null->Text(errorText!!,color=MaterialTheme.colorScheme.error)
    members.isEmpty()->Text("لا يوجد أعضاء آخرون حتى الآن. ادعُ أصدقاءك للانضمام إلى Whisper.")
    else->members.forEach{m->
     Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){
      Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
       Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
        if(m.photoData.isNotBlank() || m.photoUrl.isNotBlank()) AsyncImage(model=profilePhotoModel(m.photoData,m.photoUrl),contentDescription=null,modifier=Modifier.size(48.dp).clip(CircleShape))
        else Surface(modifier=Modifier.size(48.dp),shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer){Box(contentAlignment=Alignment.Center){Icon(Icons.Rounded.Person,null)}}
        Column(Modifier.weight(1f)){Text(m.displayName,fontWeight=FontWeight.SemiBold);Text(m.bio.ifBlank{"عضو في مجتمع Whisper"},maxLines=2,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        TextButton(onClick={selected=m}){Text("الملف")}
       }
       Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        Button(enabled=busyUid==null,onClick={busyUid=m.uid;scope.launch{try{val next=m.uid !in following;FirebaseCommunity.follow(user.uid,m.uid,next);following=if(next)following+m.uid else following-m.uid;ownFollowing=(ownFollowing+if(next)1 else -1).coerceAtLeast(0)}catch(e:Exception){errorText=e.localizedMessage ?: "تعذر تحديث المتابعة."};busyUid=null}}){Text(if(m.uid in following)"إلغاء المتابعة" else "متابعة")}
        OutlinedButton(enabled=busyUid==null && m.uid !in requested,onClick={busyUid=m.uid;scope.launch{try{FirebaseCommunity.sendFriendRequest(user.uid,displayName,photoData,m.uid);requested=requested+m.uid}catch(e:Exception){errorText=e.localizedMessage ?: "تعذر إرسال طلب الصداقة."};busyUid=null}}){Text(if(m.uid in requested)"تم إرسال الطلب" else "طلب صداقة")}
       }
      }
     }
    }
   }
  }
 }
 if(editing) AlertDialog(onDismissRequest={if(!saving)editing=false},title={Text("تعديل الملف الشخصي")},text={Column(Modifier.fillMaxWidth().heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)){
  OutlinedTextField(displayName,{displayName=it.take(30)},label={Text("اسم العرض")},singleLine=true)
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
   Button(onClick={photoPicker.launch("image/*")}){Text("اختيار صورة من الهاتف")}
   if(photoData.isNotBlank()) Text("تم اختيار صورة")
  }
  OutlinedTextField(photoUrl,{photoUrl=it.take(500)},label={Text("أو رابط صورة HTTPS")})
  OutlinedTextField(bio,{bio=it.take(160)},label={Text("نبذة عنك")},minLines=2)
  OutlinedTextField(favorites,{favorites=it.take(500)},label={Text("أصواتك المفضلة")},minLines=2)
 }},confirmButton={TextButton(enabled=!saving,onClick={saving=true;scope.launch{try{
 FirebaseCommunity.updateProfile(user.uid,displayName,photoUrl,bio,favorites,photoData)
 context.getSharedPreferences("tayer_profile", android.content.Context.MODE_PRIVATE).edit()
  .putString("display_name",displayName.trim()).putString("photo_data",photoData).putString("photo_uri",photoUrl).apply()
 editing=false
}catch(e:Exception){errorText=e.localizedMessage ?: "تعذر حفظ الملف."};saving=false;refresh()}}){Text(if(saving)"جارٍ الحفظ…" else "حفظ")}},dismissButton={TextButton(enabled=!saving,onClick={editing=false}){Text("إلغاء")}})
 LaunchedEffect(selected?.uid) {
  val m=selected
  if(m!=null) {
   selectedIsFriend=runCatching{FirebaseCommunity.isFriend(user.uid,m.uid)}.getOrDefault(false)
   selectedFollowers=runCatching{FirebaseCommunity.followerCount(m.uid)}.getOrDefault(0)
   selectedFollowing=runCatching{FirebaseCommunity.followingCount(m.uid)}.getOrDefault(0)
   selectedFriends=runCatching{FirebaseCommunity.friendCount(m.uid)}.getOrDefault(0)
  }
 }
 selected?.let{m->AlertDialog(onDismissRequest={selected=null},title={Text(m.displayName)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
  if(m.bio.isNotBlank())Text(m.bio)
  Text("المتابعون $selectedFollowers • يتابع $selectedFollowing • الأصدقاء $selectedFriends",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
  if(m.favoriteSounds.isNotBlank())Text("الأصوات المفضلة: "+m.favoriteSounds)
  Text("وقت الاستماع: "+formatListening(m.totalListeningMs));Text("مرات التشغيل: "+m.totalPlays)
 }},confirmButton={
  Row { if(selectedIsFriend) TextButton(onClick={scope.launch{try{FirebaseCommunity.requestChat(user.uid,displayName,m.uid,m.displayName);errorText="تم إرسال طلب المحادثة إلى الإدارة للمراجعة."}catch(e:Exception){errorText=e.localizedMessage};}}){Text("طلب محادثة")}
   TextButton(onClick={selected=null}){Text("إغلاق")} }
 })}
 if(showAdminPanel&&adminBuild) AdminDashboardDialog(onDismiss={showAdminPanel=false})
}

@Composable
fun FriendRequestsScreen(user: FirebaseUser) {
    val scope = rememberCoroutineScope()
    var incoming by remember { mutableStateOf<List<com.aurora.player.community.FriendRequest>>(emptyList()) }
    var outgoing by remember { mutableStateOf<List<com.aurora.player.community.FriendRequest>>(emptyList()) }
    var showIncoming by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var busyUid by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            try {
                incoming = FirebaseCommunity.incomingFriendRequests(user.uid)
                outgoing = FirebaseCommunity.outgoingFriendRequests(user.uid)
            } catch (e: Exception) { message = e.localizedMessage ?: "تعذر تحميل طلبات الصداقة." }
            loading = false
        }
    }
    LaunchedEffect(user.uid) { reload() }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("الإشعارات وطلبات الصداقة", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = showIncoming, onClick = { showIncoming = true }, label = { Text("الواردة (${incoming.count { it.status == "pending" }})") })
                FilterChip(selected = !showIncoming, onClick = { showIncoming = false }, label = { Text("المرسلة (${outgoing.size})") })
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { reload() }, enabled = !loading) { Icon(Icons.Rounded.Refresh, contentDescription = "تحديث") }
            }
            if (loading) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else {
                val items = if (showIncoming) incoming else outgoing
                if (items.isEmpty()) Text(if (showIncoming) "لا توجد طلبات صداقة واردة." else "لم ترسل طلبات صداقة بعد.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                items.forEach { request ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (request.photoData.isNotBlank() || request.photoUrl.isNotBlank()) AsyncImage(model = profilePhotoModel(request.photoData, request.photoUrl), contentDescription = null,
                                    modifier = Modifier.size(46.dp).clip(CircleShape))
                                else Surface(modifier = Modifier.size(46.dp), shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, null) }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(request.displayName, fontWeight = FontWeight.SemiBold)
                                    Text(if (showIncoming) "أرسل إليك طلب صداقة" else "طلب صداقة أرسلته",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(when (request.status) {
                                    "accepted" -> "مقبول"
                                    "rejected" -> "مرفوض"
                                    else -> "معلّق"
                                }, color = when (request.status) {
                                    "accepted" -> MaterialTheme.colorScheme.primary
                                    "rejected" -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }, style = MaterialTheme.typography.labelMedium)
                            }
                            if (showIncoming && request.status == "pending") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(enabled = busyUid == null, onClick = {
                                        busyUid = request.uid
                                        scope.launch {
                                            try { FirebaseCommunity.respondToFriendRequest(user.uid, request.uid, true); reload() }
                                            catch (e: Exception) { message = e.localizedMessage ?: "تعذر قبول الطلب." }
                                            busyUid = null
                                        }
                                    }) { Text("قبول") }
                                    OutlinedButton(enabled = busyUid == null, onClick = {
                                        busyUid = request.uid
                                        scope.launch {
                                            try { FirebaseCommunity.respondToFriendRequest(user.uid, request.uid, false); reload() }
                                            catch (e: Exception) { message = e.localizedMessage ?: "تعذر رفض الطلب." }
                                            busyUid = null
                                        }
                                    }) { Text("رفض") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    message?.let { msg -> AlertDialog(onDismissRequest = { message = null }, text = { Text(msg) },
        confirmButton = { TextButton(onClick = { message = null }) { Text("حسنًا") } }) }
}

private fun formatListening(ms: Long): String {
    val minutes = ms.coerceAtLeast(0L) / 60_000L
    val hours = minutes / 60L
    return if (hours > 0) "${hours} س ${minutes % 60} د" else "${minutes} د"
}


@Composable
private fun AdminDashboardDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var members by remember { mutableStateOf<List<com.aurora.player.community.AdminMember>>(emptyList()) }
    var chatRequests by remember { mutableStateOf<List<com.aurora.player.community.ChatApprovalRequest>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busyUid by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun reload() { loading = true; try { members = FirebaseCommunity.adminMembers(); chatRequests = FirebaseCommunity.adminPendingChatRequests(); error = null } catch (e: Exception) { error = e.localizedMessage ?: "تعذر تحميل المستخدمين." }; loading = false }
    LaunchedEffect(Unit) { reload() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("لوحة تحكم Whisper") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("الحسابات: " + members.size + " • المعروض بحد أقصى 200 حساب", style = MaterialTheme.typography.bodySmall)
            Text("يمكنك تعليق الوصول إلى المجتمع أو إعادة تفعيله. لا تعرض اللوحة كلمات المرور أو بيانات الدخول.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("طلبات السماح بالمحادثات: " + chatRequests.size,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            if(chatRequests.isEmpty()) Text("لا توجد طلبات محادثة معلّقة.")
            chatRequests.forEach { request ->
                Card { Column(Modifier.fillMaxWidth().padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(request.requesterName + " يريد بدء محادثة مع " + request.targetName,fontWeight=FontWeight.SemiBold)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(enabled=busyUid==null,onClick={busyUid=request.id;scope.launch{try{FirebaseCommunity.reviewChatRequest(request.id,true);reload()}catch(e:Exception){error=e.localizedMessage};busyUid=null}}){Text("موافقة")}
                        OutlinedButton(enabled=busyUid==null,onClick={busyUid=request.id;scope.launch{try{FirebaseCommunity.reviewChatRequest(request.id,false);reload()}catch(e:Exception){error=e.localizedMessage};busyUid=null}}){Text("رفض")}
                    }
                } }
            }
            if (loading) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            members.forEach { member -> Card { Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(member.displayName, fontWeight = FontWeight.SemiBold)
                Text("UID: " + member.uid, style = MaterialTheme.typography.labelSmall)
                if (member.bio.isNotBlank()) Text(member.bio, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (member.suspended) "معلّق" else "نشط", color = if (member.suspended) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    OutlinedButton(enabled = busyUid == null && member.uid != FirebaseCommunity.ADMIN_UID, onClick = { busyUid = member.uid; scope.launch { try { FirebaseCommunity.setSuspended(member.uid, !member.suspended); reload() } catch (e: Exception) { error = e.localizedMessage ?: "تعذر تحديث الحساب." }; busyUid = null } }) { Text(if (member.suspended) "رفع التعليق" else "تعليق الحساب") }
                }
            } } }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }, dismissButton = { TextButton(enabled = !loading, onClick = { scope.launch { reload() } }) { Text("تحديث") } })
}
