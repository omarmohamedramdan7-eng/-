package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.datastore.AppSettings
import com.example.core.datastore.SplitTunnelMode
import com.example.domain.model.InstalledAppInfo
import com.example.ui.components.OmarCard
import com.example.ui.components.SkeletonCardList
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTunnelingScreen(
    settings: AppSettings,
    installedApps: List<InstalledAppInfo>,
    isLoadingApps: Boolean,
    onLoadApps: () -> Unit,
    onUpdateSplitTunneling: (SplitTunnelMode, Set<String>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        onLoadApps()
    }

    val filteredApps = remember(installedApps, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) installedApps else installedApps.filter {
            it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(strings.splitTunnelingTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 650.dp)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SplitTunnelMode.entries.forEach { mode ->
                        val label = when (mode) {
                            SplitTunnelMode.ALL_APPS -> if (strings.isArabic) "الكل" else "All Apps"
                            SplitTunnelMode.EXCLUDE_SELECTED -> if (strings.isArabic) "استثناء المحدد" else "Bypass Selected"
                            SplitTunnelMode.INCLUDE_SELECTED -> if (strings.isArabic) "تضمين المحدد" else "Only Selected"
                        }
                        FilterChip(
                            selected = settings.splitTunnelMode == mode,
                            onClick = { onUpdateSplitTunneling(mode, settings.splitTunnelPackages) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonEmerald.copy(alpha = 0.2f),
                                selectedLabelColor = NeonEmerald
                            )
                        )
                    }
                }

                if (settings.splitTunnelMode != SplitTunnelMode.ALL_APPS) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        label = { Text(if (strings.isArabic) "ابحث عن تطبيق" else "Search installed apps") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isLoadingApps) {
                        SkeletonCardList(count = 4)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                val checked = app.packageName in settings.splitTunnelPackages
                                OmarCard(
                                    onClick = {
                                        val next = if (checked) {
                                            settings.splitTunnelPackages - app.packageName
                                        } else {
                                            settings.splitTunnelPackages + app.packageName
                                        }
                                        onUpdateSplitTunneling(settings.splitTunnelMode, next)
                                    },
                                    borderColor = if (checked) NeonEmerald.copy(alpha = 0.55f) else ObsidianBorder
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = app.appName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = app.packageName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                        }
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { isChecked ->
                                                val next = if (isChecked) {
                                                    settings.splitTunnelPackages + app.packageName
                                                } else {
                                                    settings.splitTunnelPackages - app.packageName
                                                }
                                                onUpdateSplitTunneling(settings.splitTunnelMode, next)
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = NeonEmerald,
                                                checkmarkColor = ObsidianBlack
                                            )
                                        )
                                    }
                                }
                            }
                            item { Spacer(modifier = Modifier.height(24.dp)) }
                        }
                    }
                } else {
                    OmarCard(borderColor = ObsidianBorder) {
                        Text(
                            text = if (strings.isArabic) {
                                "جميع تطبيقات الجهاز تمر حاليًا عبر نفق WireGuard المشفر. اختر (استثناء المحدد) أو (تضمين المحدد) لتخصيص التطبيقات."
                            } else {
                                "All device traffic is routed through the WireGuard tunnel. Choose 'Bypass Selected' or 'Only Selected' to customize per-app routing."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
