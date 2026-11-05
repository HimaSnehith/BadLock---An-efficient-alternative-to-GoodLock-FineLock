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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
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
            containerColor = OneUiDarkBg,
            titleContentColor = OneUiTextPrimary,
            textContentColor = OneUiTextSecondary,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.SystemUpdate,
                    contentDescription = null,
                    tint = OneUiBlue,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("Badlock Update Available", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("A new version of Badlock is ready to install!", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = OneUiAmberContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "Version: v${appUpdateInfo!!.latestVersion}",
                            color = OneUiAmber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (!appUpdateInfo!!.releaseNotes.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text("What's new:", style = MaterialTheme.typography.labelLarge, color = OneUiTextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            appUpdateInfo!!.releaseNotes!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = OneUiTextSecondary,
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
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OneUiBlue)
                ) {
                    Text("Download Update", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                    Text("Later", color = OneUiTextSecondary)
                }
            },
            shape = RoundedCornerShape(24.dp)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OneUiDarkBg)
    ) {
        when (val state = moduleState) {
            is ModuleState.Loading -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = {
                            Text(
                                "Badlock",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = OneUiTextPrimary
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = OneUiDarkBg,
                            titleContentColor = OneUiTextPrimary
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
                    topBar = {
                        Column(modifier = Modifier.background(OneUiDarkBg)) {
                            // Header Title & Subtitle
                            TopAppBar(
                                title = {
                                    Column {
                                        Text(
                                            "Badlock",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = OneUiTextPrimary
                                        )
                                        val subtitleText = if (searchQuery.isNotEmpty()) {
                                            "${searchResults.size} modules found"
                                        } else if (updatableModules.isNotEmpty()) {
                                            "${updatableModules.size} update${if (updatableModules.size > 1) "s" else ""} available"
                                        } else {
                                            "All GoodLock modules up to date"
                                        }
                                        Text(
                                            text = subtitleText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (updatableModules.isNotEmpty() && searchQuery.isEmpty()) OneUiAmber else OneUiTextSecondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = { viewModel.refreshData(force = true) }
                                    ) {
                                        Icon(
                                            Icons.Outlined.Refresh,
                                            contentDescription = "Refresh",
                                            tint = OneUiTextSecondary
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = OneUiDarkBg,
                                    titleContentColor = OneUiTextPrimary
                                )
                            )

                            // One UI Search Field
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.updateSearchQuery(it) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    placeholder = {
                                        Text(
                                            "Search GoodLock modules...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = OneUiTextSecondary.copy(alpha = 0.6f)
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Search,
                                            contentDescription = null,
                                            tint = OneUiTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                                Icon(
                                                    Icons.Outlined.Close,
                                                    contentDescription = "Clear search",
                                                    tint = OneUiTextSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = OneUiSlateLow,
                                        unfocusedContainerColor = OneUiSlateLow,
                                        focusedBorderColor = OneUiBlue,
                                        unfocusedBorderColor = OneUiBorder,
                                        cursorColor = OneUiBlue
                                    ),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = OneUiTextPrimary)
                                )
                            }

                            Spacer(Modifier.height(8.dp))
                        }
                    },
                    containerColor = OneUiDarkBg
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        if (searchQuery.isEmpty()) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 1,
                                verticalAlignment = Alignment.Top
                            ) { page ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                            val fraction = 1f - abs(pageOffset).coerceIn(0f, 1f)
                                            alpha = fraction
                                            scaleX = 0.97f + (0.03f * fraction)
                                            scaleY = 0.97f + (0.03f * fraction)
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

                            // Samsung One UI Floating Navigation Island
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 16.dp)
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
                        } else {
                            ModuleList(
                                modules = searchResults,
                                showEmptyMessage = true,
                                emptyTitle = "No Modules Found",
                                emptySubtitle = "We couldn't find any GoodLock modules matching '$searchQuery'.",
                                emptyIcon = Icons.Outlined.SearchOff,
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
                    }
                }
            }
        }
    }
}
