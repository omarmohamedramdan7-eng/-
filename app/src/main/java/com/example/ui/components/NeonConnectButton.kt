package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.VpnConnectionState
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber

@Composable
fun NeonConnectButton(
    vpnState: VpnConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val isConnecting = vpnState is VpnConnectionState.Connecting || vpnState is VpnConnectionState.Reconnecting
    val isConnected = vpnState is VpnConnectionState.Connected
    val isError = vpnState is VpnConnectionState.Error

    val infiniteTransition = rememberInfiniteTransition(label = "powerRing")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate"
    )

    val ringColor by animateColorAsState(
        targetValue = when {
            isConnected -> NeonEmerald
            isConnecting -> WarningAmber
            isError -> ErrorCoral
            else -> TextMuted
        },
        animationSpec = tween(350),
        label = "ringColor"
    )

    val actionLabel = if (isConnected || isConnecting) strings.disconnect else strings.connect
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier.size(196.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(196.dp)) {
            val outerStroke = 3.dp.toPx()
            val glowAlpha = if (isConnected) pulse * 0.45f else if (isConnecting) 0.28f else 0.08f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ringColor.copy(alpha = glowAlpha), Color.Transparent)
                ),
                radius = size.minDimension / 2f
            )

            drawCircle(
                color = ringColor.copy(alpha = 0.25f),
                radius = (size.minDimension / 2f) - 16.dp.toPx(),
                style = Stroke(width = outerStroke)
            )

            if (isConnecting) {
                drawArc(
                    color = WarningAmber,
                    startAngle = rotationAngle,
                    sweepAngle = 105f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            } else if (isConnected) {
                drawCircle(
                    color = NeonEmerald.copy(alpha = 0.9f),
                    radius = (size.minDimension / 2f) - 16.dp.toPx(),
                    style = Stroke(width = 3.5.dp.toPx())
                )
            }
        }

        Box(
            modifier = Modifier
                .size(144.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            if (isConnected) NeonEmerald.copy(alpha = 0.16f) else ObsidianElevated,
                            ObsidianSurface
                        )
                    )
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, color = NeonEmerald),
                    role = Role.Button,
                    onClick = onClick
                )
                .semantics { contentDescription = actionLabel }
                .testTag("connect_button"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = null,
                    tint = when {
                        isConnected -> NeonEmerald
                        isConnecting -> WarningAmber
                        isError -> ErrorCoral
                        else -> TextSecondary
                    },
                    modifier = Modifier.size(46.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = actionLabel.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected) NeonEmerald else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
