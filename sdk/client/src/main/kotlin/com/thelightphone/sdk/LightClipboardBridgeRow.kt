package com.thelightphone.sdk

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.content.ClipData
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.launch

/**
 * A tappable row that copies whatever's on the Android-side system clipboard
 * (read via Compose's [LocalClipboard], not a raw `Context.getSystemService`
 * call) up to LightOS's own clipboard via [setLightClipboard].
 *
 * Android apps and LightOS do not share a clipboard, so this is a manual,
 * one-shot bridge rather than a live sync. It only succeeds if the LightOS
 * server the tool is running against implements `SetClipboard` -
 * [LightServiceMethod.SetClipboard][com.thelightphone.sdk.shared.LightServiceMethod.SetClipboard] -
 * which may not be true of every LightOS build.
 */
@Composable
fun SendAndroidClipboardToLightOsRow(
    modifier: Modifier = Modifier,
    onResult: (success: Boolean, message: String) -> Unit = { _, _ -> },
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .lightClickable(
                onClick = {
                    scope.launch {
                        val text = clipboard.getClipEntry()?.clipData
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)?.text?.toString()

                        if (text.isNullOrEmpty()) {
                            onResult(false, "Clipboard is empty")
                            return@launch
                        }
                        val success = setLightClipboard(text)
                        onResult(
                            success,
                            if (success) "Sent to LightOS clipboard" else "LightOS didn't accept the clipboard",
                        )
                    }
                },
            )
            .padding(vertical = 12.dp),
    ) {
        LightText(text = "Send clipboard to LightOS", variant = LightTextVariant.Copy)
    }
}

/**
 * The inverse of [SendAndroidClipboardToLightOsRow]: pulls whatever's on
 * LightOS's clipboard via [getLightClipboard] and writes it to the
 * Android-side system clipboard so it's available to paste into Android apps.
 */
@Composable
fun PullLightOsClipboardToAndroidRow(
    modifier: Modifier = Modifier,
    onResult: (success: Boolean, message: String) -> Unit = { _, _ -> },
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .lightClickable(
                onClick = {
                    scope.launch {
                        val text = getLightClipboard()
                        if (text.isNullOrEmpty()) {
                            onResult(false, "LightOS clipboard is empty")
                            return@launch
                        }
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("LightOS clipboard", text)))
                        onResult(true, "Copied from LightOS clipboard")
                    }
                },
            )
            .padding(vertical = 12.dp),
    ) {
        LightText(text = "Get clipboard from LightOS", variant = LightTextVariant.Copy)
    }
}
