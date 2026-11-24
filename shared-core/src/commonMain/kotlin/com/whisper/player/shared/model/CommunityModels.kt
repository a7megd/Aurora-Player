package com.whisper.player.shared.model

/** Portable community contracts. Firebase document IDs and timestamps are mapped by platform adapters. */
enum class FriendRequestStatus { PENDING, ACCEPTED, REJECTED, CANCELLED }
enum class CommunityPostKind { POEM, QUOTE, POST }
enum class ArticleStatus { DRAFT, PUBLISHED, ARCHIVED }
enum class CommunityRelation { NONE, FOLLOWING, FRIENDS, BLOCKED }

data class CommunityProfile(
    val uid: String, val displayName: String, val photoUrl: String = "", val bio: String = "",
    val favoriteSounds: String = "", val totalListeningMs: Long = 0, val totalPlays: Long = 0,
) {
    init {
        require(uid.isNotBlank())
        require(displayName.trim().length in 2..30)
        require(photoUrl.isBlank() || (photoUrl.startsWith("https://") && photoUrl.length <= 500))
        require(bio.length <= 160 && favoriteSounds.length <= 500)
        require(totalListeningMs >= 0 && totalPlays >= 0)
    }
}

data class FriendRequest(
    val id: String, val senderUid: String, val recipientUid: String, val senderName: String,
    val senderPhotoUrl: String = "", val status: FriendRequestStatus = FriendRequestStatus.PENDING,
    val createdAtEpochMillis: Long = 0,
) {
    init {
        require(id.isNotBlank() && senderUid.isNotBlank() && recipientUid.isNotBlank())
        require(senderUid != recipientUid && senderName.isNotBlank())
        require(senderPhotoUrl.isBlank() || senderPhotoUrl.startsWith("https://"))
        require(createdAtEpochMillis >= 0)
    }
    fun respond(accept: Boolean): FriendRequest {
        require(status == FriendRequestStatus.PENDING) { "Only pending requests can be answered" }
        return copy(status = if (accept) FriendRequestStatus.ACCEPTED else FriendRequestStatus.REJECTED)
    }
}

data class CommunityPost(
    val id: String, val authorUid: String, val authorName: String, val kind: CommunityPostKind,
    val body: String, val createdAtEpochMillis: Long, val commentCount: Int = 0,
) {
    init {
        require(id.isNotBlank() && authorUid.isNotBlank() && authorName.isNotBlank())
        require(body.isNotBlank() && body.length <= 10000)
        require(createdAtEpochMillis >= 0 && commentCount >= 0)
    }
    fun canBeDeletedBy(uid: String): Boolean = uid == authorUid
}

data class CommunityComment(
    val id: String, val postId: String, val authorUid: String, val authorName: String,
    val body: String, val createdAtEpochMillis: Long,
) {
    init {
        require(id.isNotBlank() && postId.isNotBlank() && authorUid.isNotBlank())
        require(authorName.isNotBlank() && body.isNotBlank() && body.length <= 2000)
        require(createdAtEpochMillis >= 0)
    }
    fun canBeEditedBy(uid: String): Boolean = uid == authorUid
    fun canBeDeletedBy(uid: String): Boolean = uid == authorUid
}

data class CommunityArticle(
    val id: String, val authorUid: String, val authorName: String, val title: String,
    val subtitle: String = "", val body: String, val coverUrl: String = "",
    val status: ArticleStatus = ArticleStatus.DRAFT, val createdAtEpochMillis: Long = 0,
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
) {
    init {
        require(id.isNotBlank() && authorUid.isNotBlank() && authorName.isNotBlank())
        require(title.isNotBlank() && title.length <= 160)
        require(subtitle.length <= 300 && body.length <= 100000)
        require(coverUrl.isBlank() || coverUrl.startsWith("https://"))
        require(createdAtEpochMillis >= 0 && updatedAtEpochMillis >= createdAtEpochMillis)
    }
    fun publish(byUid: String): CommunityArticle {
        require(byUid == authorUid) { "Only the author can publish this article" }
        require(body.isNotBlank()) { "An article needs body text before publishing" }
        return copy(status = ArticleStatus.PUBLISHED)
    }
    fun saveDraft(byUid: String, newTitle: String, newSubtitle: String, newBody: String, newCoverUrl: String, now: Long): CommunityArticle {
        require(byUid == authorUid) { "Only the author can edit this article" }
        require(now >= createdAtEpochMillis)
        return copy(title = newTitle, subtitle = newSubtitle, body = newBody, coverUrl = newCoverUrl,
            status = ArticleStatus.DRAFT, updatedAtEpochMillis = now)
    }
    fun isVisibleTo(uid: String): Boolean = status == ArticleStatus.PUBLISHED || uid == authorUid
}

data class CommunityArticleComment(
    val id: String,
    val articleId: String,
    val authorUid: String,
    val authorName: String,
    val body: String = "",
    val audioUrl: String = "",
    val createdAtEpochMillis: Long = 0,
) {
    init {
        require(id.isNotBlank() && articleId.isNotBlank() && authorUid.isNotBlank())
        require(authorName.isNotBlank() && body.length <= 2000)
        require(audioUrl.isBlank() || audioUrl.startsWith("https://"))
        require(body.isNotBlank() || audioUrl.isNotBlank())
        require(createdAtEpochMillis >= 0)
    }
    fun canBeEditedBy(uid: String): Boolean = uid == authorUid
}

data class CommunityBlock(val ownerUid: String, val blockedUid: String, val createdAtEpochMillis: Long = 0) {
    init {
        require(ownerUid.isNotBlank() && blockedUid.isNotBlank() && ownerUid != blockedUid)
        require(createdAtEpochMillis >= 0)
    }
}

/** Shared policy helper; backend security rules remain authoritative. */
fun canStartDirectChat(firstUid: String, secondUid: String, areFriends: Boolean, eitherBlocked: Boolean): Boolean =
    firstUid.isNotBlank() && secondUid.isNotBlank() && firstUid != secondUid && areFriends && !eitherBlocked
