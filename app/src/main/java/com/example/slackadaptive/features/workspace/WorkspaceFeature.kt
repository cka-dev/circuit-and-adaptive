package com.example.slackadaptive.features.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.MutableThreePaneScaffoldState
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.slackadaptive.data.SlackRepository
import com.example.slackadaptive.screens.ChannelListScreen
import com.example.slackadaptive.screens.ChatScreen
import com.example.slackadaptive.screens.LocalWorkspacePaneState
import com.example.slackadaptive.screens.ThreadScreen
import com.example.slackadaptive.screens.TopLevelTab
import com.example.slackadaptive.screens.WorkspacePaneKey
import com.example.slackadaptive.screens.WorkspacePaneState
import com.example.slackadaptive.screens.WorkspaceScreen
import com.example.slackadaptive.ui.theme.SlackMentionBadge
import com.slack.circuit.foundation.CircuitContent
import com.slack.circuit.foundation.NavEvent
import com.slack.circuit.foundation.ProvideRecordLifecycle
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.presenter.Presenter
import kotlinx.coroutines.launch

/**
 * Circuit Presenter for the root [WorkspaceScreen].
 */
class WorkspacePresenter(
    private val repository: SlackRepository,
) : Presenter<WorkspaceScreen.State> {

    @Composable
    override fun present(): WorkspaceScreen.State {
        val selectedTabState = rememberRetained { mutableStateOf(TopLevelTab.HOME) }
        val showDebugBannerState = rememberRetained { mutableStateOf(true) }

        val channels by repository.channels.collectAsState()
        val dms by repository.directMessages.collectAsState()
        val totalUnread = channels.sumOf { it.unreadCount } + dms.sumOf { it.unreadCount }

        return WorkspaceScreen.State(
            selectedTab = selectedTabState.value,
            showAdaptiveDebugBanner = showDebugBannerState.value,
            totalUnreadCount = totalUnread,
        ) { event ->
            when (event) {
                is WorkspaceScreen.Event.SelectTab -> {
                    selectedTabState.value = event.tab
                }
                WorkspaceScreen.Event.ToggleAdaptiveDebugBanner -> {
                    showDebugBannerState.value = !showDebugBannerState.value
                }
            }
        }
    }
}

/**
 * Computes a [PaneScaffoldDirective] using [currentWindowAdaptiveInfoV2] and upgrades
 * `maxHorizontalPartitions` so that:
 *
 * - Compact (< 600dp): 1 pane (`maxHorizontalPartitions = 1`)
 * - Medium / Expanded (600dp..1199dp): 2 panes (`maxHorizontalPartitions = 2`)
 * - Large / Extra-Large (>= 1200dp): 3 panes (`maxHorizontalPartitions = 3`, showing
 *   `List` = Channels + `Detail` = Chat + `Extra` = Thread simultaneously)
 */
@Composable
fun calculateSlackPaneScaffoldDirective(
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfoV2(),
): PaneScaffoldDirective {
    val baseDirective = calculatePaneScaffoldDirective(windowAdaptiveInfo)
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return when {
        screenWidthDp >= 1200 -> baseDirective.copy(
            maxHorizontalPartitions = 3,
            horizontalPartitionSpacerSize = 1.dp,
        )
        screenWidthDp >= 600 -> baseDirective.copy(
            maxHorizontalPartitions = 2,
            horizontalPartitionSpacerSize = 1.dp,
        )
        else -> baseDirective.copy(
            maxHorizontalPartitions = 1,
            horizontalPartitionSpacerSize = 1.dp,
        )
    }
}

/**
 * Pattern A1 Implementation:
 *
 * 1. Outer `NavigationSuiteScaffold` switches between bottom `NavigationBar` (compact phone)
 *    and vertical `NavigationRail` (tablet/desktop/foldable).
 * 2. Inner `NavigableListDetailPaneScaffold` manages the 1-, 2-, or 3-pane adaptive layout
 *    and multi-pane back stack via `rememberListDetailPaneScaffoldNavigator<WorkspacePaneKey>()`.
 * 3. Each pane wraps its content in `ProvideRecordLifecycle` (added in Circuit 0.33.1) and renders
 *    its independent feature screen via `CircuitContent(screen = ..., onNavEvent = ...)`.
 * 4. `PaneExpansionState` + `VerticalDragHandle` allow users to drag-resize the split panes.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun WorkspaceUi(
    state: WorkspaceScreen.State,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val scaffoldDirective = calculateSlackPaneScaffoldDirective()
    val screenWidthDp = LocalConfiguration.current.screenWidthDp

    // Persist the most recently selected channel/DM and thread key across posture transitions.
    var selectedPaneKey by rememberSaveable {
        mutableStateOf(
            WorkspacePaneKey(
                channelId = SlackRepository.DEFAULT_CHANNEL_ID,
                threadMessageId = null,
            )
        )
    }

    val initialDestinations = remember {
        when {
            scaffoldDirective.maxHorizontalPartitions >= 3 -> listOf(
                ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List, null),
                ThreePaneScaffoldDestinationItem(
                    ListDetailPaneScaffoldRole.Detail,
                    WorkspacePaneKey(
                        channelId = SlackRepository.DEFAULT_CHANNEL_ID,
                        threadMessageId = SlackRepository.DEFAULT_THREAD_MESSAGE_ID,
                    ),
                ),
                ThreePaneScaffoldDestinationItem(
                    ListDetailPaneScaffoldRole.Extra,
                    WorkspacePaneKey(
                        channelId = SlackRepository.DEFAULT_CHANNEL_ID,
                        threadMessageId = SlackRepository.DEFAULT_THREAD_MESSAGE_ID,
                    ),
                ),
            )
            scaffoldDirective.maxHorizontalPartitions == 2 -> listOf(
                ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List, null),
                ThreePaneScaffoldDestinationItem(
                    ListDetailPaneScaffoldRole.Detail,
                    WorkspacePaneKey(channelId = SlackRepository.DEFAULT_CHANNEL_ID),
                ),
            )
            else -> listOf(
                ThreePaneScaffoldDestinationItem<WorkspacePaneKey>(
                    ListDetailPaneScaffoldRole.List,
                    null,
                ),
            )
        }
    }

    val scaffoldNavigator = rememberListDetailPaneScaffoldNavigator<WorkspacePaneKey>(
        scaffoldDirective = scaffoldDirective,
        initialDestinationHistory = initialDestinations,
    )

    // Keep selectedPaneKey updated whenever the navigator's currentDestination has a contentKey.
    val currentDestination = scaffoldNavigator.currentDestination
    val currentKey = currentDestination?.contentKey
    if (currentKey != null && currentKey != selectedPaneKey) {
        selectedPaneKey = currentKey
    }

    // Synchronize destination history and MutableThreePaneScaffoldState across
    // Compact (1-pane) <-> Large (2/3-pane) posture transitions:
    // 1. When unfolding to >= 2 panes while on the List destination, push Detail(selectedPaneKey)
    //    so both List and Detail panes show the selected conversation and folding back keeps it open.
    // 2. In Material 3 Adaptive 1.3.0, DefaultThreePaneScaffoldNavigator.setScaffoldDirective
    //    updates navigator.scaffoldValue (derivedStateOf) in-place without updating
    //    navigator.scaffoldState (MutableThreePaneScaffoldState). Explicitly snapping scaffoldState
    //    to scaffoldValue ensures ListDetailPaneScaffold immediately transitions between 1-pane
    //    and 2/3-pane layouts on fold/unfold.
    LaunchedEffect(scaffoldDirective) {
        if (
            scaffoldDirective.maxHorizontalPartitions >= 2 &&
            scaffoldNavigator.currentDestination?.pane == ListDetailPaneScaffoldRole.List
        ) {
            scaffoldNavigator.navigateTo(
                pane = ListDetailPaneScaffoldRole.Detail,
                contentKey = selectedPaneKey,
            )
        }
        val mutableScaffoldState = scaffoldNavigator.scaffoldState as? MutableThreePaneScaffoldState
        if (
            mutableScaffoldState != null &&
            mutableScaffoldState.targetState != scaffoldNavigator.scaffoldValue
        ) {
            mutableScaffoldState.snapTo(scaffoldNavigator.scaffoldValue)
        }
    }

    val paneExpansionState = rememberPaneExpansionState(
        keyProvider = scaffoldNavigator.scaffoldValue,
        anchors = listOf(
            PaneExpansionAnchor.Proportion(0.28f),
            PaneExpansionAnchor.Proportion(0.35f),
            PaneExpansionAnchor.Proportion(0.50f),
        ),
    )

    val activeChannelId = currentKey?.channelId ?: selectedPaneKey.channelId
    val activeThreadId = currentKey?.threadMessageId ?: selectedPaneKey.threadMessageId

    // IMPORTANT: In ListDetailPaneScaffoldRole:
    // - List   == ThreePaneScaffoldRole.Secondary
    // - Detail == ThreePaneScaffoldRole.Primary
    // - Extra  == ThreePaneScaffoldRole.Tertiary
    // Always index scaffoldValue by ListDetailPaneScaffoldRole rather than .primary / .secondary!
    val isListVisible =
        scaffoldNavigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded
    val isDetailVisible =
        scaffoldNavigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
    val isExtraVisible =
        scaffoldNavigator.scaffoldValue[ListDetailPaneScaffoldRole.Extra] == PaneAdaptedValue.Expanded

    val workspacePaneState = remember(
        activeChannelId,
        activeThreadId,
        isListVisible,
        isDetailVisible,
        isExtraVisible,
    ) {
        WorkspacePaneState(
            selectedChannelId = activeChannelId,
            activeThreadMessageId = activeThreadId,
            isListVisible = isListVisible,
            isDetailVisible = isDetailVisible,
            isExtraVisible = isExtraVisible,
        )
    }

    CompositionLocalProvider(LocalWorkspacePaneState provides workspacePaneState) {
        NavigationSuiteScaffold(
            modifier = modifier.fillMaxSize(),
            navigationSuiteItems = {
                TopLevelTab.entries.forEach { tab ->
                    item(
                        selected = state.selectedTab == tab,
                        onClick = { state.eventSink(WorkspaceScreen.Event.SelectTab(tab)) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (tab == TopLevelTab.HOME && state.totalUnreadCount > 0) {
                                        Badge(containerColor = SlackMentionBadge) {
                                            Text(state.totalUnreadCount.toString())
                                        }
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = when (tab) {
                                        TopLevelTab.HOME -> Icons.Default.Home
                                        TopLevelTab.DMS -> Icons.Default.ChatBubbleOutline
                                        TopLevelTab.ACTIVITY -> Icons.Default.NotificationsNone
                                        TopLevelTab.YOU -> Icons.Default.PersonOutline
                                    },
                                    contentDescription = tab.label,
                                )
                            }
                        },
                        label = { Text(tab.label) },
                    )
                }
            },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Live Adaptive Telemetry Banner so engineers can inspect pane state while resizing
                if (state.showAdaptiveDebugBanner) {
                    AdaptiveTelemetryBar(
                        screenWidthDp = screenWidthDp,
                        maxPartitions = scaffoldDirective.maxHorizontalPartitions,
                        isListVisible = isListVisible,
                        isDetailVisible = isDetailVisible,
                        isExtraVisible = isExtraVisible,
                        onToggleBanner = {
                            state.eventSink(WorkspaceScreen.Event.ToggleAdaptiveDebugBanner)
                        },
                    )
                }

                when (state.selectedTab) {
                    TopLevelTab.HOME, TopLevelTab.DMS -> {
                        NavigableListDetailPaneScaffold(
                            navigator = scaffoldNavigator,
                            paneExpansionState = paneExpansionState,
                            paneExpansionDragHandle = if (scaffoldDirective.maxHorizontalPartitions >= 2) {
                                { expansionState ->
                                    val interactionSource = remember { MutableInteractionSource() }
                                    VerticalDragHandle(
                                        modifier = Modifier.paneExpansionDraggable(
                                            state = expansionState,
                                            minTouchTargetSize = LocalMinimumInteractiveComponentSize.current,
                                            interactionSource = interactionSource,
                                        ),
                                        interactionSource = interactionSource,
                                    )
                                }
                            } else {
                                null
                            },
                            listPane = {
                                AnimatedPane {
                                    // ProvideRecordLifecycle (Circuit 0.33.1+) ensures simultaneously
                                    // visible panes remain active at the same time.
                                    ProvideRecordLifecycle(isActive = isListVisible) {
                                        val listScreen = remember(state.selectedTab) {
                                            ChannelListScreen(
                                                filterDmsOnly = state.selectedTab == TopLevelTab.DMS,
                                            )
                                        }
                                        val onListNavEvent: (NavEvent) -> Unit = remember(scaffoldNavigator) {
                                            { navEvent ->
                                                if (navEvent is NavEvent.GoTo && navEvent.screen is ChatScreen) {
                                                    val target = navEvent.screen as ChatScreen
                                                    val newKey = WorkspacePaneKey(
                                                        channelId = target.channelId,
                                                        threadMessageId = null,
                                                    )
                                                    selectedPaneKey = newKey
                                                    coroutineScope.launch {
                                                        scaffoldNavigator.navigateTo(
                                                            pane = ListDetailPaneScaffoldRole.Detail,
                                                            contentKey = newKey,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        CircuitContent(
                                            screen = listScreen,
                                            onNavEvent = onListNavEvent,
                                        )
                                    }
                                }
                            },
                            detailPane = {
                                AnimatedPane {
                                    ProvideRecordLifecycle(isActive = isDetailVisible) {
                                        val detailScreen = remember(activeChannelId) {
                                            ChatScreen(channelId = activeChannelId)
                                        }
                                        val onDetailNavEvent: (NavEvent) -> Unit = remember(scaffoldNavigator) {
                                            { navEvent ->
                                                when (navEvent) {
                                                    is NavEvent.GoTo -> {
                                                        val target = navEvent.screen
                                                        if (target is ThreadScreen) {
                                                            val newKey = WorkspacePaneKey(
                                                                channelId = target.channelId,
                                                                threadMessageId = target.parentMessageId,
                                                            )
                                                            selectedPaneKey = newKey
                                                            coroutineScope.launch {
                                                                scaffoldNavigator.navigateTo(
                                                                    pane = ListDetailPaneScaffoldRole.Extra,
                                                                    contentKey = newKey,
                                                                )
                                                            }
                                                        }
                                                    }
                                                    is NavEvent.Pop -> {
                                                        coroutineScope.launch {
                                                            scaffoldNavigator.navigateBack()
                                                        }
                                                    }
                                                    else -> Unit
                                                }
                                            }
                                        }
                                        CircuitContent(
                                            screen = detailScreen,
                                            onNavEvent = onDetailNavEvent,
                                        )
                                    }
                                }
                            },
                            extraPane = {
                                AnimatedPane {
                                    ProvideRecordLifecycle(isActive = isExtraVisible) {
                                        if (activeThreadId != null) {
                                            val extraScreen = remember(activeChannelId, activeThreadId) {
                                                ThreadScreen(
                                                    channelId = activeChannelId,
                                                    parentMessageId = activeThreadId,
                                                )
                                            }
                                            val onExtraNavEvent: (NavEvent) -> Unit = remember(scaffoldNavigator) {
                                                { navEvent ->
                                                    if (navEvent is NavEvent.Pop) {
                                                        selectedPaneKey = selectedPaneKey.copy(threadMessageId = null)
                                                        coroutineScope.launch {
                                                            scaffoldNavigator.navigateBack()
                                                        }
                                                    }
                                                }
                                            }
                                            CircuitContent(
                                                screen = extraScreen,
                                                onNavEvent = onExtraNavEvent,
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    TopLevelTab.ACTIVITY, TopLevelTab.YOU -> {
                        PlaceholderTabContent(
                            tab = state.selectedTab,
                            onReturnHome = {
                                state.eventSink(WorkspaceScreen.Event.SelectTab(TopLevelTab.HOME))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdaptiveTelemetryBar(
    screenWidthDp: Int,
    maxPartitions: Int,
    isListVisible: Boolean,
    isDetailVisible: Boolean,
    isExtraVisible: Boolean,
    onToggleBanner: () -> Unit,
) {
    val activePanes = buildList {
        if (isListVisible) add("List (Channels)")
        if (isDetailVisible) add("Detail (Chat)")
        if (isExtraVisible) add("Extra (Thread)")
    }.joinToString(" + ")

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "${screenWidthDp}dp · Max ${maxPartitions}P",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondary,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Visible Panes: $activePanes",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            IconButton(
                onClick = onToggleBanner,
                modifier = Modifier.height(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "Hide adaptive telemetry banner",
                )
            }
        }
    }
}

@Composable
private fun PlaceholderTabContent(
    tab: TopLevelTab,
    onReturnHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "${tab.label} Tab",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Switch back to Home or DMs to explore the adaptive 1-, 2-, and 3-pane ListDetailPaneScaffold powered by Slack Circuit.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
