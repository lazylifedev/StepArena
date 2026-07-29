package com.lazyapps.steparena.feature.placeholder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.designsystem.component.GlassSurface
import com.lazyapps.steparena.core.designsystem.theme.StepArenaColors
import com.lazyapps.steparena.core.designsystem.theme.StepArenaSpacing

@Composable
fun PlaceholderScreen(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(StepArenaSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.coming_soon_message),
                color = StepArenaColors.TextSecondary,
            )
        }
    }
}
