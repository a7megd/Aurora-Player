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
