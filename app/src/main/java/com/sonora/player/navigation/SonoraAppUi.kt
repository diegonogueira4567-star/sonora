package com.sonora.player.navigation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRail
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sonora.player.domain.model.ThemeMode
import com.sonora.player.ui.LocalAppContainer
import com.sonora.player.ui.components.LocalTrackMenu
import com.sonora.player.ui.components.TrackMenuHost
import com.sonora.player.ui.components.TrackMenuState
import com.sonora.player.ui.home.HomeScreen
import com.sonora.player.ui.library.CollectionKind
import com.sonora.player.ui.library.CollectionScreen
import com.sonora.player.ui.library.LibraryScreen
import com.sonora.player.ui.library.LibraryViewModel
import com.sonora.player.ui.library.StatsScreen
import com.sonora.player.ui.permissions.AUDIO_PERMISSION
import com.sonora.player.ui.permissions.PermissionScreen
import com.sonora.player.ui.player.MiniPlayer
import com.sonora.player.ui.player.PlayerScreen
import com.sonora.player.ui.playlists.PlaylistScreen
import com.sonora.player.ui.search.SearchScreen
import com.sonora.player.ui.search.SearchViewModel
import com.sonora.player.ui.settings.EqualizerScreen
import com.sonora.player.ui.settings.FoldersScreen
import com.sonora.player.ui.settings.SettingsScreen
import com.sonora.player.ui.settings.SettingsViewModel
import com.sonora.player.ui.theme.SonoraTheme
import com.sonora.player.ui.theme.isDarkTheme
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private data class TabItem(val route: String, val label: String, val selected: androidx.compose.ui.graphics.vector.ImageVector, val unselected: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    TabItem(Routes.HOME, "Início", Icons.Rounded.Home, Icons.Outlined.Home),
    TabItem(Routes.SEARCH, "Buscar", Icons.Rounded.Search, Icons.Outlined.Search),
    TabItem(Routes.LIBRARY, "Biblioteca", Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic),
)

@Composable
fun SonoraAppUi(openPlayerRequests: StateFlow<Int>, externalUris: StateFlow<Uri?>, onExternalHandled: () -> Unit) {
    val libraryVm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
    val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val searchVm: SearchViewModel = viewModel(factory = SearchViewModel.Factory)
    val settings by libraryVm.settings.collectAsState()
    val s = settings
    if (s != null) LaunchedEffect(Unit) { com.sonora.player.PerfLog.mark("settings.loaded") }

    SonoraTheme(
        themeMode = s?.themeMode ?: ThemeMode.DARK,
        accent = Color(s?.accentColor ?: 0xFF1ED2A4),
        dynamicColor = s?.dynamicColor ?: false,
    ) {
        val dark = isDarkTheme(s?.themeMode ?: ThemeMode.DARK)
        val activity = LocalContext.current as? ComponentActivity
        LaunchedEffect(dark) {
            val transparent = android.graphics.Color.TRANSPARENT
            val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
            activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }
        // Surface provides the theme's content color to every Text/Icon below it.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          // Side system bars (3-button navigation in landscape) and camera cutouts never cover content.
          Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
            if (s != null) AppContent(libraryVm, settingsVm, searchVm, openPlayerRequests, externalUris, onExternalHandled)
          }
        }
    }
}

@Composable
private fun AppContent(
    libraryVm: LibraryViewModel,
    settingsVm: SettingsViewModel,
    searchVm: SearchViewModel,
    openPlayerRequests: StateFlow<Int>,
    externalUris: StateFlow<Uri?>,
    onExternalHandled: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val activity = context as Activity
    val playback = container.playbackController
    val playerState by playback.state.collectAsState()
    val settings by settingsVm.settings.collectAsState()
    val favorites by libraryVm.favoriteIds.collectAsState()
    val folders by libraryVm.folders.collectAsState()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val trackMenu = remember { TrackMenuState() }
    val scope = rememberCoroutineScope()
    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(libraryVm.hasAudioPermission()) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var permissionAsks by rememberSaveable { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permissionAsks++
        if (granted) {
            settingsVm.update { setOnboardingDone(true) }
            libraryVm.rescan()
        } else {
            permanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(activity, AUDIO_PERMISSION)
            libraryVm.message("Sem permissão, só as pastas que você escolher serão lidas.")
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            settingsVm.update { setOnboardingDone(true) }
            libraryVm.addFolder(uri.toString())
        }
    }
    val requestPermission: () -> Unit = {
        if (permanentlyDenied) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        } else {
            permissionLauncher.launch(AUDIO_PERMISSION)
        }
    }
    val pickFolder: () -> Unit = { runCatching { folderPicker.launch(null) }.onFailure { libraryVm.message("Seletor de pastas indisponível neste aparelho.") } }

    // Initial scan, and re-check permission when returning from system settings.
    LaunchedEffect(Unit) { libraryVm.ensureStartupScan() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = libraryVm.hasAudioPermission()
        if (now != hasPermission) {
            hasPermission = now
            libraryVm.rescan()
        }
    }
    val firstTracks by libraryVm.tracks.collectAsState()
    var reportedDrawn by remember { mutableStateOf(false) }
    LaunchedEffect(firstTracks != null) {
        // "Fully drawn" = library list available (measured with `am start -W` / logcat).
        if (firstTracks != null && !reportedDrawn) {
            reportedDrawn = true
            com.sonora.player.PerfLog.mark("library.firstEmission size=${firstTracks?.size}")
            activity.reportFullyDrawn()
        }
    }
    LaunchedEffect(Unit) { libraryVm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { playback.messages.collect { snackbar.showSnackbar(it) } }
    val openRequests by openPlayerRequests.collectAsState()
    LaunchedEffect(openRequests) { if (openRequests > 0) playerExpanded = true }
    val external by externalUris.collectAsState()
    LaunchedEffect(external, playerState.connected) {
        val uri = external ?: return@LaunchedEffect
        if (!playerState.connected) return@LaunchedEffect
        val known = container.libraryRepository.findByUri(uri.toString())
        if (known != null) playback.playTracks(listOf(known)) else playback.playExternal(uri, uri.lastPathSegment ?: "Arquivo de áudio")
        playerExpanded = true
        onExternalHandled()
    }

    if (!settings.onboardingDone && !hasPermission && folders.isEmpty()) {
        PermissionScreen(
            permanentlyDenied = permanentlyDenied,
            onGrant = requestPermission,
            onOpenSettings = requestPermission,
            onPickFolder = pickFolder,
            onSkip = { settingsVm.update { setOnboardingDone(true) } },
        )
        return
    }

    val navigate: (String) -> Unit = { route -> nav.navigate(route) { launchSingleTop = true } }
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val selectTab: (String) -> Unit = { route ->
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    CompositionLocalProviderTrackMenu(trackMenu) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        // Wide windows (landscape phones, tablets) use a side rail: vertical space is the scarce one.
        val wide = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Vertical),
                ) {
                    Spacer(Modifier.height(8.dp))
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { selectTab(tab.route) },
                            icon = { Icon(if (selected) tab.selected else tab.unselected, contentDescription = null) },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag("nav_${tab.route}"),
                        )
                    }
                }
            }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    Column(
                        Modifier.background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.12f to MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                                1f to MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ) {
                        AnimatedVisibility(visible = playerState.hasMedia, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            MiniPlayer(
                                state = playerState,
                                position = playback.position,
                                isFavorite = playerState.trackId?.let { it in favorites } == true,
                                swipeEnabled = settings.swipeMiniPlayer,
                                onExpand = { playerExpanded = true },
                                onPlayPause = playback::togglePlayPause,
                                onNext = playback::next,
                                onPrevious = playback::previous,
                                onToggleFavorite = { playerState.trackId?.let(libraryVm::toggleFavorite) },
                            )
                        }
                        if (!wide) {
                            NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
                                tabs.forEach { tab ->
                                    val selected = currentRoute == tab.route
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { selectTab(tab.route) },
                                        icon = { Icon(if (selected) tab.selected else tab.unselected, contentDescription = null) },
                                        label = { Text(tab.label) },
                                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
                                        modifier = Modifier.testTag("nav_${tab.route}"),
                                    )
                                }
                            }
                        } else {
                            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)))
                        }
                    }
                },
            ) { padding ->
                val content = PaddingValues(bottom = padding.calculateBottomPadding())
                val current = playerState.mediaId
                NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.fillMaxSize()) {
                    composable(Routes.HOME) {
                        HomeScreen(libraryVm, playback, navigate, requestPermission, pickFolder, content)
                    }
                    composable(Routes.SEARCH) { SearchScreen(searchVm, favorites, playback, current, navigate, content) }
                    composable(Routes.LIBRARY) { LibraryScreen(libraryVm, playback, current, navigate, pickFolder, content) }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(settingsVm, libraryVm, playback, navigate, onBack = { nav.popBackStack() }, onRequestPermission = requestPermission, contentPadding = content)
                    }
                    composable(Routes.FOLDERS) { FoldersScreen(libraryVm, onBack = { nav.popBackStack() }, contentPadding = content) }
                    composable(Routes.EQUALIZER) { EqualizerScreen(settingsVm, onBack = { nav.popBackStack() }, contentPadding = content) }
                    composable(Routes.STATS) { StatsScreen(libraryVm, playback, onBack = { nav.popBackStack() }, contentPadding = content) }
                    composable(Routes.FAVORITES) { CollectionScreen(CollectionKind.FAVORITES, "", libraryVm, playback, current, navigate, { nav.popBackStack() }, content) }
                    composable(Routes.HISTORY) { CollectionScreen(CollectionKind.HISTORY, "", libraryVm, playback, current, navigate, { nav.popBackStack() }, content) }
                    composable(Routes.RECENT) { CollectionScreen(CollectionKind.RECENT, "", libraryVm, playback, current, navigate, { nav.popBackStack() }, content) }
                    listOf(
                        Routes.ALBUM to CollectionKind.ALBUM,
                        Routes.ARTIST to CollectionKind.ARTIST,
                        Routes.GENRE to CollectionKind.GENRE,
                        Routes.FOLDER to CollectionKind.FOLDER,
                    ).forEach { (route, kind) ->
                        composable(route, arguments = listOf(navArgument("key") { type = NavType.StringType })) { entry ->
                            val key = Routes.decodeKey(entry.arguments?.getString("key"))
                            CollectionScreen(kind, key, libraryVm, playback, current, navigate, { nav.popBackStack() }, content)
                        }
                    }
                    composable(Routes.PLAYLIST, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        PlaylistScreen(entry.arguments?.getLong("id") ?: 0L, libraryVm, playback, current, onBack = { nav.popBackStack() }, contentPadding = content)
                    }
                }
            }

        }
        }
            AnimatedVisibility(
                visible = playerExpanded && playerState.hasMedia,
                enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
            ) {
                PlayerScreen(
                    state = playerState,
                    position = playback.position,
                    isFavorite = playerState.trackId?.let { it in favorites } == true,
                    swipeArtwork = settings.swipeArtwork,
                    swipeDownToClose = settings.swipeDownToClose,
                    playback = playback,
                    onToggleFavorite = { playerState.trackId?.let(libraryVm::toggleFavorite) },
                    onCollapse = { playerExpanded = false },
                    navigate = { route -> scope.launch { navigate(route) } },
                )
            }
            TrackMenuHost(trackMenu, libraryVm, playback, navigate = { route -> playerExpanded = false; navigate(route) })
      }
    }
}

@Composable
private fun CompositionLocalProviderTrackMenu(state: TrackMenuState, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalTrackMenu provides state, content = content)
}
