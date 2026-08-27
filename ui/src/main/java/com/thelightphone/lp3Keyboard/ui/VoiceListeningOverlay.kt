package com.thelightphone.lp3Keyboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thelightphone.lp3Keyboard.ui.viewmodel.DictationState

@Composable
fun VoiceListeningOverlay(
    state: DictationState,
    modifier: Modifier = Modifier
) {
    val colors = LocalKeyboardColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.microphone_lp3),
            contentDescription = "Voice",
            tint = colors.foreground,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        val statusText = when (state) {
            is DictationState.Loading -> "Loading voice..."
            is DictationState.Listening -> state.partialText.ifBlank { "Listening..." }
            is DictationState.Error -> state.message
            else -> ""
        }
        Text(
            text = statusText,
            color = colors.foreground,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap ▾ when done",
            color = colors.foreground.copy(alpha = 0.5f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}
