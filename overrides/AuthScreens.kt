package com.aurora.player.ui.navigation

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

@Composable
fun CommunityScreen(user: FirebaseUser, onSignOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    var members by remember { mutableStateOf<List<com.aurora.player.community.CommunityMember>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf(user.displayName.orEmpty()) }
    var photoUrl by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var showAdminPanel by remember { mutableStateOf(false) }
    var showArticles by remember { mutableStateOf(false) }
    val adminBuild = LocalContext.current.packageName.endsWith(".test") && FirebaseCommunity.isAdmin(user.uid)
    fun refresh() { scope.launch {
        loading = true; errorText = null
        try {
            FirebaseCommunity.ensureProfile(user)
            members = FirebaseCommunity.leaderboard()
            members.firstOrNull { it.uid == user.uid }?.let {
                displayName = it.displayName; photoUrl = it.photoUrl; bio = it.bio; favorites = it.favoriteSounds
            }
        } catch (e: Exception) { errorText = e.localizedMessage ?: "تعذر تحميل المجتمع." }
        loading = false
    } }
    LaunchedEffect(user.uid) { refresh() }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("مجتمع Whisper", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("استمع، تقدّم، وكن جزءًا من المجتمع", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                TextButton(onClick = onSignOut) { Text("تسجيل الخروج") }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text("مساحة المقالات", fontWeight = FontWeight.Bold); Text("اقرأ واكتب وانشر مقالاتك مع مجتمع Whisper", style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = { showArticles = true }) { Text("استكشف") }
                }
            }
            if (adminBuild) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) { Text("لوحة تحكم المدير", fontWeight = FontWeight.Bold); Text("إدارة عضوية المجتمع ومراجعة الحسابات", style = MaterialTheme.typography.bodySmall) }
                        Button(onClick = { showAdminPanel = true }) { Text("فتح اللوحة") }
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (photoUrl.isNotBlank()) AsyncImage(model = photoUrl, contentDescription = "الصورة الشخصية", modifier = Modifier.size(68.dp).clip(CircleShape))
                        else Surface(modifier = Modifier.size(68.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, null, modifier = Modifier.size(34.dp)) } }
                        Column(Modifier.weight(1f)) { Text(displayName.ifBlank { "عضو Whisper" }, style = MaterialTheme.typography.titleLarge); Text("ملفك الشخصي", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        TextButton(onClick = { editing = true; message = null }) { Text("تعديل") }
                    }
                    if (bio.isNotBlank()) Text(bio, style = MaterialTheme.typography.bodyMedium)
                    if (favorites.isNotBlank()) {
                        Text("أصواتي المفضلة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        favorites.split(",", "\n").map { it.trim() }.filter { it.isNotBlank() }.forEach { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(7.dp)); Text(it, style = MaterialTheme.typography.bodySmall) } }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("الأكثر استماعًا", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { refresh() }, enabled = !loading) { Text("تحديث") }
            }
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                errorText != null -> Text(errorText!!, color = MaterialTheme.colorScheme.error)
                members.isEmpty() -> Text("لا يوجد أعضاء في لوحة الترتيب بعد. ابدأ الاستماع لتكون من أوائل المتصدرين.")
                else -> members.forEachIndexed { index, member ->
                    Card(colors = CardDefaults.cardColors(containerColor = if (member.uid == user.uid) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (member.photoUrl.isNotBlank()) AsyncImage(model = member.photoUrl, contentDescription = null, modifier = Modifier.size(42.dp).clip(CircleShape))
                                else Surface(modifier = Modifier.size(42.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, null) } }
                                Column(Modifier.weight(1f)) { Text("${index + 1}. ${member.displayName}", fontWeight = FontWeight.SemiBold); Text("${member.totalPlays} تشغيلات • ${formatListening(member.totalListeningMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            if (member.bio.isNotBlank()) Text(member.bio, style = MaterialTheme.typography.bodySmall)
                            if (member.favoriteSounds.isNotBlank()) Text("المفضلة: " + member.favoriteSounds.replace("\n", " • "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Text("الترتيب مبني على وقت الاستماع المسجّل داخل التطبيق، والأرقام تقريبية.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (editing) AlertDialog(
        onDismissRequest = { if (!saving) editing = false },
        title = { Text("تعديل الملف الشخصي") },
        text = { Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(value = displayName, onValueChange = { displayName = it.take(30) }, label = { Text("اسم العرض") }, singleLine = true)
            OutlinedTextField(value = photoUrl, onValueChange = { photoUrl = it.take(500) }, label = { Text("رابط الصورة الشخصية (HTTPS)") }, supportingText = { Text("ضع رابط الصورة المباشر؛ ستظهر المعاينة هنا ثم تظهر الصورة لباقي الأعضاء بعد الحفظ.") }, singleLine = true)
            if (photoUrl.startsWith("https://")) {
                AsyncImage(model = photoUrl, contentDescription = "معاينة الصورة الشخصية", modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp).clip(RoundedCornerShape(16.dp)))
            }
            OutlinedTextField(value = bio, onValueChange = { bio = it.take(160) }, label = { Text("نبذة / كابشن") }, supportingText = { Text("${bio.length}/160") }, minLines = 2, maxLines = 4)
            OutlinedTextField(value = favorites, onValueChange = { favorites = it.take(500) }, label = { Text("أصواتك المفضلة") }, supportingText = { Text("اكتب أسماء الأصوات أو الأغاني، وافصل بينها بفاصلة أو سطر جديد.") }, minLines = 3, maxLines = 5)
        } },
        confirmButton = { TextButton(enabled = !saving, onClick = { scope.launch {
            saving = true; message = null
            try { FirebaseCommunity.updateProfile(user.uid, displayName, photoUrl, bio, favorites); editing = false; refresh() }
            catch (e: Exception) { message = e.localizedMessage ?: "تعذر حفظ الملف الشخصي." }
            saving = false
        } }) { if (saving) CircularProgressIndicator(Modifier.size(18.dp)) else Text("حفظ") } },
        dismissButton = { TextButton(enabled = !saving, onClick = { editing = false }) { Text("إلغاء") } }
    )
    message?.let { AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton(onClick = { message = null }) { Text("حسنًا") } }, text = { Text(it) }) }
    if (showAdminPanel && adminBuild) AdminDashboardDialog(onDismiss = { showAdminPanel = false })
    if (showArticles) ArticleHubDialog(user = user, onDismiss = { showArticles = false })
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
    var loading by remember { mutableStateOf(true) }
    var busyUid by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun reload() { loading = true; try { members = FirebaseCommunity.adminMembers(); error = null } catch (e: Exception) { error = e.localizedMessage ?: "تعذر تحميل المستخدمين." }; loading = false }
    LaunchedEffect(Unit) { reload() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("لوحة تحكم Whisper") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("الحسابات: " + members.size + " • المعروض بحد أقصى 200 حساب", style = MaterialTheme.typography.bodySmall)
            Text("يمكنك تعليق الوصول إلى المجتمع أو إعادة تفعيله. لا تعرض اللوحة كلمات المرور أو بيانات الدخول.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
