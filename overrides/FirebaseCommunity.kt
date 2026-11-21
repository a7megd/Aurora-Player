package com.aurora.player.community

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

data class CommunityMember(val uid: String, val displayName: String, val totalListeningMs: Long, val totalPlays: Long)

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
                "totalListeningMs" to 0L, "totalPlays" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "lastActiveAt" to FieldValue.serverTimestamp()
            )).await()
        } else ref.update("lastActiveAt", FieldValue.serverTimestamp()).await()
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
                    totalPlays = doc.getLong("totalPlays") ?: 0L
                )
            }
}
