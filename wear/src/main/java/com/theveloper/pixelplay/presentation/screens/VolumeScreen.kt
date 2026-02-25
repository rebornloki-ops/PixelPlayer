package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import com.theveloper.pixelplay.presentation.components.WearTopTimeText
import com.theveloper.pixelplay.presentation.theme.LocalWearPalette
import com.theveloper.pixelplay.presentation.viewmodel.WearPlayerViewModel

/**
 * Simple volume control screen for Wear OS.
 * Provides volume up/down buttons. In the future, this can integrate with
 * Horologist's VolumeScreen for rotary crown support.
 */
@Composable
fun VolumeScreen(
    viewModel: WearPlayerViewModel = hiltViewModel(),
) {
    val palette = LocalWearPalette.current
    val background = Brush.radialGradient(
        colors = listOf(
            palette.gradientTop,
            palette.gradientMiddle,
            palette.gradientBottom,
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Volume",
                style = MaterialTheme.typography.title3,
                color = palette.textPrimary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { viewModel.volumeDown() },
                    modifier = Modifier.size(ButtonDefaults.LargeButtonSize),
                    colors = ButtonDefaults.secondaryButtonColors(
                        backgroundColor = palette.chipContainer,
                        contentColor = palette.chipContent,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                        contentDescription = "Volume down",
                        modifier = Modifier.size(28.dp),
                    )
                }

                Button(
                    onClick = { viewModel.volumeUp() },
                    modifier = Modifier.size(ButtonDefaults.LargeButtonSize),
                    colors = ButtonDefaults.secondaryButtonColors(
                        backgroundColor = palette.chipContainer,
                        contentColor = palette.chipContent,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume up",
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        WearTopTimeText(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(5f),
            color = palette.textPrimary,
        )
    }
}
