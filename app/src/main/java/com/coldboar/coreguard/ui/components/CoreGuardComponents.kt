package com.coldboar.coreguard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.ui.theme.BackgroundDeepBlack
import com.coldboar.coreguard.ui.theme.BackgroundDeepTeal
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.SurfacePewter

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
        Spacer(Modifier.height(6.dp))
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
    enabled: Boolean = true
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

@Composable
fun ScreenAtmosphere(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BackgroundDeepTeal.copy(alpha = 0.35f),
                        BackgroundDeepBlack,
                        BackgroundDeepBlack
                    )
                )
            )
            .padding(16.dp),
        content = content
    )
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
