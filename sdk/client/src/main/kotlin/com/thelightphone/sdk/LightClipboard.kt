package com.thelightphone.sdk

import android.util.Log
import com.thelightphone.sdk.shared.LightServiceMethod
import com.thelightphone.sdk.shared.error
import com.thelightphone.sdk.shared.getOrNull

private const val TAG = "LightClipboard"

/**
 * Reads LightOS's own clipboard. This is a separate store from Android's
 * [android.content.ClipboardManager] - the two are not synced automatically,
 * so a copy made in an Android app will not show up here and vice versa.
 *
 * Returns null on any error (including an older LightOS server that doesn't
 * yet implement this method) or if LightOS's clipboard is empty.
 */
suspend fun getLightClipboard(): String? {
    val result = callRemoteServiceMethod(LightServiceMethod.GetClipboard, Unit)
    result.error?.let {
        Log.e(TAG, "Error getting LightOS clipboard, code:${it.code}, message:${it.extra}")
        return null
    }
    return result.getOrNull()?.text
}

/**
 * Writes [text] to LightOS's own clipboard so LightOS-side apps can read it,
 * independent of Android's clipboard. Returns true on success.
 */
suspend fun setLightClipboard(text: String): Boolean {
    val result = callRemoteServiceMethod(
        LightServiceMethod.SetClipboard,
        LightServiceMethod.SetClipboard.Request(text),
    )
    result.error?.let {
        Log.e(TAG, "Error setting LightOS clipboard, code:${it.code}, message:${it.extra}")
        return false
    }
    return true
}
