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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    fun refresh() { scope.launch {
        loading = true; errorText = null
        try { members = FirebaseCommunity.leaderboard() }
        catch (e: Exception) { errorText = e.localizedMessage ?: "تعذر تحميل المجتمع." }
        loading = false
    } }
    LaunchedEffect(Unit) { refresh() }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("مجتمع Whisper", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("استمع، تقدّم، وكن جزءًا من المجتمع", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onSignOut) { Text("تسجيل الخروج") }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(user.displayName ?: "عضو Whisper", style = MaterialTheme.typography.titleLarge)
                    Text("حسابك موثّق وجاهز للمشاركة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", modifier = Modifier.width(34.dp), fontWeight = FontWeight.Bold)
                            Column(Modifier.weight(1f)) {
                                Text(member.displayName, fontWeight = FontWeight.SemiBold)
                                Text("${member.totalPlays} تشغيلات", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatListening(member.totalListeningMs), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            Text("الترتيب مبني على وقت الاستماع المسجّل داخل التطبيق، والأرقام تقريبية.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatListening(ms: Long): String {
    val minutes = ms.coerceAtLeast(0L) / 60_000L
    val hours = minutes / 60L
    return if (hours > 0) "${hours} س ${minutes % 60} د" else "${minutes} د"
}
