package com.dark.badlock.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.badlock.R
import com.dark.badlock.data.InstalledModule
import com.dark.badlock.logic.LaunchHelper
import com.dark.badlock.logic.UpdateChecker
import com.dark.badlock.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomIsland(
    tabs: List<String>,
    currentPage: Int,
    updatableCount: Int,
    onTabClick: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .width(340.dp)
            .height(64.dp),
        shape = RoundedCornerShape(32.dp),
        color = DarkSurface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        tonalElevation = 12.dp,
        shadowElevation = 20.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = currentPage == index
                val contentColor by animateColorAsState(
                    if (isSelected) PrimaryAccent else TextSecondary,
                    label = "contentColor"
                )
                val backgroundColor by animateColorAsState(
                    if (isSelected) PrimaryAccent.copy(alpha = 0.12f) else Color.Transparent,
                    label = "backgroundColor"
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.25f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "iconScale"
                )

                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(backgroundColor)
                        .clickable { onTabClick(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                    ) {
                        val icon = when (title) {
                            "Updates" -> Icons.Default.SystemUpdate
                            "Make up" -> Icons.Default.Palette
                            else -> Icons.Default.Style
                        }

                        if (title == "Updates" && updatableCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = PrimaryAccent,
                                        contentColor = Color.White,
                                        modifier = Modifier.offset(x = (-4).dp, y = 4.dp)
                                    ) { Text("$updatableCount", fontSize = 10.sp) }
                                }
                            ) {
                                Icon(icon, contentDescription = title, tint = contentColor, modifier = Modifier.size(22.dp))
                            }
                        } else {
                            Icon(icon, contentDescription = title, tint = contentColor, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModuleList(
    modules: List<InstalledModule>,
    showEmptyMessage: Boolean = false,
    emptyTitle: String = "All Clear!",
    emptySubtitle: String = "All your modules are up-to-date.",
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.DoneAll,
    onModuleClick: (InstalledModule) -> Unit,
    onModuleLongClick: (InstalledModule) -> Unit,
    onWebsiteClick: (String) -> Unit,
    onUpdateClick: (InstalledModule) -> Unit,
    onAppInfoClick: (String) -> Unit
) {
    if (modules.isEmpty() && showEmptyMessage) {
        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 100.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = RoundedCornerShape(30.dp),
                color = DarkSurface
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = emptyIcon, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(48.dp))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(emptyTitle, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(emptySubtitle, color = TextSecondary, textAlign = TextAlign.Center)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items = modules, key = { it.packageName }) { module ->
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
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                onClick = onModuleClick,
                onLongClick = onModuleLongClick
            ),
        color = DarkSurface.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = DarkBackground
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = module.iconResId?.let { painterResource(id = it) }
                            ?: painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "${module.name} icon",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = module.name,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                VersionInfo(module)
            }

            // Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!module.isInstalled) {
                    Button(
                        onClick = onWebsiteClick,
                        colors = ButtonDefaults.buttonColors(containerColor = GreenAccent, contentColor = Color.Black),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("Install", fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                } else {
                    if (module.isUpdateAvailable) {
                        Button(
                            onClick = onUpdateClick,
                            colors = ButtonDefaults.buttonColors(containerColor = UpdateYellow, contentColor = Color.Black),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Update", fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    IconButton(
                        onClick = onWebsiteClick,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkBackground.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "Website",
                            tint = TextSecondary.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    IconButton(
                        onClick = onAppInfoClick,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkBackground.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "App Info",
                            tint = TextSecondary.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VersionInfo(module: InstalledModule) {
    val versionText = if (module.isInstalled) "v${module.versionName ?: "N/A"}" else "Tap to install"
    Text(versionText, color = TextSecondary, fontSize = 12.sp, maxLines = 1)

    if (module.latestVersion != null && module.isInstalled) {
        val color = if (module.isUpdateAvailable) UpdateYellow else TextSecondary
        Text("Latest: v${module.latestVersion}", color = color.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1)
    }

    if (module.latestVersion == null && module.isInstalled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CloudOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text("Version check failed", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
fun ErrorScreen(errorMessage: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(40.dp),
            color = DarkSurface
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Default.WifiOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(56.dp))
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text("Connection Issue", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        Text(errorMessage, color = TextSecondary, textAlign = TextAlign.Center, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.height(56.dp).fillMaxWidth(0.7f)
        ) {
            Text("Try Again", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun ShimmerModuleList() {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
            .height(72.dp)
            .clip(RoundedCornerShape(24.dp)),
        color = DarkSurface.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
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
                Box(modifier = Modifier.width(140.dp).height(18.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                Spacer(Modifier.height(10.dp))
                Box(modifier = Modifier.width(90.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
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
        containerColor = DarkSurface,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 48.dp, top = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = DarkBackground
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = module.iconResId?.let { painterResource(id = it) }
                                ?: painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(module.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(module.packageName, fontSize = 12.sp, color = TextSecondary)
                }
            }

            Spacer(Modifier.height(32.dp))

            // Info Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoItem(
                    label = "Installed",
                    value = module.versionName ?: "Not installed",
                    icon = Icons.Default.Smartphone,
                    modifier = Modifier.weight(1f)
                )
                InfoItem(
                    label = "Latest",
                    value = module.latestVersion ?: "N/A",
                    icon = Icons.Default.Cloud,
                    modifier = Modifier.weight(1f),
                    highlight = module.isUpdateAvailable
                )
            }

            Spacer(Modifier.height(12.dp))
            InfoItem(
                label = "Device Architecture",
                value = UpdateChecker.getDeviceArchitecture(),
                icon = Icons.Default.Memory,
                modifier = Modifier.fillMaxWidth()
            )

            if (module.minAndroidVersion != null) {
                Spacer(Modifier.height(12.dp))
                InfoItem(
                    label = "Minimum Android",
                    value = "Android ${module.minAndroidVersion}+",
                    icon = Icons.Default.Android,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (module.isUpdateAvailable) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = UpdateYellow.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, UpdateYellow.copy(alpha = 0.2f))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, tint = UpdateYellow, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Tip: Pick the '${UpdateChecker.getDeviceArchitecture()}' variant on APKMirror for the best compatibility.",
                            fontSize = 12.sp,
                            color = UpdateYellow
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (module.isInstalled) {
                    Button(
                        onClick = onLaunch,
                        modifier = Modifier.weight(1.5f).height(56.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                    ) {
                        Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open", fontWeight = FontWeight.Bold)
                    }
                } else {
                    val installLabel = if (module.latestVariantUrl != null) "Fast Install" else "Install"
                    Button(
                        onClick = {
                            module.latestVariantUrl?.let { LaunchHelper.openUrl(context, it) } ?: onWebsite()
                        },
                        modifier = Modifier.weight(1.5f).height(56.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenAccent, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(installLabel, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onAppInfo,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                }

                val websiteLabel = if (module.isUpdateAvailable && module.latestVariantUrl != null) "Fast Download" else "Website"
                OutlinedButton(
                    onClick = {
                        val url = if (module.isUpdateAvailable) module.latestVariantUrl ?: module.latestVersionUrl ?: module.apkMirrorMainPage else module.apkMirrorMainPage
                        LaunchHelper.openUrl(context, url)
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Icon(
                        imageVector = if (websiteLabel.contains("Fast")) Icons.Default.FlashOn else Icons.Default.Public,
                        contentDescription = null, 
                        modifier = Modifier.size(20.dp)
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = DarkBackground.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, if (highlight) UpdateYellow.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlight) UpdateYellow else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(label, fontSize = 11.sp, color = TextSecondary)
            Text(
                value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (highlight) UpdateYellow else TextPrimary,
                maxLines = 1
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

    background(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.05f),
                Color.White.copy(alpha = 0.25f),
                Color.White.copy(alpha = 0.05f),
            ),
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        )
    ).onGloballyPositioned {
        size = it.size
    }
}
