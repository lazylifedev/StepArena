package com.lazyapps.steparena.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import com.lazyapps.steparena.R

object OnboardingTestTags {
    const val SCREEN = "onboarding_screen"
    const val NEXT = "onboarding_next"
    const val BACK = "onboarding_back"
}

data class OnboardingPage(
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
    val icon: ImageVector,
)

val onboardingPages = listOf(
    OnboardingPage(R.string.onboarding_title_1, R.string.onboarding_body_1, Icons.AutoMirrored.Filled.DirectionsWalk),
    OnboardingPage(R.string.onboarding_title_2, R.string.onboarding_body_2, Icons.Default.Sensors),
    OnboardingPage(R.string.onboarding_title_3, R.string.onboarding_body_3, Icons.Default.Lock),
    OnboardingPage(R.string.onboarding_title_4, R.string.onboarding_body_4, Icons.Default.EmojiEvents),
    OnboardingPage(R.string.onboarding_title_5, R.string.onboarding_body_5, Icons.Default.CheckCircle),
)

@Composable
fun OnboardingScreen(
    step: Int,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onStartTracking: () -> Unit = onNext,
    onLater: () -> Unit = onNext,
) {
    val page = onboardingPages[step.coerceIn(onboardingPages.indices)]
    val progressDescription = stringResource(
        R.string.onboarding_progress_description,
        step + 1,
        onboardingPages.size,
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag(OnboardingTestTags.SCREEN),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            stringResource(R.string.onboarding_page_progress, step + 1, onboardingPages.size),
            color = MaterialTheme.colorScheme.secondary,
        )
        LinearProgressIndicator(
            progress = { (step + 1f) / onboardingPages.size },
            modifier = Modifier.fillMaxWidth().testTag("onboarding_progress").semantics {
                contentDescription = progressDescription
            },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier.padding(top = 16.dp).testTag("onboarding_icon"),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(page.titleRes), style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 16.dp).semantics { heading() })
            Text(stringResource(page.bodyRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 24.dp))
        }
        if (step == onboardingPages.lastIndex) {
            Button(onClick = onStartTracking, modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.NEXT)) {
                Text(stringResource(R.string.onboarding_start_tracking))
            }
            OutlinedButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_start_later))
            }
        } else {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.NEXT)) {
                Text(stringResource(R.string.onboarding_next))
            }
        }
        if (step > 0) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.BACK),
            ) { Text(stringResource(R.string.onboarding_back)) }
        }
    }
}
