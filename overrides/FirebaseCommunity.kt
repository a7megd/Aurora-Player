package com.aurora.player.community

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

data class AdminMember(val uid: String, val displayName: String, val bio: String, val suspended: Boolean)

data class CommunityMember(
    val uid: String,
    val displayName: String,
    val totalListeningMs: Long,
    val totalPlays: Long,
    val photoUrl: String = "",
    val bio: String = "",
    val favoriteSounds: String = ""
)


data class FriendRequest(
    val uid: String,
    val displayName: String,
    val photoUrl: String,
    val status: String,
    val createdAt: Long = 0L
)
\nobject FirebaseCommunity {
    const val ADMIN_UID = "Cva1DdKPU3QIVXOoA3kSJG0DYxw1"
    fun isAdmin(uid: String): Boolean = uid == ADMIN_UID
    suspend fun isSuspended(uid: String): Boolean = db.collection("users").document(uid).get().await().getBoolean("suspended") == true
    suspend fun adminMembers(): List<AdminMember> {
        check(isAdmin(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty())) { "غير مصرح لك باستخدام لوحة الإدارة." }
        return db.collection("users").limit(200).get().await().documents.map { doc ->
            AdminMember(doc.id, doc.getString("displayName") ?: "عضو Whisper", doc.getString("bio").orEmpty(), doc.getBoolean("suspended") == true)
        }
    }
    suspend fun setSuspended(targetUid: String, suspended: Boolean) {
        check(isAdmin(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty())) { "غير مصرح لك بتنفيذ هذا الإجراء." }
        require(targetUid != ADMIN_UID) { "لا يمكن تعليق حساب المدير." }
        db.collection("users").document(targetUid).update("suspended", suspended).await()
    }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    suspend fun ensureProfile(user: FirebaseUser, fallbackName: String? = null) {
        val ref = db.collection("users").document(user.uid)
        val snapshot = ref.get().await()
        if (!snapshot.exists()) {
            val name = user.displayName?.takeIf { it.isNotBlank() }
                ?: fallbackName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore('@')?.take(24) ?: "Whisper User"
            ref.set(mapOf(
                "uid" to user.uid, "displayName" to name,
                "photoUrl" to "", "bio" to "", "favoriteSounds" to "",
                "totalListeningMs" to 0L, "totalPlays" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "lastActiveAt" to FieldValue.serverTimestamp()
            )).await()
        } else {
            // Backfill fields used by profile editing and leaderboard for accounts created by older builds.
            val updates = mutableMapOf<String, Any>("lastActiveAt" to FieldValue.serverTimestamp())
            if (!snapshot.contains("displayName")) {
                updates["displayName"] = user.displayName?.takeIf { it.isNotBlank() }
                    ?: fallbackName?.takeIf { it.isNotBlank() }
                    ?: user.email?.substringBefore('@')?.take(24) ?: "Whisper User"
            }
            if (!snapshot.contains("photoUrl")) updates["photoUrl"] = ""
            if (!snapshot.contains("bio")) updates["bio"] = ""
            if (!snapshot.contains("favoriteSounds")) updates["favoriteSounds"] = ""
            if (!snapshot.contains("totalListeningMs")) updates["totalListeningMs"] = 0L
            if (!snapshot.contains("totalPlays")) updates["totalPlays"] = 0L
            ref.update(updates).await()
        }
    }

    suspend fun updateProfile(uid: String, displayName: String, photoUrl: String, bio: String, favoriteSounds: String) {
        require(displayName.trim().length in 2..30) { "الاسم يجب أن يكون بين حرفين و30 حرفًا." }
        require(photoUrl.length <= 500 && (photoUrl.isBlank() || photoUrl.startsWith("https://"))) {
            "رابط الصورة يجب أن يبدأ بـ https://"
        }
        require(bio.length <= 160) { "النبذة لا يمكن أن تتجاوز 160 حرفًا." }
        require(favoriteSounds.length <= 500) { "قائمة الأصوات المفضلة طويلة جدًا." }
        db.collection("users").document(uid).update(
            mapOf(
                "displayName" to displayName.trim(),
                "photoUrl" to photoUrl.trim(),
                "bio" to bio.trim(),
                "favoriteSounds" to favoriteSounds.trim()
            )
        ).await()
    }

    fun recordPlay(uid: String) {
        db.collection("users").document(uid).update(
            "totalPlays", FieldValue.increment(1L),
            "lastActiveAt", FieldValue.serverTimestamp()
        )
    }

    suspend fun addListeningMinute(uid: String) {
        db.collection("users").document(uid).update(
            "totalListeningMs", FieldValue.increment(60_000L),
            "lastActiveAt", FieldValue.serverTimestamp()
        ).await()
    }

    suspend fun communityMembers(): List<CommunityMember> =
        db.collection("users").limit(100).get().await().documents
            .filter { it.getBoolean("suspended") != true }
            .map { doc ->
                CommunityMember(
                    uid = doc.getString("uid") ?: doc.id,
                    displayName = doc.getString("displayName") ?: "Whisper User",
                    totalListeningMs = doc.getLong("totalListeningMs") ?: 0L,
                    totalPlays = doc.getLong("totalPlays") ?: 0L,
                    photoUrl = doc.getString("photoUrl").orEmpty(),
                    bio = doc.getString("bio").orEmpty(),
                    favoriteSounds = doc.getString("favoriteSounds").orEmpty()
                )
            }.sortedBy { it.displayName.lowercase() }

    suspend fun isFollowing(uid: String, targetUid: String): Boolean =
        db.collection("users").document(uid).collection("following").document(targetUid).get().await().exists()

    suspend fun follow(uid: String, targetUid: String, enabled: Boolean) {
        require(uid != targetUid) { "لا يمكنك متابعة حسابك." }
        val ref = db.collection("users").document(uid).collection("following").document(targetUid)
        if (enabled) ref.set(mapOf("targetUid" to targetUid, "createdAt" to FieldValue.serverTimestamp())).await()
        else ref.delete().await()
    }

    suspend fun requestFriend(fromUid: String, fromName: String, targetUid: String) {
        require(fromUid != targetUid) { "لا يمكنك إرسال طلب صداقة لنفسك." }
        db.collection("users").document(targetUid).collection("friendRequests").document(fromUid)
            .set(mapOf("requesterUid" to fromUid, "requesterName" to fromName, "createdAt" to FieldValue.serverTimestamp())).await()
    }


    private fun requestData(uid: String, displayName: String, photoUrl: String, targetUid: String): Map<String, Any> =
        mapOf("requesterUid" to uid, "requesterName" to displayName, "requesterPhotoUrl" to photoUrl,
            "recipientUid" to targetUid, "status" to "pending", "createdAt" to FieldValue.serverTimestamp())

    suspend fun sendFriendRequest(fromUid: String, fromName: String, fromPhotoUrl: String, targetUid: String) {
        require(fromUid != targetUid) { "لا يمكنك إرسال طلب صداقة لنفسك." }
        val sender = db.collection("users").document(fromUid).collection("sentFriendRequests").document(targetUid)
        val receiver = db.collection("users").document(targetUid).collection("friendRequests").document(fromUid)
        val current = sender.get().await()
        require(!current.exists() || current.getString("status") == "rejected") { "لديك طلب صداقة سابق أو قائم لهذا المستخدم." }
        val data = requestData(fromUid, fromName, fromPhotoUrl, targetUid)
        db.runBatch { batch -> batch.set(sender, data); batch.set(receiver, data) }.await()
    }

    suspend fun incomingFriendRequests(uid: String): List<FriendRequest> =
        db.collection("users").document(uid).collection("friendRequests").get().await().documents.map { d ->
            FriendRequest(d.id, d.getString("requesterName") ?: "عضو Whisper",
                d.getString("requesterPhotoUrl").orEmpty(), d.getString("status") ?: "pending",
                d.getTimestamp("createdAt")?.toDate()?.time ?: 0L)
        }.sortedByDescending { it.createdAt }

    suspend fun outgoingFriendRequests(uid: String): List<FriendRequest> =
        db.collection("users").document(uid).collection("sentFriendRequests").get().await().documents.map { d ->
            FriendRequest(d.id, d.getString("recipientName") ?: d.getString("requesterName") ?: "عضو Whisper",
                d.getString("recipientPhotoUrl").orEmpty(), d.getString("status") ?: "pending",
                d.getTimestamp("createdAt")?.toDate()?.time ?: 0L)
        }.sortedByDescending { it.createdAt }

    suspend fun respondToFriendRequest(recipientUid: String, requesterUid: String, accept: Boolean) {
        val incoming = db.collection("users").document(recipientUid).collection("friendRequests").document(requesterUid)
        val outgoing = db.collection("users").document(requesterUid).collection("sentFriendRequests").document(recipientUid)
        val newStatus = if (accept) "accepted" else "rejected"
        db.runBatch { batch ->
            batch.update(incoming, "status", newStatus, "respondedAt", FieldValue.serverTimestamp())
            batch.update(outgoing, "status", newStatus, "respondedAt", FieldValue.serverTimestamp())
            if (accept) {
                batch.set(db.collection("users").document(recipientUid).collection("friends").document(requesterUid),
                    mapOf("uid" to requesterUid, "createdAt" to FieldValue.serverTimestamp()))
                batch.set(db.collection("users").document(requesterUid).collection("friends").document(recipientUid),
                    mapOf("uid" to recipientUid, "createdAt" to FieldValue.serverTimestamp()))
            }
        }.await()
    }

    suspend fun leaderboard(): List<CommunityMember> =
        db.collection("users").orderBy("totalListeningMs", Query.Direction.DESCENDING)
            .limit(25).get().await().documents.map { doc ->
                CommunityMember(
                    uid = doc.getString("uid") ?: doc.id,
                    displayName = doc.getString("displayName") ?: "Whisper User",
                    totalListeningMs = doc.getLong("totalListeningMs") ?: 0L,
                    totalPlays = doc.getLong("totalPlays") ?: 0L,
                    photoUrl = doc.getString("photoUrl").orEmpty(),
                    bio = doc.getString("bio").orEmpty(),
                    favoriteSounds = doc.getString("favoriteSounds").orEmpty()
                )
            }
}
