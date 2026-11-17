package com.aurora.player.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val HOME = "home"
    const val SONGS = "songs"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val PLAYLISTS = "playlists"
    const val FOLDERS = "folders"
    const val FAVORITES = "favorites"
    const val RECENT = "recent"
    const val NOW_PLAYING = "now_playing"
    const val QUEUE = "queue"
    const val LYRICS = "lyrics"
    const val SEARCH = "search"
    const val EQUALIZER = "equalizer"
    const val SETTINGS = "settings"
}

data class BottomTab(val route: String, val label: String, val icon: ImageVector)

val bottomTabs = listOf(
    BottomTab(Routes.HOME, "Home", Icons.Rounded.Home),
    BottomTab(Routes.SONGS, "Songs", Icons.Rounded.MusicNote),
    BottomTab(Routes.ALBUMS, "Albums", Icons.Rounded.Album),
    BottomTab(Routes.ARTISTS, "Artists", Icons.Rounded.Person),
    BottomTab(Routes.PLAYLISTS, "Playlists", Icons.AutoMirrored.Rounded.QueueMusic),
)
