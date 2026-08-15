package com.dark.badlock.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dark.badlock.data.InstalledModule
import com.dark.badlock.data.ModuleState
import com.dark.badlock.logic.LaunchHelper
import com.dark.badlock.ui.BadlockViewModel
import com.dark.badlock.ui.components.*
import com.dark.badlock.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: BadlockViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val moduleState by viewModel.moduleState.collectAsState()
    val appUpdateInfo by viewModel.appUpdateInfo.collectAsState()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    var selectedModule by remember { mutableStateOf<InstalledModule?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // The ViewModel handles the initial load and check thresholds
                viewModel.refreshData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        val packageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d("BadlockAutoRefresh", "Package change detected, refreshing data...")
                viewModel.refreshData(force = true)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(packageReceiver, filter)
        onDispose {
            context.unregisterReceiver(packageReceiver)
        }
    }

    if (showUpdateDialog && appUpdateInfo != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = PrimaryAccent)
                    Spacer(Modifier.width(12.dp))
                    Text("Badlock Update", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text("A new version of Badlock is available!", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text("Version: v${appUpdateInfo!!.latestVersion}", color = UpdateYellow, fontWeight = FontWeight.Bold)
                    if (!appUpdateInfo!!.releaseNotes.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text("What's new:", fontSize = 12.sp, color = TextPrimary)
                        Text(
                            appUpdateInfo!!.releaseNotes!!,
                            fontSize = 11.sp,
                            maxLines = 5,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        LaunchHelper.openUrl(context, appUpdateInfo!!.downloadUrl)
                        viewModel.dismissUpdateDialog()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                ) {
                    Text("Go to GitHub", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                    Text("Later", color = TextSecondary)
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    selectedModule?.let { module ->
        ModuleDetailsSheet(
            module = module,
            onDismiss = { selectedModule = null },
            onLaunch = {
                LaunchHelper.launchModule(context, module)
                selectedModule = null
            },
            onWebsite = {
                LaunchHelper.openUrl(context, module.apkMirrorMainPage)
                selectedModule = null
            },
            onAppInfo = {
                LaunchHelper.openAppInfo(context, module.packageName)
                selectedModule = null
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        when (val state = moduleState) {
            is ModuleState.Loading -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Pre-scaffold loading state to maintain header consistency
                    LargeTopAppBar(
                        title = { Text("Badlock", fontWeight = FontWeight.ExtraBold) },
                        colors = TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = DarkBackground,
                            scrolledContainerColor = DarkBackground,
                            titleContentColor = TextPrimary
                        )
                    )
                    ShimmerModuleList()
                }
            }
            is ModuleState.Error -> {
                ErrorScreen(errorMessage = state.message, onRetry = { viewModel.refreshData(force = true) })
            }
            is ModuleState.Success -> {
                val updatableModules = remember(state.modules) {
                    state.modules.values.flatten().filter { it.isUpdateAvailable }
                }
                val tabs = listOf("Make up", "Life up", "Updates")
                val pagerState = rememberPagerState(pageCount = { tabs.size })

                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        Column(modifier = Modifier.background(DarkBackground)) {
                            LargeTopAppBar(
                                title = {
                                    Column {
                                        Text(
                                            "Badlock",
                                            style = MaterialTheme.typography.headlineLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = TextPrimary
                                        )
                                        if (scrollBehavior.state.collapsedFraction < 0.5f) {
                                            Text(
                                                text = if (searchQuery.isNotEmpty()) "${searchResults.size} results found"
                                                else if (updatableModules.isNotEmpty()) "${updatableModules.size} updates available"
                                                else "Your modules are up to date",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (updatableModules.isNotEmpty() && searchQuery.isEmpty()) UpdateYellow else TextSecondary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = { viewModel.refreshData(force = true) },
                                        enabled = true
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary)
                                    }
                                },
                                scrollBehavior = scrollBehavior,
                                colors = TopAppBarDefaults.largeTopAppBarColors(
                                    containerColor = DarkBackground,
                                    scrolledContainerColor = DarkBackground,
                                    titleContentColor = TextPrimary
                                )
                            )
                            
                            // Search bar integrated into the header area
                            Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.updateSearchQuery(it) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    placeholder = { Text("Search modules...", color = TextSecondary.copy(alpha = 0.5f), fontSize = 14.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                                Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = DarkSurface.copy(alpha = 0.5f),
                                        unfocusedContainerColor = DarkSurface.copy(alpha = 0.3f),
                                        focusedBorderColor = PrimaryAccent.copy(alpha = 0.5f),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.05f),
                                        cursorColor = PrimaryAccent
                                    ),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                                )
                            }
                        }
                    },
                    containerColor = DarkBackground
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        if (searchQuery.isEmpty()) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondBoundsPageCount = 1,
                                verticalAlignment = Alignment.Top
                            ) { page ->
                                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                val alpha = 1f - abs(pageOffset).coerceIn(0f, 1f)
                                val scale = 0.95f + (0.05f * (1f - abs(pageOffset).coerceIn(0f, 1f)))

                                Box(modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        this.alpha = alpha
                                        this.scaleX = scale
                                        this.scaleY = scale
                                    }
                                ) {
                                    val pageTitle = tabs[page]
                                    val modulesToShow = when (pageTitle) {
                                        "Updates" -> updatableModules
                                        else -> state.modules[pageTitle] ?: emptyList()
                                    }
                                    ModuleList(
                                        modules = modulesToShow,
                                        showEmptyMessage = (pageTitle == "Updates"),
                                        onModuleClick = { module ->
                                            if (module.isInstalled) LaunchHelper.launchModule(context, module)
                                            else LaunchHelper.openUrl(context, module.apkMirrorMainPage)
                                        },
                                        onModuleLongClick = { module ->
                                            selectedModule = module
                                        },
                                        onWebsiteClick = { url -> LaunchHelper.openUrl(context, url) },
                                        onUpdateClick = { module -> module.latestVersionUrl?.let { LaunchHelper.openUrl(context, it) } },
                                        onAppInfoClick = { packageName -> LaunchHelper.openAppInfo(context, packageName) }
                                    )
                                }
                            }
                        } else {
                            ModuleList(
                                modules = searchResults,
                                showEmptyMessage = true,
                                emptyTitle = "No Results",
                                emptySubtitle = "We couldn't find any modules matching your search.",
                                emptyIcon = Icons.Default.SearchOff,
                                onModuleClick = { module ->
                                    val targetTab = tabs.indexOf(module.category)
                                    viewModel.updateSearchQuery("")
                                    if (targetTab != -1) {
                                        coroutineScope.launch { pagerState.animateScrollToPage(targetTab) }
                                    }
                                    if (module.isInstalled) LaunchHelper.launchModule(context, module)
                                    else LaunchHelper.openUrl(context, module.apkMirrorMainPage)
                                },
                                onModuleLongClick = { module ->
                                    selectedModule = module
                                },
                                onWebsiteClick = { url -> LaunchHelper.openUrl(context, url) },
                                onUpdateClick = { module -> module.latestVersionUrl?.let { LaunchHelper.openUrl(context, it) } },
                                onAppInfoClick = { packageName -> LaunchHelper.openAppInfo(context, packageName) }
                            )
                        }

                        // Bottom Island Navigation (Overlay)
                        if (searchQuery.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 40.dp)
                            ) {
                                BottomIsland(
                                    tabs = tabs,
                                    currentPage = pagerState.currentPage,
                                    updatableCount = updatableModules.size,
                                    onTabClick = { index ->
                                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
