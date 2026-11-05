package com.dark.badlock.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.badlock.R
import com.dark.badlock.data.InstalledModule
import com.dark.badlock.logic.LaunchHelper
import com.dark.badlock.logic.UpdateChecker
import com.dark.badlock.ui.theme.*

// One UI Tactile Spring Bounce Modifier
fun Modifier.bounceClick(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounceScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                },
                onTap = { onClick() },
                onLongPress = { onLongClick?.invoke() }
            )
        }
}

// Samsung One UI Floating Bottom Navigation Dock
@Composable
fun BottomIsland(
    tabs: List<String>,
    currentPage: Int,
    updatableCount: Int,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth(0.92f)
            .height(64.dp),
        shape = RoundedCornerShape(32.dp),
        color = OneUiSlateLow.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, OneUiBorder),
        tonalElevation = 10.dp,
        shadowElevation = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, title ->
                BottomIslandTabItem(
                    title = title,
                    isSelected = currentPage == index,
                    updatableCount = updatableCount,
                    onClick = { onTabClick(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BottomIslandTabItem(
    title: String,
    isSelected: Boolean,
    updatableCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor by animateColorAsState(
        if (isSelected) OneUiBlueLight else OneUiTextSecondary,
        animationSpec = tween(200),
        label = "contentColor"
    )
    val backgroundColor by animateColorAsState(
        if (isSelected) OneUiBlueContainer.copy(alpha = 0.6f) else Color.Transparent,
        animationSpec = tween(200),
        label = "backgroundColor"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "iconScale"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .graphicsLayer(scaleX = iconScale, scaleY = iconScale)
        ) {
            val icon = when (title) {
                "Updates" -> Icons.Outlined.SystemUpdate
                "Make up" -> Icons.Outlined.Palette
                else -> Icons.Outlined.Widgets
            }

            if (title == "Updates" && updatableCount > 0) {
                BadgedBox(
                    badge = {
                        Badge(
                            containerColor = OneUiAmber,
                            contentColor = Color.Black
                        ) { Text("$updatableCount", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                ) {
                    Icon(icon, contentDescription = title, tint = contentColor, modifier = Modifier.size(20.dp))
                }
            } else {
                Icon(icon, contentDescription = title, tint = contentColor, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(6.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ModuleList(
    modules: List<InstalledModule>,
    showEmptyMessage: Boolean = false,
    emptyTitle: String = "All Clear!",
    emptySubtitle: String = "All your modules are up-to-date.",
    emptyIcon: ImageVector = Icons.Outlined.TaskAlt,
    onModuleClick: (InstalledModule) -> Unit,
    onModuleLongClick: (InstalledModule) -> Unit,
    onWebsiteClick: (String) -> Unit,
    onUpdateClick: (InstalledModule) -> Unit,
    onAppInfoClick: (String) -> Unit
) {
    if (modules.isEmpty() && showEmptyMessage) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = RoundedCornerShape(28.dp),
                color = OneUiSlateLow,
                border = BorderStroke(1.dp, OneUiBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = emptyIcon,
                        contentDescription = null,
                        tint = OneUiBlue,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                emptyTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = OneUiTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                emptySubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = OneUiTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(items = modules, key = { _, item -> item.packageName }) { index, module ->
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(module.packageName) {
                    visible = true
                }

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(durationMillis = 250, delayMillis = (index * 25).coerceAtMost(250))) +
                            slideInVertically(
                                initialOffsetY = { it / 4 },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                ) {
                    ModuleCard(
                        module = module,
                        onModuleClick = { onModuleClick(module) },
                        onModuleLongClick = { onModuleLongClick(module) },
                        onWebsiteClick = { onWebsiteClick(module.apkMirrorMainPage) },
                        onUpdateClick = { onUpdateClick(module) },
                        onAppInfoClick = { onAppInfoClick(module.packageName) }
                    )
                }
            }
        }
    }
}

// Samsung One UI 6 Module Card Tile
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModuleCard(
    module: InstalledModule,
    onModuleClick: () -> Unit,
    onModuleLongClick: () -> Unit,
    onWebsiteClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onAppInfoClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(
                onClick = onModuleClick,
                onLongClick = onModuleLongClick
            ),
        shape = RoundedCornerShape(20.dp),
        color = OneUiSlateLow,
        border = BorderStroke(1.dp, OneUiBorder),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // One UI Squircle Icon Box (14dp rounded corners)
            Surface(
                modifier = Modifier.size(50.dp),
                shape = RoundedCornerShape(14.dp),
                color = OneUiSlateMedium
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = module.iconResId?.let { painterResource(id = it) }
                            ?: painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = module.name,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            // Center Title & Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = module.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OneUiTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (module.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = module.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = OneUiTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(4.dp))

                VersionSubText(module = module)
            }

            Spacer(Modifier.width(8.dp))

            // One UI Action Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!module.isInstalled) {
                    Button(
                        onClick = onWebsiteClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OneUiGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Install", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (module.isUpdateAvailable) {
                    Button(
                        onClick = onUpdateClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OneUiAmber,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Outlined.SystemUpdate, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onModuleClick,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, OneUiBlue.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OneUiBlueLight),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Launch, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Open", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // 3-dot details menu button
                IconButton(
                    onClick = onModuleLongClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Details",
                        tint = OneUiTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun VersionSubText(module: InstalledModule) {
    if (module.isInstalled) {
        if (module.isUpdateAvailable && module.latestVersion != null) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = OneUiAmberContainer.copy(alpha = 0.4f),
                border = BorderStroke(0.5.dp, OneUiAmber.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "Update available: v${module.versionName ?: "N/A"} → v${module.latestVersion}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OneUiAmber,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        } else {
            Text(
                text = "v${module.versionName ?: "N/A"}",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = OneUiTextSecondary,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        Text(
            text = "Tap to install",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            color = OneUiTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ErrorScreen(errorMessage: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = RoundedCornerShape(28.dp),
            color = OneUiSlateLow,
            border = BorderStroke(1.dp, OneUiBorder)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.WifiOff,
                    contentDescription = null,
                    tint = OneUiBlue,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Connection Issue",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = OneUiTextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = OneUiTextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OneUiBlue),
            modifier = Modifier
                .height(48.dp)
                .fillMaxWidth(0.5f)
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Retry", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ShimmerModuleList() {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(8) {
            ShimmerModuleItem()
        }
    }
}

@Composable
fun ShimmerModuleItem() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp),
        shape = RoundedCornerShape(20.dp),
        color = OneUiSlateLow,
        border = BorderStroke(1.dp, OneUiBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .shimmerEffect()
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(190.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleDetailsSheet(
    module: InstalledModule,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onWebsite: () -> Unit,
    onAppInfo: () -> Unit
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = OneUiDarkBg,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = OneUiTextSecondary.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 36.dp, top = 4.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(60.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = OneUiSlateMedium,
                    border = BorderStroke(1.dp, OneUiBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = module.iconResId?.let { painterResource(id = it) }
                                ?: painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = module.name,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            module.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = OneUiTextPrimary
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text(module.category, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(8.dp),
                            colors = AssistChipDefaults.assistChipColors(containerColor = OneUiSlateMedium, labelColor = OneUiTextSecondary),
                            border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = OneUiBorder),
                            modifier = Modifier.height(24.dp)
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        module.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = OneUiTextSecondary
                    )
                }
            }

            if (module.description.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = OneUiSlateLow,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, OneUiBorder)
                ) {
                    Text(
                        text = module.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OneUiTextPrimary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Info Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoItem(
                    label = "Installed",
                    value = module.versionName ?: "Not installed",
                    icon = Icons.Outlined.Smartphone,
                    modifier = Modifier.weight(1f)
                )
                InfoItem(
                    label = "Latest Version",
                    value = module.latestVersion ?: "N/A",
                    icon = Icons.Outlined.Cloud,
                    modifier = Modifier.weight(1f),
                    highlight = module.isUpdateAvailable
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoItem(
                    label = "Architecture",
                    value = UpdateChecker.getDeviceArchitecture(),
                    icon = Icons.Outlined.Memory,
                    modifier = Modifier.weight(1f)
                )
                if (module.minAndroidVersion != null) {
                    InfoItem(
                        label = "Min Android",
                        value = "Android ${module.minAndroidVersion}+",
                        icon = Icons.Outlined.Android,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (module.isUpdateAvailable) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    color = OneUiAmber.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, OneUiAmber.copy(alpha = 0.3f))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = OneUiAmber, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Tip: Pick the '${UpdateChecker.getDeviceArchitecture()}' APK variant on APKMirror for best compatibility.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OneUiTextPrimary
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (module.isInstalled) {
                    Button(
                        onClick = onLaunch,
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OneUiBlue)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open App", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            module.latestVariantUrl?.let { LaunchHelper.openUrl(context, it) } ?: onWebsite()
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OneUiGreen, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (module.latestVariantUrl != null) "Fast Install" else "Install", fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onAppInfo,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, OneUiBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OneUiTextPrimary)
                ) {
                    Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                }

                OutlinedButton(
                    onClick = {
                        val url = if (module.isUpdateAvailable) module.latestVariantUrl ?: module.latestVersionUrl ?: module.apkMirrorMainPage else module.apkMirrorMainPage
                        LaunchHelper.openUrl(context, url)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, OneUiBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OneUiTextPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Public,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun InfoItem(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = OneUiSlateLow,
        border = BorderStroke(
            1.dp,
            if (highlight) OneUiAmber.copy(alpha = 0.5f) else OneUiBorder
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlight) OneUiAmber else OneUiBlue,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = OneUiTextSecondary)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlight) OneUiAmber else OneUiTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun Modifier.shimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing)
        ),
        label = "shimmerOffset"
    )

    val baseColor = OneUiTextPrimary.copy(alpha = 0.04f)
    val highlightColor = OneUiTextPrimary.copy(alpha = 0.15f)

    background(
        brush = Brush.linearGradient(
            colors = listOf(
                baseColor,
                highlightColor,
                baseColor,
            ),
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        )
    ).onGloballyPositioned {
        size = it.size
    }
}
