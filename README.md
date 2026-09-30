# Slack Adaptive Prototype (Circuit 0.39.0 + Material 3 Adaptive 1.1.0)

This standalone Android Studio / Gradle project demonstrates how **Slack's [Circuit](https://slackhq.github.io/circuit/)** (`v0.39.0`) integrates directly with **Jetpack Compose Material 3 Adaptive** (`androidx.compose.material3.adaptive` `v1.1.0`) to build a responsive 1-, 2-, and 3-pane Slack workspace without requiring Jetpack Navigation 3 (`androidx.navigation3`).

## Architecture Highlights (Pattern A1)

1. **Adaptive Outer Navigation (`NavigationSuiteScaffold`)**:
   - Automatically switches between a bottom `NavigationBar` on compact phones (`< 600dp`) and a vertical `NavigationRail` on foldables, tablets, and desktop windows (`>= 600dp`).
2. **Adaptive 1-, 2-, and 3-Pane Workspace (`NavigableListDetailPaneScaffold`)**:
   - **Compact (`< 600dp`)**: Single pane (`maxHorizontalPartitions = 1`). Navigating from `ChannelListScreen` $\rightarrow$ `ChatScreen` $\rightarrow$ `ThreadScreen` pushes onto the scaffold navigator's history; system Back pops `Thread` $\rightarrow$ `Chat` $\rightarrow$ `Channels`.
   - **Medium / Expanded (`600dp..1199dp`)**: Two panes side-by-side (`maxHorizontalPartitions = 2`). Displays `List` (`ChannelListScreen`) + `Detail` (`ChatScreen`), and reflows to `Detail` + `Extra` (`ThreadScreen`) when a message thread is opened.
   - **Large Desktop / Wide Tablet (`>= 1200dp`)**: Custom `calculateSlackPaneScaffoldDirective()` upgrades `maxHorizontalPartitions = 3`, displaying all three panes (**Channels + Chat + Thread**) simultaneously side-by-side.
3. **Zero Presenter Coupling (`CircuitContent` + `onNavEvent`)**:
   - `ChannelListPresenter`, `ChatPresenter`, and `ThreadPresenter` are standard, pure Circuit `Presenter` classes that have **zero dependencies** on `ListDetailPaneScaffold`.
   - `ChannelListPresenter` calls `navigator.goTo(ChatScreen(channelId))`.
   - `ChatPresenter` calls `navigator.goTo(ThreadScreen(channelId, messageId))` and `navigator.pop()`.
   - `WorkspaceUi` renders each pane via `CircuitContent(screen = ..., onNavEvent = ...)` and intercepts `NavEvent.GoTo` / `NavEvent.Pop` to drive `scaffoldNavigator.navigateTo(...)` and `scaffoldNavigator.navigateBack()`.
4. **Circuit 0.33.0 – 0.39.0 Capabilities Used**:
   - `ProvideRecordLifecycle(isActive = ...)` (`v0.33.1+`) wraps each pane so simultaneously visible panes remain active at the same time.
   - `rememberSaveableNavStack(root = WorkspaceScreen)` (`v0.33.0+`) manages root navigation state.
   - `ParcelableScreen` & `@Parcelize data class WorkspacePaneKey : Parcelable` (`v0.38.0+` where `Screen` no longer extends `Parcelable` by default) ensure Android's `SaveableStateRegistry` persists the active channel and thread across configuration changes and process death.
5. **User-Resizable Split Panes (`PaneExpansionState` + `VerticalDragHandle`)**:
   - Users can drag the vertical divider between panes to resize them between `28%`, `35%`, and `50%` width proportions.

## Project Structure

- `app/src/main/java/com/example/slackadaptive/`
  - `MainActivity.kt` — Wires `Circuit.Builder()`, `SlackRepository`, `rememberSaveableNavStack`, and `NavigableCircuitContent`.
  - `screens/SlackScreens.kt` — `WorkspacePaneKey`, `WorkspaceScreen`, `ChannelListScreen`, `ChatScreen`, and `ThreadScreen`.
  - `features/workspace/WorkspaceFeature.kt` — `WorkspacePresenter`, `WorkspaceUi`, and `calculateSlackPaneScaffoldDirective()`.
  - `features/channellist/ChannelListFeature.kt` — `ChannelListPresenter` & `ChannelListUi` (`List` pane).
  - `features/chat/ChatFeature.kt` — `ChatPresenter` & `ChatUi` (`Detail` pane).
  - `features/thread/ThreadFeature.kt` — `ThreadPresenter` & `ThreadUi` (`Extra` pane).
  - `data/SlackRepository.kt` & `data/SlackModels.kt` — Reactive `StateFlow` repository synchronizing messages, reactions, and thread replies across all visible panes in real time.
  - `ui/theme/SlackTheme.kt` — Slack-inspired Material 3 color scheme.

## Opening & Running in Android Studio

1. Open Android Studio and select **File $\rightarrow$ Open...**
2. Select this directory (`slack-adaptive-circuit-prototype`).
3. Run the `app` configuration on a **Resizable Emulator**, **Pixel Fold**, **Pixel Tablet**, or **Desktop AVD** and resize or fold/unfold the window to observe the live 1-, 2-, and 3-pane transitions.
