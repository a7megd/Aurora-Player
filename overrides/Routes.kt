package com.aurora.player.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Article
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
    const val COMMUNITY = "community"
    const val WRITE_ARTICLE = "write_article"
    const val ARTICLES = "articles"
    const val FRIEND_REQUESTS = "friend_requests"
    const val POSTS = "posts"
    const val CHAT = "chat"
}

data class BottomTab(val route: String, val label: String, val icon: ImageVector)

val bottomTabs = listOf(
    BottomTab(Routes.HOME, "الرئيسية", Icons.Rounded.Home),
    BottomTab(Routes.SONGS, "الأغاني", Icons.Rounded.MusicNote),
    BottomTab(Routes.WRITE_ARTICLE, "اكتب مقالًا", Icons.Rounded.Edit),
    BottomTab(Routes.ARTICLES, "المقالات", Icons.Rounded.Article),
    BottomTab(Routes.POSTS, "المنشورات", Icons.Rounded.Groups),
    BottomTab(Routes.COMMUNITY, "المجتمع", Icons.Rounded.Groups),
)
