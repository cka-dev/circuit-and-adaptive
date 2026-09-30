package com.example.slackadaptive.data

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory reactive repository simulating Slack workspace state.
 *
 * Exposes [StateFlow]s directly so Circuit Presenters always have synchronous access
 * to the current channel, DM, and message state on the very first composition frame
 * (avoiding empty initial frames during single-pane or multi-pane transitions).
 */
class SlackRepository {

    private val _channels = MutableStateFlow(INITIAL_CHANNELS)
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _directMessages = MutableStateFlow(INITIAL_DMS)
    val directMessages: StateFlow<List<DirectMessage>> = _directMessages.asStateFlow()

    private val _messagesByChannel = MutableStateFlow(INITIAL_MESSAGES)
    val messagesByChannel: StateFlow<Map<String, List<ChatMessage>>> = _messagesByChannel.asStateFlow()

    fun resolveChannel(
        channelId: String,
        channelList: List<Channel> = _channels.value,
        dmList: List<DirectMessage> = _directMessages.value,
    ): Channel? {
        return channelList.find { it.id == channelId }
            ?: dmList.find { it.id == channelId }?.let { dm ->
                Channel(
                    id = dm.id,
                    name = dm.userName,
                    topic = "${dm.userRole} ${dm.statusEmoji.orEmpty()}".trim(),
                    memberCount = 2,
                    unreadCount = dm.unreadCount,
                    isPrivate = true,
                )
            }
    }

    fun markChannelRead(channelId: String) {
        _channels.update { list ->
            list.map { ch -> if (ch.id == channelId) ch.copy(unreadCount = 0) else ch }
        }
        _directMessages.update { list ->
            list.map { dm -> if (dm.id == channelId) dm.copy(unreadCount = 0) else dm }
        }
    }

    fun sendMessage(channelId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val newMessage = ChatMessage(
            id = "msg_${System.currentTimeMillis()}",
            channelId = channelId,
            authorName = "You (Staff Android Engineer)",
            authorRole = "Android Architecture",
            authorInitials = "YO",
            timestamp = currentFormattedTime(),
            content = trimmed,
            reactions = emptyList(),
            replies = emptyList(),
        )

        _messagesByChannel.update { current ->
            val existing = current[channelId].orEmpty()
            current + (channelId to (existing + newMessage))
        }
    }

    fun sendThreadReply(channelId: String, parentMessageId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val newReply = ThreadReply(
            id = "reply_${System.currentTimeMillis()}",
            parentMessageId = parentMessageId,
            authorName = "You (Staff Android Engineer)",
            authorInitials = "YO",
            timestamp = currentFormattedTime(),
            content = trimmed,
        )

        _messagesByChannel.update { current ->
            val messages = current[channelId].orEmpty().map { msg ->
                if (msg.id == parentMessageId) {
                    msg.copy(replies = msg.replies + newReply)
                } else {
                    msg
                }
            }
            current + (channelId to messages)
        }
    }

    fun toggleReaction(channelId: String, messageId: String, emoji: String) {
        _messagesByChannel.update { current ->
            val messages = current[channelId].orEmpty().map { msg ->
                if (msg.id != messageId) return@map msg
                val existing = msg.reactions.find { it.emoji == emoji }
                val updatedReactions = if (existing == null) {
                    msg.reactions + Reaction(emoji = emoji, count = 1, userReacted = true)
                } else if (existing.userReacted) {
                    if (existing.count <= 1) {
                        msg.reactions.filterNot { it.emoji == emoji }
                    } else {
                        msg.reactions.map {
                            if (it.emoji == emoji) it.copy(count = it.count - 1, userReacted = false) else it
                        }
                    }
                } else {
                    msg.reactions.map {
                        if (it.emoji == emoji) it.copy(count = it.count + 1, userReacted = true) else it
                    }
                }
                msg.copy(reactions = updatedReactions)
            }
            current + (channelId to messages)
        }
    }

    private fun currentFormattedTime(): String {
        return try {
            LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a"))
        } catch (_: Exception) {
            "Just now"
        }
    }

    companion object {
        const val DEFAULT_CHANNEL_ID = "ch_android_adaptive"
        const val DEFAULT_THREAD_MESSAGE_ID = "msg_adaptive_1"

        private val INITIAL_CHANNELS = listOf(
            Channel(
                id = "ch_android_adaptive",
                name = "android-adaptive",
                topic = "Building Slack's 2-pane & 3-pane adaptive layouts with Circuit + Material 3 Adaptive",
                memberCount = 48,
                unreadCount = 0,
                isStarred = true,
            ),
            Channel(
                id = "ch_circuit_arch",
                name = "circuit-architecture",
                topic = "Circuit v0.39.0: Presenters, SaveableNavStack, ProvideRecordLifecycle & SubCircuit",
                memberCount = 112,
                unreadCount = 3,
                isStarred = true,
            ),
            Channel(
                id = "ch_design_systems",
                name = "design-systems",
                topic = "Material 3 Adaptive tokens, drag handles, and fold/hinge postures",
                memberCount = 84,
                unreadCount = 1,
                isStarred = false,
            ),
            Channel(
                id = "ch_general",
                name = "general",
                topic = "Company-wide announcements and engineering town halls",
                memberCount = 1420,
                unreadCount = 0,
                isStarred = false,
            ),
            Channel(
                id = "ch_release_ops",
                name = "release-ops-private",
                topic = "Foldable & Tablet rollout metrics",
                memberCount = 19,
                unreadCount = 0,
                isStarred = false,
                isPrivate = true,
            ),
        )

        private val INITIAL_DMS = listOf(
            DirectMessage(
                id = "dm_maya",
                userName = "Maya Lin",
                userRole = "Staff UI Toolkit Engineer",
                avatarInitials = "ML",
                isOnline = true,
                statusEmoji = "📱",
                unreadCount = 1,
            ),
            DirectMessage(
                id = "dm_alex",
                userName = "Alex Rivera",
                userRole = "Product Design Lead",
                avatarInitials = "AR",
                isOnline = true,
                statusEmoji = "🎨",
                unreadCount = 0,
            ),
            DirectMessage(
                id = "dm_sam",
                userName = "Sam Patel",
                userRole = "Circuit Maintainer",
                avatarInitials = "SP",
                isOnline = false,
                statusEmoji = "⚡",
                unreadCount = 0,
            ),
        )

        private val INITIAL_MESSAGES: Map<String, List<ChatMessage>> = mapOf(
            "ch_android_adaptive" to listOf(
                ChatMessage(
                    id = "msg_adaptive_1",
                    channelId = "ch_android_adaptive",
                    authorName = "Maya Lin",
                    authorRole = "Staff UI Toolkit Engineer",
                    authorInitials = "ML",
                    timestamp = "10:14 AM",
                    content = "Hey team! We just landed the prototype integrating Slack's Circuit with `NavigableListDetailPaneScaffold` and `NavigationSuiteScaffold`. Notice how `ChannelListPresenter`, `ChatPresenter`, and `ThreadPresenter` don't know anything about how many panes are visible!",
                    reactions = listOf(
                        Reaction("🚀", 7, userReacted = true),
                        Reaction("🎉", 4, userReacted = false),
                    ),
                    replies = listOf(
                        ThreadReply(
                            id = "reply_1",
                            parentMessageId = "msg_adaptive_1",
                            authorName = "Sam Patel",
                            authorInitials = "SP",
                            timestamp = "10:16 AM",
                            content = "Wrapping each pane in `ProvideRecordLifecycle` (added in Circuit 0.33.1) works like a charm—both the List and Detail panes stay active simultaneously on tablets.",
                        ),
                        ThreadReply(
                            id = "reply_2",
                            parentMessageId = "msg_adaptive_1",
                            authorName = "Alex Rivera",
                            authorInitials = "AR",
                            timestamp = "10:19 AM",
                            content = "And on desktop/ChromeOS windows >= 1200dp, upgrading `maxHorizontalPartitions = 3` lets this Thread pane sit right next to the Channel List and Chat!",
                        ),
                        ThreadReply(
                            id = "reply_3",
                            parentMessageId = "msg_adaptive_1",
                            authorName = "Maya Lin",
                            authorInitials = "ML",
                            timestamp = "10:22 AM",
                            content = "Try dragging the vertical handle between panes or resizing the emulator window—`PaneExpansionState` and `rememberListDetailPaneScaffoldNavigator` preserve everything seamlessly.",
                        ),
                    ),
                ),
                ChatMessage(
                    id = "msg_adaptive_2",
                    channelId = "ch_android_adaptive",
                    authorName = "Alex Rivera",
                    authorRole = "Product Design Lead",
                    authorInitials = "AR",
                    timestamp = "10:28 AM",
                    content = "I also tested folding and unfolding on a Pixel Fold emulator. Because `rememberListDetailPaneScaffoldNavigator<WorkspacePaneKey>()` owns the pane history, pressing Back when folded pops from Thread -> Chat -> Channel List automatically.",
                    reactions = listOf(
                        Reaction("💯", 5, userReacted = true),
                    ),
                    replies = listOf(
                        ThreadReply(
                            id = "reply_4",
                            parentMessageId = "msg_adaptive_2",
                            authorName = "Maya Lin",
                            authorInitials = "ML",
                            timestamp = "10:31 AM",
                            content = "Exactly! And we didn't have to pull in `androidx.navigation3` or replace Circuit's presenter architecture to get Material 3 Adaptive layouts.",
                        ),
                    ),
                ),
                ChatMessage(
                    id = "msg_adaptive_3",
                    channelId = "ch_android_adaptive",
                    authorName = "Sam Patel",
                    authorRole = "Circuit Maintainer",
                    authorInitials = "SP",
                    timestamp = "10:45 AM",
                    content = "Quick note for Circuit 0.38.0+: since `Screen` no longer extends `Parcelable` by default, our `WorkspacePaneKey` uses `@Parcelize : Parcelable` and our screens implement `ParcelableScreen` so Android's `SaveableStateRegistry` persists the active channel and thread across process death.",
                    reactions = listOf(
                        Reaction("🧠", 6, userReacted = false),
                        Reaction("👍", 3, userReacted = true),
                    ),
                    replies = emptyList(),
                ),
            ),
            "ch_circuit_arch" to listOf(
                ChatMessage(
                    id = "msg_circuit_1",
                    channelId = "ch_circuit_arch",
                    authorName = "Sam Patel",
                    authorRole = "Circuit Maintainer",
                    authorInitials = "SP",
                    timestamp = "9:30 AM",
                    content = "Circuit 0.39.0 is live! Highlights since late 2024:\n• `SaveableNavStack` with forward/backward history (0.33.0)\n• `ProvideRecordLifecycle` for multi-pane visibility (0.33.1)\n• `SubCircuit` & Composite Presenters (0.34.0)\n• First-party AndroidX `retain {}` integration by default (0.37.1)",
                    reactions = listOf(Reaction("🔥", 12, userReacted = true)),
                    replies = listOf(
                        ThreadReply(
                            id = "reply_circuit_1",
                            parentMessageId = "msg_circuit_1",
                            authorName = "Maya Lin",
                            authorInitials = "ML",
                            timestamp = "9:42 AM",
                            content = "First-party AndroidX `retain` support makes configuration changes and foldable posture transitions rock-solid.",
                        ),
                    ),
                ),
            ),
            "ch_design_systems" to listOf(
                ChatMessage(
                    id = "msg_design_1",
                    channelId = "ch_design_systems",
                    authorName = "Alex Rivera",
                    authorRole = "Product Design Lead",
                    authorInitials = "AR",
                    timestamp = "Yesterday",
                    content = "Here are our target breakpoints for Slack Adaptive:\n• Compact (< 600dp): Bottom NavigationBar + 1 pane\n• Medium (600–839dp): NavigationRail + 2 panes (List + Detail, Extra swaps in)\n• Expanded (840–1199dp): NavigationRail + 2 resizable panes\n• Large Desktop (>= 1200dp): NavigationRail + 3 simultaneous panes (List + Detail + Extra Thread)",
                    reactions = listOf(Reaction("✨", 8, userReacted = true)),
                    replies = emptyList(),
                ),
            ),
            "ch_general" to listOf(
                ChatMessage(
                    id = "msg_gen_1",
                    channelId = "ch_general",
                    authorName = "VP of Engineering",
                    authorRole = "Executive Sponsor",
                    authorInitials = "VP",
                    timestamp = "Monday",
                    content = "Welcome to the Q4 Large Screen & Adaptive UI sprint! Try clicking different channels on the left and opening message threads on the right.",
                    reactions = listOf(Reaction("🙌", 24, userReacted = false)),
                    replies = emptyList(),
                ),
            ),
            "ch_release_ops" to listOf(
                ChatMessage(
                    id = "msg_ops_1",
                    channelId = "ch_release_ops",
                    authorName = "Release Bot",
                    authorRole = "Automation",
                    authorInitials = "RB",
                    timestamp = "8:00 AM",
                    content = "Canary build 26.09.28 passed all foldable hinge & multi-window UI tests.",
                    reactions = listOf(Reaction("✅", 4, userReacted = true)),
                    replies = emptyList(),
                ),
            ),
            "dm_maya" to listOf(
                ChatMessage(
                    id = "msg_dm_maya_1",
                    channelId = "dm_maya",
                    authorName = "Maya Lin",
                    authorRole = "Staff UI Toolkit Engineer",
                    authorInitials = "ML",
                    timestamp = "11:02 AM",
                    content = "Hey! Let me know what you think of Pattern A1 (`CircuitContent` + `onNavEvent` inside `NavigableListDetailPaneScaffold`). Super clean separation between Circuit presenters and Material 3 Adaptive!",
                    reactions = listOf(Reaction("💜", 1, userReacted = true)),
                    replies = listOf(
                        ThreadReply(
                            id = "reply_dm_maya_1",
                            parentMessageId = "msg_dm_maya_1",
                            authorName = "Maya Lin",
                            authorInitials = "ML",
                            timestamp = "11:05 AM",
                            content = "Try unfolding and folding while viewing this DM or thread—the selection stays in sync across 1, 2, and 3 panes!",
                        ),
                    ),
                ),
            ),
            "dm_alex" to listOf(
                ChatMessage(
                    id = "msg_dm_alex_1",
                    channelId = "dm_alex",
                    authorName = "Alex Rivera",
                    authorRole = "Product Design Lead",
                    authorInitials = "AR",
                    timestamp = "Yesterday",
                    content = "Can we make sure the drag handle supports 35%/65% and 50%/50% anchors? Added that to `WorkspaceFeature.kt`!",
                    reactions = emptyList(),
                    replies = emptyList(),
                ),
            ),
            "dm_sam" to listOf(
                ChatMessage(
                    id = "msg_dm_sam_1",
                    channelId = "dm_sam",
                    authorName = "Sam Patel",
                    authorRole = "Circuit Maintainer",
                    authorInitials = "SP",
                    timestamp = "Tuesday",
                    content = "Feel free to ping me if you want to explore a `NavDecoration` wrapper around `ListDetailPaneScaffold` next.",
                    reactions = emptyList(),
                    replies = emptyList(),
                ),
            ),
        )
    }
}
