package com.whisper.player.shared.platform

import com.whisper.player.shared.model.AppSettings
import com.whisper.player.shared.model.PlaybackQueue
import com.whisper.player.shared.model.RepeatMode
import com.whisper.player.shared.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class PlaybackSnapshot(
    val queue: PlaybackQueue = PlaybackQueue(),
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val speed: Float = 1f,
    val currentTrack: Track? = null,
)

interface PlaybackController {
    val state: StateFlow<PlaybackSnapshot>
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setQueue(queue: PlaybackQueue, tracksById: Map<String, Track>)
    fun setRepeatMode(mode: RepeatMode)
    fun setShuffleEnabled(enabled: Boolean)
    fun setSpeed(speed: Float)
}

enum class AccessStatus { GRANTED, LIMITED, DENIED, NOT_REQUIRED }

data class MediaAccessResult(
    val status: AccessStatus,
    val explanation: String? = null,
)

interface LocalMediaLibrary {
    val tracks: Flow<List<Track>>
    suspend fun requestAccess(): MediaAccessResult
    suspend fun refresh()
    /** Opens the platform's supported document/media picker and imports user-selected files. */
    suspend fun importAudioFiles(): List<Track>
}

data class RecordingOptions(
    val title: String,
    val sampleRateHz: Int = 44_100,
    val channelCount: Int = 1,
)

enum class RecordingStatus { IDLE, RECORDING, PAUSED, FINALIZING, FAILED }

data class RecordingSnapshot(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val durationMs: Long = 0,
    val errorMessage: String? = null,
)

interface AudioRecorder {
    val state: StateFlow<RecordingSnapshot>
    suspend fun start(options: RecordingOptions)
    suspend fun pause()
    suspend fun resume()
    suspend fun stop(): Track?
}

data class MediaReference(
    /** Opaque URI/URL understood only by the platform adapter that created it. */
    val locator: String,
    val displayName: String,
    val mimeType: String? = null,
)

data class ConversionProgress(
    val fraction: Float,
    val stage: String,
    val errorMessage: String? = null,
) {
    init {
        require(fraction in 0f..1f)
    }
}

interface VideoToAudioConverter {
    val progress: StateFlow<ConversionProgress>
    suspend fun convertToMp3(source: MediaReference, outputName: String, bitrateKbps: Int): Track
}

data class MediaMetadataUpdate(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val year: Int? = null,
)

interface MediaFileManager {
    suspend fun rename(track: Track, newName: String)
    suspend fun delete(track: Track)
    suspend fun updateMetadata(track: Track, update: MediaMetadataUpdate)
}

data class PlatformCapabilities(
    val backgroundAudio: Boolean,
    val unrestrictedDeviceLibraryScan: Boolean,
    val systemEqualizer: Boolean,
    val manualAudioOutputSelection: Boolean,
    val backgroundConversion: Boolean,
    val editableImportedFileMetadata: Boolean,
    val pushNotifications: Boolean,
)

interface SettingsStore {
    val settings: StateFlow<AppSettings>
    suspend fun update(settings: AppSettings)
}

/** Portable persistence operations for playlists, favorites, history and user preferences. */
interface LibraryStateStore {
    val playlists: Flow<List<com.whisper.player.shared.model.Playlist>>
    suspend fun savePlaylist(playlist: com.whisper.player.shared.model.Playlist)
    suspend fun deletePlaylist(playlistId: String)
    suspend fun setFavorite(trackId: String, favorite: Boolean)
    suspend fun isFavorite(trackId: String): Boolean
    suspend fun recordPlayback(trackId: String, playedAtEpochMillis: Long)
}

/** Authentication identity shared by the Android and Apple client adapters. */
data class UserIdentity(
    val userId: String,
    val displayName: String,
    val email: String? = null,
    val photoUrl: String? = null,
)

interface AccountService {
    val currentUser: StateFlow<UserIdentity?>
    suspend fun signInWithEmail(email: String, password: String): UserIdentity
    suspend fun signOut()
}

/** Social/community operations are shared; platform SDKs only transport these requests. */
interface CommunityService {
    val feed: Flow<List<com.whisper.player.shared.model.CommunityPost>>
    val incomingFriendRequests: Flow<List<com.whisper.player.shared.model.FriendRequest>>
    val outgoingFriendRequests: Flow<List<com.whisper.player.shared.model.FriendRequest>>
    val articles: Flow<List<com.whisper.player.shared.model.CommunityArticle>>
    val friends: Flow<List<com.whisper.player.shared.model.CommunityProfile>>

    suspend fun searchProfiles(query: String): List<com.whisper.player.shared.model.CommunityProfile>
    suspend fun updateProfile(profile: com.whisper.player.shared.model.CommunityProfile)
    suspend fun followUser(userId: String)
    suspend fun unfollowUser(userId: String)
    suspend fun sendFriendRequest(userId: String)
    suspend fun respondToFriendRequest(requestId: String, accept: Boolean)
    suspend fun removeFriend(userId: String)
    suspend fun publishPost(post: com.whisper.player.shared.model.CommunityPost)
    suspend fun deletePost(postId: String)
    suspend fun addPostComment(comment: com.whisper.player.shared.model.CommunityComment)
    suspend fun saveArticle(article: com.whisper.player.shared.model.CommunityArticle)
    suspend fun addArticleComment(comment: com.whisper.player.shared.model.CommunityArticleComment)
    suspend fun deleteArticle(articleId: String)
    suspend fun sendMessage(conversationId: String, text: String)
    suspend fun markConversationRead(conversationId: String)
    suspend fun setConversationArchived(conversationId: String, archived: Boolean)
    suspend fun blockUser(userId: String)
    suspend fun unblockUser(userId: String)
}

data class UploadProgress(
    val bytesSent: Long,
    val totalBytes: Long?,
    val completed: Boolean = false,
    val errorMessage: String? = null,
)

interface MediaCloudUploader {
    val progress: StateFlow<UploadProgress>
    suspend fun upload(reference: MediaReference): String
}

data class NotificationRequest(
    val title: String,
    val body: String,
    val deepLink: String? = null,
)

interface LocalNotificationService {
    suspend fun requestPermission(): AccessStatus
    suspend fun notify(request: NotificationRequest)
}

/** Audio controls that are not supported by a platform must report that capability as false. */
interface AudioEffectsController {
    val equalizerAvailable: StateFlow<Boolean>
    suspend fun setEqualizerEnabled(enabled: Boolean)
    suspend fun setBassBoostStrength(strength: Int)
}
