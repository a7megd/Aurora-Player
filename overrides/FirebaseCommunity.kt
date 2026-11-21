package com.aurora.player.community

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

data class CommunityMember(
    val uid: String,
    val displayName: String,
    val totalListeningMs: Long,
    val totalPlays: Long,
    val photoUrl: String = "",
    val bio: String = "",
    val favoriteSounds: String = ""
)

object FirebaseCommunity {
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
            val updates = mutableMapOf<String, Any>("lastActiveAt" to FieldValue.serverTimestamp())
            if (!snapshot.contains("photoUrl")) updates["photoUrl"] = ""
            if (!snapshot.contains("bio")) updates["bio"] = ""
            if (!snapshot.contains("favoriteSounds")) updates["favoriteSounds"] = ""
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
