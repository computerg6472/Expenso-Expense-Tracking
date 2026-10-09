package com.spendwise.app.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.spendwise.app.data.preferences.AppPreferences
import com.spendwise.app.ui.chat.AiChatModal
import com.spendwise.app.ui.components.ExpensoBottomBar
import com.spendwise.app.ui.components.ExpensoNavItem
import com.spendwise.app.ui.components.FloatingAiButton
import com.spendwise.app.ui.lock.AppLockViewModel
import com.spendwise.app.ui.lock.LockState
import com.spendwise.app.ui.navigation.Screen
import com.spendwise.app.ui.navigation.SpendWiseNavGraph
import com.spendwise.app.ui.screen.lock.LockScreen
import kotlinx.coroutines.launch

@Composable
fun SpendWiseApp(
    lockViewModel: AppLockViewModel,
    appPreferences: AppPreferences,
    deepLinkIntent: Intent?,
    startDestination: String = Screen.Dashboard.route,
    isOfflineNotice: Boolean = false,
    historyProgressPercent: Int = 100
) {
    val navController = rememberNavController()
    val lockState by lockViewModel.lockState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    val aiFloatingEnabled by appPreferences.aiFloatingButtonEnabled.collectAsStateWithLifecycle(initialValue = true)

    var isChatOpen by remember { mutableStateOf(false) }
    var chatInitialVoiceMode by remember { mutableStateOf(false) }
    var chatInitialQuery by remember { mutableStateOf<String?>(null) }

    if (lockState == LockState.INITIALIZING) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
        return
    }

    if (lockState == LockState.LOCKED) {
        LockScreen(onUnlocked = { lockViewModel.onUnlocked() })
        return
    }

    LaunchedEffect(lockState, deepLinkIntent) {
        if (lockState != LockState.LOCKED && deepLinkIntent != null) {
            navController.handleDeepLink(deepLinkIntent)
        }
    }

    val bottomNavItems = listOf(
        ExpensoNavItem("Home", Icons.Filled.Home, Icons.Outlined.Home, Screen.Dashboard.route),
        ExpensoNavItem("History", Icons.Filled.History, Icons.Outlined.History, Screen.History.route),
        ExpensoNavItem("Forecast", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, Screen.Forecast.route),
        ExpensoNavItem("Reports", Icons.Filled.BarChart, Icons.Outlined.BarChart, Screen.Reports.route),
        ExpensoNavItem("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, Screen.Settings.route)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    // Check if keyboard is open
    val isImeVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

    // Floating button visibility rules: hide on Add Expense, onboarding, chat modal, or when keyboard is open
    val isFloatingButtonVisible = aiFloatingEnabled &&
            !isChatOpen &&
            !isImeVisible &&
            currentRoute != Screen.AddExpense.route &&
            currentRoute != Screen.Onboarding.route &&
            currentRoute?.startsWith("settings_") != true

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBottomBar) {
                    ExpensoBottomBar(
                        items = bottomNavItems,
                        currentRoute = currentRoute,
                        onItemClick = { route ->
                            if (route == currentRoute) return@ExpensoBottomBar
                            if (route == Screen.Dashboard.route) {
                                val popped = navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                                if (!popped) {
                                    navController.navigate(Screen.Dashboard.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                                        launchSingleTop = true
                                    }
                                }
                            } else {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        onReselect = { route ->
                            if (route == Screen.Dashboard.route && currentRoute != Screen.Dashboard.route) {
                                navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                if (showBottomBar) {
                    val fabInteraction = remember { MutableInteractionSource() }
                    val fabPressed by fabInteraction.collectIsPressedAsState()
                    val fabScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (fabPressed) 0.90f else 1.0f,
                        animationSpec = androidx.compose.animation.core.tween(90),
                        label = "fab_press_scale"
                    )

                    FloatingActionButton(
                        onClick = { navController.navigate(Screen.AddExpense.route()) },
                        interactionSource = fabInteraction,
                        modifier = Modifier.graphicsLayer {
                            scaleX = fabScale
                            scaleY = fabScale
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Expense")
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isOfflineNotice) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Working offline. Changes will sync when connected.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                // History sync banner
                AnimatedVisibility(
                    visible = historyProgressPercent in 1..99,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Loading history... $historyProgressPercent%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            LinearProgressIndicator(
                                progress = { historyProgressPercent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    SpendWiseNavGraph(
                        navController = navController,
                        startDestination = startDestination,
                        onOpenAiChat = { isVoice, query ->
                            chatInitialVoiceMode = isVoice
                            chatInitialQuery = query
                            isChatOpen = true
                        }
                    )
                }
            }
        }

        // Round Floating AI Button - drawn above the content and bottom bar
        FloatingAiButton(
            visible = isFloatingButtonVisible,
            hasBottomBar = showBottomBar,
            onClick = {
                chatInitialVoiceMode = false
                chatInitialQuery = null
                isChatOpen = true
            },
            onHide = {
                coroutineScope.launch {
                    appPreferences.setAiFloatingButtonEnabled(false)
                }
            }
        )

        // Single Source of Truth AI Chat Screen
        if (isChatOpen) {
            AiChatModal(
                onDismissRequest = { isChatOpen = false },
                initialVoiceMode = chatInitialVoiceMode,
                initialQuery = chatInitialQuery
            )
        }
    }
}

