package com.aurora.player.community

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

data class AdminMember(val uid: String, val displayName: String, val bio: String, val suspended: Boolean)

data class CommunityMember(
    val uid: String,
    val displayName: String,
    val totalListeningMs: Long,
    val totalPlays: Long,
    val photoUrl: String = "",
    val bio: String = "",
    val favoriteSounds: String = "",
    val photoData: String = ""
)


data class ChatApprovalRequest(val id: String, val requesterUid: String, val requesterName: String, val targetUid: String, val targetName: String, val status: String, val createdAt: Long = 0L)

data class BlockedMember(val uid: String, val displayName: String, val photoUrl: String = "")

data class FriendRequest(
    val uid: String,
    val displayName: String,
    val photoUrl: String,
    val status: String,
    val createdAt: Long = 0L,
    val photoData: String = ""
)

object FirebaseCommunity {
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
                "photoUrl" to "", "photoData" to "", "bio" to "", "favoriteSounds" to "",
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
            if (snapshot.getString("photoData").orEmpty().isNotEmpty()) updates["photoData"] = ""
            if (!snapshot.contains("bio")) updates["bio"] = ""
            if (!snapshot.contains("favoriteSounds")) updates["favoriteSounds"] = ""
            if (!snapshot.contains("totalListeningMs")) updates["totalListeningMs"] = 0L
            if (!snapshot.contains("totalPlays")) updates["totalPlays"] = 0L
            ref.update(updates).await()
        }
    }

    suspend fun updateProfile(uid: String, displayName: String, photoUrl: String, bio: String, favoriteSounds: String, photoData: String) {
        require(displayName.trim().length in 2..30) { "الاسم يجب أن يكون بين حرفين و30 حرفًا." }
        require(photoUrl.length <= 500 && (photoUrl.isBlank() || photoUrl.startsWith("https://"))) {
            "رابط الصورة يجب أن يبدأ بـ https://"
        }
        require(photoData.length <= 180000) { "حجم الصورة بعد الضغط كبير جدًا." }
        require(bio.length <= 160) { "النبذة لا يمكن أن تتجاوز 160 حرفًا." }
        require(favoriteSounds.length <= 500) { "قائمة الأصوات المفضلة طويلة جدًا." }
        db.collection("users").document(uid).update(
            mapOf(
                "displayName" to displayName.trim(),
                "photoUrl" to photoUrl.trim(),
                "photoData" to "",
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
                    photoData = doc.getString("photoData").orEmpty(),
                    bio = doc.getString("bio").orEmpty(),
                    favoriteSounds = doc.getString("favoriteSounds").orEmpty()
                )
            }.sortedBy { it.displayName.lowercase() }

    suspend fun followingIds(uid: String): Set<String> =
        db.collection("users").document(uid).collection("following").get().await().documents.map { it.id }.toSet()

    suspend fun isFollowing(uid: String, targetUid: String): Boolean =
        db.collection("users").document(uid).collection("following").document(targetUid).get().await().exists()

    suspend fun follow(uid: String, targetUid: String, enabled: Boolean) {
        require(uid != targetUid) { "لا يمكنك متابعة حسابك." }
        val following = db.collection("users").document(uid).collection("following").document(targetUid)
        val followers = db.collection("users").document(targetUid).collection("followers").document(uid)
        db.runBatch { batch ->
            if (enabled) {
                val data = mapOf("uid" to uid, "targetUid" to targetUid, "createdAt" to FieldValue.serverTimestamp())
                batch.set(following, data, SetOptions.merge())
                batch.set(followers, data, SetOptions.merge())
            } else {
                batch.delete(following)
                batch.delete(followers)
            }
        }.await()
    }

    suspend fun followerCount(uid: String): Int =
        db.collection("users").document(uid).collection("followers").get().await().size()

    suspend fun followingCount(uid: String): Int =
        db.collection("users").document(uid).collection("following").get().await().size()

    suspend fun friendCount(uid: String): Int =
        db.collection("users").document(uid).collection("friends").get().await().size()

    suspend fun blockedUserIds(uid: String): Set<String> =
        db.collection("users").document(uid).collection("blockedUsers").get().await().documents.map { it.id }.toSet()

    suspend fun isBlockedByMe(uid: String, targetUid: String): Boolean =
        db.collection("users").document(uid).collection("blockedUsers").document(targetUid).get().await().exists()

    suspend fun isBlocked(firstUid: String, secondUid: String): Boolean {
        if (firstUid == secondUid) return false
        val first = db.collection("users").document(firstUid).collection("blockedUsers").document(secondUid).get().await().exists()
        if (first) return true
        return db.collection("users").document(secondUid).collection("blockedUsers").document(firstUid).get().await().exists()
    }

    suspend fun blockedUsers(uid: String): List<BlockedMember> {
        val docs = db.collection("users").document(uid).collection("blockedUsers").get().await().documents
        return docs.map { doc ->
            val profile = db.collection("users").document(doc.id).get().await()
            BlockedMember(doc.id, profile.getString("displayName") ?: "عضو Whisper", profile.getString("photoUrl").orEmpty())
        }.sortedBy { it.displayName.lowercase() }
    }

    suspend fun blockUser(uid: String, targetUid: String) {
        require(uid != targetUid) { "لا يمكنك حظر حسابك." }
        val profile = db.collection("users").document(targetUid).get().await()
        check(profile.exists()) { "هذا الحساب لم يعد متاحًا." }
        db.collection("users").document(uid).collection("blockedUsers").document(targetUid).set(
            mapOf("uid" to targetUid, "createdAt" to FieldValue.serverTimestamp())
        ).await()
    }

    suspend fun unblockUser(uid: String, targetUid: String) {
        db.collection("users").document(uid).collection("blockedUsers").document(targetUid).delete().await()
    }

    suspend fun friendIds(uid: String): Set<String> =
        db.collection("users").document(uid).collection("friends").get().await().documents.map { it.id }.toSet()

    suspend fun isFriend(uid: String, targetUid: String): Boolean =
        db.collection("users").document(uid).collection("friends").document(targetUid).get().await().exists()

    suspend fun requestFriend(fromUid: String, fromName: String, targetUid: String) {
        require(fromUid != targetUid) { "لا يمكنك إرسال طلب صداقة لنفسك." }
        db.collection("users").document(targetUid).collection("friendRequests").document(fromUid)
            .set(mapOf("requesterUid" to fromUid, "requesterName" to fromName, "createdAt" to FieldValue.serverTimestamp())).await()
    }


    private fun requestData(uid: String, displayName: String, photoUrl: String, targetUid: String): Map<String, Any> =
        mapOf("requesterUid" to uid, "requesterName" to displayName, "requesterPhotoUrl" to photoUrl,
            "recipientUid" to targetUid, "status" to "pending", "createdAt" to FieldValue.serverTimestamp())

    suspend fun sendFriendRequest(fromUid: String, fromName: String, fromPhotoData: String, targetUid: String) {
        require(fromUid != targetUid) { "لا يمكنك إرسال طلب صداقة لنفسك." }
        val sender = db.collection("users").document(fromUid).collection("sentFriendRequests").document(targetUid)
        val receiver = db.collection("users").document(targetUid).collection("friendRequests").document(fromUid)
        val current = sender.get().await()
        val incomingCurrent = receiver.get().await()
        require(!current.exists() || current.getString("status") == "rejected") {
            "لديك طلب صداقة قائم أو سبق قبوله لهذا المستخدم."
        }
        require(!incomingCurrent.exists() || incomingCurrent.getString("status") == "rejected") {
            "يوجد طلب سابق بينكما. افتح طلبات الصداقة لقبوله أو رفضه أولًا."
        }
        val targetProfile = db.collection("users").document(targetUid).get().await()
        check(targetProfile.exists()) { "هذا الحساب لم يعد متاحًا." }
        val data = requestData(fromUid, fromName, "", targetUid) + mapOf(
            "requesterPhotoData" to fromPhotoData,
            "recipientName" to (targetProfile.getString("displayName") ?: "عضو Whisper"),
            "recipientPhotoUrl" to targetProfile.getString("photoUrl").orEmpty(),
            "recipientPhotoData" to targetProfile.getString("photoData").orEmpty()
        )
        db.runBatch { batch ->
            batch.set(sender, data)
            batch.set(receiver, data)
        }.await()
    }

    suspend fun incomingFriendRequests(uid: String): List<FriendRequest> =
        db.collection("users").document(uid).collection("friendRequests").get().await().documents.map { d ->
            FriendRequest(d.id, d.getString("requesterName") ?: "عضو Whisper",
                d.getString("requesterPhotoUrl").orEmpty(), d.getString("status") ?: "pending",
                d.getTimestamp("createdAt")?.toDate()?.time ?: 0L, d.getString("requesterPhotoData").orEmpty())
        }.sortedByDescending { it.createdAt }

    suspend fun outgoingFriendRequests(uid: String): List<FriendRequest> =
        db.collection("users").document(uid).collection("sentFriendRequests").get().await().documents.map { d ->
            FriendRequest(d.id, d.getString("recipientName") ?: d.getString("requesterName") ?: "عضو Whisper",
                d.getString("recipientPhotoUrl").orEmpty(), d.getString("status") ?: "pending",
                d.getTimestamp("createdAt")?.toDate()?.time ?: 0L, d.getString("recipientPhotoData").orEmpty())
        }.sortedByDescending { it.createdAt }

    suspend fun respondToFriendRequest(recipientUid: String, requesterUid: String, accept: Boolean) {
        val incoming = db.collection("users").document(recipientUid).collection("friendRequests").document(requesterUid)
        val outgoing = db.collection("users").document(requesterUid).collection("sentFriendRequests").document(recipientUid)
        val newStatus = if (accept) "accepted" else "rejected"
        db.runTransaction { tx ->
            val incomingSnapshot = tx.get(incoming)
            val outgoingSnapshot = tx.get(outgoing)
            check(incomingSnapshot.exists()) { "طلب الصداقة لم يعد موجودًا. حدّث القائمة وحاول مجددًا." }
            check(incomingSnapshot.getString("status") != "accepted") { "تم قبول هذا الطلب بالفعل." }
            tx.update(incoming, mapOf("status" to newStatus, "respondedAt" to FieldValue.serverTimestamp()))
            // Older app versions created only the incoming request. Do not update a missing outgoing document.
            if (outgoingSnapshot.exists()) {
                tx.update(outgoing, mapOf("status" to newStatus, "respondedAt" to FieldValue.serverTimestamp()))
            }
            if (accept) {
                val recipientFriend = db.collection("users").document(recipientUid).collection("friends").document(requesterUid)
                val requesterFriend = db.collection("users").document(requesterUid).collection("friends").document(recipientUid)
                val friendData = mapOf("createdAt" to FieldValue.serverTimestamp())
                tx.set(recipientFriend, friendData + mapOf("uid" to requesterUid), SetOptions.merge())
                tx.set(requesterFriend, friendData + mapOf("uid" to recipientUid), SetOptions.merge())
            }
        }.await()
    }

    suspend fun requestChat(fromUid: String, fromName: String, targetUid: String, targetName: String) {
        require(fromUid != targetUid) { "لا يمكنك طلب محادثة مع نفسك." }
        val friendship = db.collection("users").document(fromUid).collection("friends").document(targetUid).get().await()
        check(friendship.exists()) { "المحادثة متاحة للأصدقاء فقط. أرسل طلب صداقة وانتظر قبوله." }
        val existing = db.collection("chatRequests").whereEqualTo("requesterUid", fromUid).get().await()
        check(existing.documents.none { it.getString("targetUid") == targetUid && it.getString("status") == "pending" }) {
            "طلب المحادثة قيد مراجعة الإدارة بالفعل."
        }
        db.collection("chatRequests").add(mapOf(
            "requesterUid" to fromUid, "requesterName" to fromName,
            "targetUid" to targetUid, "targetName" to targetName,
            "status" to "pending", "createdAt" to FieldValue.serverTimestamp()
        )).await()
    }

    suspend fun adminPendingChatRequests(): List<ChatApprovalRequest> {
        check(isAdmin(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty())) { "غير مصرح." }
        return db.collection("chatRequests").whereEqualTo("status", "pending").get().await().documents.map { d ->
            ChatApprovalRequest(d.id, d.getString("requesterUid").orEmpty(), d.getString("requesterName").orEmpty(),
                d.getString("targetUid").orEmpty(), d.getString("targetName").orEmpty(), "pending",
                d.getTimestamp("createdAt")?.toDate()?.time ?: 0L)
        }.sortedBy { it.createdAt }
    }

    suspend fun reviewChatRequest(requestId: String, approve: Boolean) {
        check(isAdmin(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty())) { "غير مصرح." }
        val ref = db.collection("chatRequests").document(requestId)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            check(snap.exists() && snap.getString("status") == "pending") { "تمت مراجعة هذا الطلب بالفعل." }
            val requester = snap.getString("requesterUid").orEmpty()
            val target = snap.getString("targetUid").orEmpty()
            tx.update(ref, if (approve) mapOf("status" to "approved", "reviewedAt" to FieldValue.serverTimestamp(), "threadId" to listOf(requester, target).sorted().joinToString("_")) else mapOf("status" to "rejected", "reviewedAt" to FieldValue.serverTimestamp()))
            if (approve) {
                val threadId = listOf(requester, target).sorted().joinToString("_")
                val thread = db.collection("chatThreads").document(threadId)
                tx.set(thread, mapOf("participants" to listOf(requester, target), "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            }
        }.await()
    }

    suspend fun approvedChats(uid: String): List<Map<String, String>> =
        db.collection("chatThreads").whereArrayContains("participants", uid).get().await().documents.map { d ->
            val other = (d.get("participants") as? List<*>)?.filterIsInstance<String>()?.firstOrNull { it != uid }.orEmpty()
            mapOf("threadId" to d.id, "otherUid" to other)
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
