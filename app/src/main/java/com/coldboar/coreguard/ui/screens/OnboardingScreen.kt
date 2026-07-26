package com.coldboar.coreguard.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.ui.components.BrandSeal
import com.coldboar.coreguard.ui.components.PrimaryTealButton
import com.coldboar.coreguard.ui.components.ScreenAtmosphere
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold

private data class OnboardingPage(
    val title: String,
    val body: String
)

private val pages = listOf(
    OnboardingPage(
        title = "CoreGuard",
        body = "On-device privacy intelligence for people who need to communicate safely — journalists, activists, and anyone who values a private phone."
    ),
    OnboardingPage(
        title = "Checks stay local",
        body = "Nemesis scans and Guardian Score run on your device. We don’t sell data or show ads. Optional Premium only unlocks extras you choose."
    ),
    OnboardingPage(
        title = "Ready when you are",
        body = "Run a privacy check for spyware indicators, then arm Privacy Shield to block known surveillance domains over a private on-device VPN."
    )
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onRunFirstScan: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    val last = step >= pages.lastIndex

    ScreenAtmosphere(
        modifier = Modifier.fillMaxSize(),
        accent = if (last) RestrainedGold else ElectricTeal
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onFinished) {
                    Text("Skip", color = MutedText)
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    BrandSeal(
                        size = 168.dp,
                        color = if (last) RestrainedGold else ElectricTeal,
                        alpha = 0.28f
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))

                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(280))) togetherWith
                                (slideOutHorizontally(tween(280)) { -it / 4 } + fadeOut(tween(220)))
                        } else {
                            (slideInHorizontally(tween(320)) { -it / 3 } + fadeIn(tween(280))) togetherWith
                                (slideOutHorizontally(tween(280)) { it / 4 } + fadeOut(tween(220)))
                        }
                    },
                    label = "onboardingPage"
                ) { index ->
                    val page = pages[index]
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = page.title,
                            style = MaterialTheme.typography.displayLarge,
                            color = ElectricTeal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = page.body,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MutedText,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pages.indices.forEach { i ->
                        PageDot(active = i == step)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                if (last) {
                    PrimaryTealButton(
                        text = "Check my device",
                        onClick = onRunFirstScan
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onFinished) {
                        Text("Enter CoreGuard", color = ElectricTeal)
                    }
                } else {
                    PrimaryTealButton(
                        text = "Continue",
                        onClick = { step += 1 }
                    )
                }
            }
        }
    }
}

@Composable
private fun PageDot(active: Boolean) {
    Box(
        modifier = Modifier
            .height(4.dp)
            .width(if (active) 22.dp else 10.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (active) ElectricTeal else Color.White.copy(alpha = 0.2f))
    )
}
