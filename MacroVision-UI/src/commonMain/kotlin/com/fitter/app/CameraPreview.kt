package com.fitter.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun CameraPreview(
    modifier: Modifier = Modifier,
    onPhotoCaptured: (ByteArray) -> Unit,
    onCancel: () -> Unit
)
