package com.example.slackadaptive.data

import androidx.compose.runtime.Immutable

@Immutable
data class Channel(
    val id: String,
    val name: String,
    val topic: String,
    val memberCount: Int,
    val unreadCount: Int = 0,
    val isStarred: Boolean = false,
    val isPrivate: Boolean = false,
)

@Immutable
data class DirectMessage(
    val id: String,
    val userName: String,
    val userRole: String,
    val avatarInitials: String,
    val isOnline: Boolean,
    val statusEmoji: String? = null,
    val unreadCount: Int = 0,
)

@Immutable
data class Reaction(
    val emoji: String,
    val count: Int,
    val userReacted: Boolean = false,
)

@Immutable
data class ThreadReply(
    val id: String,
    val parentMessageId: String,
    val authorName: String,
    val authorInitials: String,
    val timestamp: String,
    val content: String,
)

@Immutable
data class ChatMessage(
    val id: String,
    val channelId: String,
    val authorName: String,
    val authorRole: String,
    val authorInitials: String,
    val timestamp: String,
    val content: String,
    val reactions: List<Reaction> = emptyList(),
    val replies: List<ThreadReply> = emptyList(),
)
