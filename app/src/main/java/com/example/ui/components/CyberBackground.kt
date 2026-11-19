package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.domain.model.VpnConnectionState
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.WarningAmber

@Composable
fun CyberBackground(
    showSilhouette: Boolean,
    vpnState: VpnConnectionState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val glowColor by animateColorAsState(
        targetValue = when (vpnState) {
            is VpnConnectionState.Connected -> NeonEmerald.copy(alpha = 0.14f)
            is VpnConnectionState.Connecting,
            is VpnConnectionState.Reconnecting -> WarningAmber.copy(alpha = 0.10f)
            is VpnConnectionState.Error -> ErrorCoral.copy(alpha = 0.10f)
            is VpnConnectionState.Disconnected -> NeonEmerald.copy(alpha = 0.05f)
        },
        animationSpec = tween(500),
        label = "bgGlow"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        if (showSilhouette) {
            Image(
                painter = painterResource(id = R.drawable.img_cyber_guardian_bg),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.22f)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ObsidianBlack.copy(alpha = 0.72f),
                                ObsidianBlack.copy(alpha = 0.55f),
                                ObsidianBlack.copy(alpha = 0.88f)
                            )
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        radius = 900f
                    )
                )
        )

        content()
    }
}
