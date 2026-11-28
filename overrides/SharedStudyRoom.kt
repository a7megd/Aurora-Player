package com.aurora.player.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

@Composable
fun SharedStudyRoomCard() {
    val user = FirebaseAuth.getInstance().currentUser
    val uid = user?.uid.orEmpty()
    val name = user?.displayName?.takeIf { it.isNotBlank() } ?: user?.email?.substringBefore("@") ?: "طالب"
    val db = remember { FirebaseFirestore.getInstance() }
    var codeInput by remember { mutableStateOf("") }
    var roomCode by remember { mutableStateOf<String?>(null) }
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    var owner by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var endsAt by remember { mutableLongStateOf(0L) }
    var secondsLeft by remember { mutableLongStateOf(0L) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(roomCode) {
        val code = roomCode
        val registration = if (code != null) {
            db.collection("studyRooms").document(code).addSnapshotListener { snap, error ->
                if (error != null) {
                    message = error.localizedMessage ?: "تعذر تحديث الغرفة"
                } else if (snap == null || !snap.exists()) {
                    roomCode = null
                    names = emptyList()
                    message = "تم إغلاق الغرفة."
                } else {
                    val members = snap.get("members") as? Map<*, *> ?: emptyMap<Any, Any>()
                    names = members.values.mapNotNull { it as? String }
                    owner = snap.getString("ownerUid") == uid
                    running = snap.getBoolean("running") == true
                    endsAt = snap.getLong("endsAt") ?: 0L
                }
            }
        } else null
        onDispose { registration?.remove() }
    }

    LaunchedEffect(running, endsAt) {
        while (running && endsAt > 0L) {
            secondsLeft = ((endsAt - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L)
            if (secondsLeft == 0L) {
                if (owner && roomCode != null) {
                    db.collection("studyRooms").document(roomCode!!).update(
                        mapOf("running" to false, "endsAt" to 0L, "updatedAt" to System.currentTimeMillis())
                    )
                }
                break
            }
            delay(1000)
        }
    }

    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("غرف مذاكرة مباشرة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (user == null) {
                Text("سجّل الدخول لإنشاء غرفة مشتركة أو الانضمام إلى أصدقائك.")
            } else if (roomCode == null) {
                Text("أنشئ غرفة أو أدخل رمز صديقك. الحد الأقصى 10 طلاب.")
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it.filter(Char::isDigit).take(6) },
                    label = { Text("رمز الغرفة المكوّن من 6 أرقام") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = {
                        busy = true
                        val code = (System.currentTimeMillis() % 900000L + 100000L).toString()
                        db.collection("studyRooms").document(code).set(
                            mapOf(
                                "code" to code, "ownerUid" to uid, "memberIds" to listOf(uid),
                                "members" to mapOf(uid to name), "running" to false, "endsAt" to 0L,
                                "createdAt" to System.currentTimeMillis(), "updatedAt" to System.currentTimeMillis()
                            )
                        ).addOnSuccessListener {
                            roomCode = code
                            message = "تم إنشاء الغرفة. شارك الرمز مع أصدقائك."
                            busy = false
                        }.addOnFailureListener {
                            message = it.localizedMessage ?: "تعذر إنشاء الغرفة"
                            busy = false
                        }
                    }) { Text("إنشاء غرفة") }
                    OutlinedButton(enabled = !busy && codeInput.length == 6, onClick = {
                        busy = true
                        val ref = db.collection("studyRooms").document(codeInput)
                        ref.get().addOnSuccessListener { snap ->
                            if (!snap.exists()) {
                                message = "رمز الغرفة غير صحيح."
                                busy = false
                            } else {
                                val ids = (snap.get("memberIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
                                if (uid !in ids && ids.size >= 10) {
                                    message = "الغرفة مكتملة (10 طلاب)."
                                    busy = false
                                } else {
                                    val members = (snap.get("members") as? Map<*, *>)?.entries
                                        ?.mapNotNull { entry -> (entry.key as? String)?.let { it to (entry.value as? String).orEmpty() } }
                                        ?.toMap()?.toMutableMap() ?: mutableMapOf()
                                    members[uid] = name
                                    ref.update(
                                        mapOf(
                                            "memberIds" to (ids + uid).distinct(),
                                            "members" to members,
                                            "updatedAt" to System.currentTimeMillis()
                                        )
                                    ).addOnSuccessListener {
                                        roomCode = codeInput
                                        message = "انضممت إلى الغرفة."
                                        busy = false
                                    }.addOnFailureListener {
                                        message = it.localizedMessage ?: "تعذر الانضمام"
                                        busy = false
                                    }
                                }
                            }
                        }.addOnFailureListener {
                            message = it.localizedMessage ?: "تعذر البحث عن الغرفة"
                            busy = false
                        }
                    }) { Text("انضمام") }
                }
            } else {
                Text("رمز الغرفة: " + roomCode, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("المشاركون: " + names.size + " / 10")
                names.forEach { Text("• " + it) }
                Text(if (running) "جلسة التركيز المشتركة نشطة" else "الجلسة متوقفة")
                if (running) Text("%02d:%02d".format(secondsLeft / 60L, secondsLeft % 60L), style = MaterialTheme.typography.headlineMedium)
                if (owner) {
                    Button(onClick = {
                        val end = System.currentTimeMillis() + 25L * 60_000L
                        db.collection("studyRooms").document(roomCode!!).update(
                            mapOf("running" to true, "endsAt" to end, "updatedAt" to System.currentTimeMillis())
                        )
                    }, enabled = !running) { Text("بدء جلسة تركيز 25 دقيقة") }
                    OutlinedButton(onClick = {
                        db.collection("studyRooms").document(roomCode!!).update(
                            mapOf("running" to false, "endsAt" to 0L, "updatedAt" to System.currentTimeMillis())
                        )
                    }, enabled = running) { Text("إيقاف الجلسة") }
                }
                OutlinedButton(onClick = {
                    val ref = db.collection("studyRooms").document(roomCode!!)
                    if (owner) {
                        ref.delete()
                    } else {
                        ref.update(
                            mapOf(
                                "memberIds" to FieldValue.arrayRemove(uid),
                                "members." + uid to FieldValue.delete(),
                                "updatedAt" to System.currentTimeMillis()
                            )
                        )
                    }
                    roomCode = null
                }, modifier = Modifier.fillMaxWidth()) { Text(if (owner) "إغلاق الغرفة" else "مغادرة الغرفة") }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
