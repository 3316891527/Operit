package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio


import android.app.Application
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.rememberNavController
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar.LocalOpenMainChatSidebar
import com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar.LocalRunMainChatAction
import com.ai.assistance.operit.ui.main.LocalTopBarActions
import com.ai.assistance.operit.ui.main.LocalTopBarTitleContent
import com.ai.assistance.operit.ui.main.NavigationTransitionSource
import com.ai.assistance.operit.ui.main.TopBarTitleContent
import com.ai.assistance.operit.ui.main.components.AppContent
import com.ai.assistance.operit.ui.main.navigation.AppRouterState
import com.ai.assistance.operit.ui.main.navigation.LocalRouteBackGuardRegistry
import com.ai.assistance.operit.ui.main.navigation.RouteBackGuardRegistry
import com.ai.assistance.operit.ui.main.navigation.screenKeysAliveOnStack
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.ui.main.screens.ScreenRouteRegistry
import com.ai.assistance.operit.ui.theme.LocalIsThemePagePreview
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import java.util.UUID
import kotlinx.coroutines.launch

/** 复用完整 AppContent；路由与页面 VM 独立，聊天内容只读取当前主会话。 */
@Composable
internal fun StudioLivePage(
    initialScreen: Screen,
    readOnly: Boolean = true,
    onNavigate: ((Screen) -> Boolean)? = null,
    onExit: () -> Unit = {},
) {
    key(initialScreen) {
        StudioLivePageContent(initialScreen, readOnly, onNavigate, onExit)
    }
}

@Composable
private fun StudioLivePageContent(
    initialScreen: Screen,
    readOnly: Boolean,
    onNavigate: ((Screen) -> Boolean)?,
    onExit: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as Application
    val parentLifecycle = LocalLifecycleOwner.current.lifecycle
    val owner = remember(initialScreen) { StudioPagePreviewOwner(application) }
    val screenKeyPrefix = remember { "theme-preview:${UUID.randomUUID()}:" }
    val navigate by rememberUpdatedState(onNavigate)
    val exit by rememberUpdatedState(onExit)
    val router = remember(initialScreen) { AppRouterState(ScreenRouteRegistry.toEntry(initialScreen)) }
    val scope = rememberCoroutineScope()
    val guards = remember(initialScreen) { RouteBackGuardRegistry() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val tablet = LocalConfiguration.current.screenWidthDp >= 600
    val entry = router.currentEntry
    var actions by remember(entry.instanceId) { mutableStateOf<@Composable RowScope.() -> Unit>({}) }
    var title by remember(entry.instanceId) { mutableStateOf<TopBarTitleContent?>(null) }
    val screen = requireNotNull(ScreenRouteRegistry.screenFromEntry(entry))
    var error by remember(entry.instanceId) { mutableStateOf<String?>(null) }
    val readOnlyLabel = stringResource(R.string.studio_live_page_read_only)
    DisposableEffect(owner, parentLifecycle) {
        val observer = LifecycleEventObserver { _, _ -> owner.moveTo(parentLifecycle.currentState) }
        parentLifecycle.addObserver(observer)
        owner.moveTo(parentLifecycle.currentState)
        onDispose {
            parentLifecycle.removeObserver(observer)
            owner.close()
        }
    }
    suspend fun canLeaveCurrentPage(): Boolean {
        val current = router.currentEntry
        val currentScreen = requireNotNull(ScreenRouteRegistry.screenFromEntry(current))
        return guards.canLeaveRoute(screenKeyPrefix + currentScreen.screenKey(current.instanceId))
    }
    val readOnlyModifier = if (readOnly) Modifier.clearAndSetSemantics { contentDescription = readOnlyLabel }
        .focusProperties { canFocus = false }.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        } else Modifier
    Box(Modifier.fillMaxSize().then(readOnlyModifier).consumeWindowInsets(WindowInsets.systemBars)) {
        CompositionLocalProvider(
            LocalIsThemePagePreview provides true,
            LocalSaveableStateRegistry provides null,
            LocalRunMainChatAction provides { _ -> },
            LocalOpenMainChatSidebar provides {},
            LocalViewModelStoreOwner provides owner,
            LocalLifecycleOwner provides owner,
            LocalSavedStateRegistryOwner provides owner,
            LocalOnBackPressedDispatcherOwner provides owner,
            LocalRouteBackGuardRegistry provides guards,
            LocalTopBarActions provides { supplied: @Composable RowScope.() -> Unit -> actions = supplied },
            LocalTopBarTitleContent provides { supplied -> title = supplied },
        ) {
            val nav = rememberNavController()
            AppContent(
                currentRouteEntry = entry, currentScreen = screen, selectedItem = screen.navItem,
                useTabletLayout = tablet, isTabletSidebarExpanded = false, isLoading = false,
                navController = nav, scope = scope, drawerState = drawer, showFpsCounter = false,
                enableNavigationAnimation = false, navigationTransitionSource = NavigationTransitionSource.DEFAULT,
                onScreenChange = { next ->
                    if (!readOnly) scope.launch {
                        if (canLeaveCurrentPage() && navigate?.invoke(next) != true) {
                            val destination = ScreenRouteRegistry.toEntry(next)
                            router.navigate(destination.routeId, destination.args)
                        }
                    }
                },
                onToggleSidebar = {}, navigateToTokenConfig = {},
                onError = { error = it }, canGoBack = router.canPop,
                onGoBack = {
                    if (!readOnly) scope.launch {
                        if (canLeaveCurrentPage()) {
                            if (router.canPop) router.pop() else exit()
                        }
                    }
                },
                actions = actions, titleContent = title,
                aliveScreenKeys = screenKeysAliveOnStack(router.backStack, ScreenRouteRegistry::screenFromEntry),
                isThemePreview = true, screenKeyPrefix = screenKeyPrefix,
            )
        }
        error?.let { message ->
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
                Text(message, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
