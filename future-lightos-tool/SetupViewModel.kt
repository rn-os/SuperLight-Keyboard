package com.thelightphone.lp3keyboard

import com.thelightphone.sdk.LightViewModel

class SetupViewModel : LightViewModel<Unit>() {
    override fun onBackPressed(): Boolean {
        // Return false to allow the SDK to handle back navigation (exit tool)
        return false
    }
}
