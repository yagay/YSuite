package com.yagay.yui

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Shared Material 3 dialog builder for legacy View screens (not system overlays). */
object YViewDialogs {
    @JvmStatic fun builder(context: Context): AlertDialog.Builder = MaterialAlertDialogBuilder(context)
}
