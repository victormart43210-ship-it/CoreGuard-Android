package com.coldboar.coreguard.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.ui.theme.AtmosphereGold
import com.coldboar.coreguard.ui.theme.AtmosphereTeal
import com.coldboar.coreguard.ui.theme.BackgroundDeepBlack
import com.coldboar.coreguard.ui.theme.BackgroundInk
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.SurfacePewter
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = ElectricTeal,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText
        )
    }
}

@Composable
fun CoreGuardCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun PrimaryTealButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = ElectricTeal,
            contentColor = BackgroundDeepBlack,
            disabledContainerColor = ElectricTeal.copy(alpha = 0.35f),
            disabledContentColor = BackgroundDeepBlack.copy(alpha = 0.6f)
        )
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun PremiumGoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = RestrainedGold,
            contentColor = BackgroundDeepBlack
        )
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Animated full-bleed atmosphere + content column used by primary screens.
 * Soft dual washes and drifting motes — presence without dashboard clutter.
 */
@Composable
fun ScreenAtmosphere(
    modifier: Modifier = Modifier,
    accent: Color = ElectricTeal,
    content: @Composable ColumnScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "screenAtmosphere")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val motes = remember {
        List(16) {
            Triple(
                Random.nextFloat(),
                Random.nextFloat(),
                1.2f + Random.nextFloat() * 2.2f
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundDeepBlack)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        BackgroundInk,
                        BackgroundDeepBlack,
                        AtmosphereTeal.copy(alpha = 0.32f)
                    )
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.16f * pulse), Color.Transparent),
                    center = Offset(size.width * 0.18f, size.height * 0.1f),
                    radius = size.minDimension * 0.85f
                ),
                center = Offset(size.width * 0.18f, size.height * 0.1f),
                radius = size.minDimension * 0.85f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AtmosphereGold.copy(alpha = 0.2f), Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.06f),
                    radius = size.minDimension * 0.5f
                ),
                center = Offset(size.width * 0.9f, size.height * 0.06f),
                radius = size.minDimension * 0.5f
            )
            motes.forEachIndexed { index, (x, y, r) ->
                val yy = (y + drift * (0.2f + (index % 5) * 0.08f)) % 1.1f
                val xx = x + 0.01f * sin((drift + y) * Math.PI * 2).toFloat()
                val color = if (index % 3 == 0) RestrainedGold else accent
                drawCircle(
                    color = color.copy(alpha = 0.16f + 0.18f * pulse),
                    radius = r * density,
                    center = Offset(xx * size.width, yy * size.height)
                )
            }
            val sweepY = size.height * (0.22f + 0.015f * cos(drift * Math.PI * 2).toFloat())
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, accent.copy(alpha = 0.12f), Color.Transparent)
                ),
                start = Offset(0f, sweepY),
                end = Offset(size.width, sweepY),
                strokeWidth = 1.5f * density
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun NestedSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = SurfacePewter)
    ) {
        Column(modifier = Modifier.padding(12.dp), content = content)
    }
}

@Composable
fun SubScreenTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go back",
                tint = ElectricTeal
            )
        }
        ScreenHeader(
            title = title,
            subtitle = subtitle,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun EmptyStatePanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    CoreGuardCard(modifier = modifier, containerColor = SurfacePewter.copy(alpha = 0.92f)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = ElectricTeal)
        Spacer(modifier = Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MutedText)
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryTealButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun LoadingLine(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            color = ElectricTeal,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MutedText)
    }
}
