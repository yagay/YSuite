package com.yagay.yui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.view.View
import android.widget.PopupMenu

/**
 * One native transient-control entry point for Java/View and mixed Compose features.
 * Uses the caller's YUI-themed Activity context; the feature supplies only callbacks.
 */
object YViewTransientControls {
    @JvmStatic fun datePicker(
        context: Context, onDateSet: DatePickerDialog.OnDateSetListener,
        year: Int, month: Int, day: Int,
    ): DatePickerDialog = DatePickerDialog(context, onDateSet, year, month, day)

    @JvmStatic fun timePicker(
        context: Context, onTimeSet: TimePickerDialog.OnTimeSetListener,
        hour: Int, minute: Int, is24Hour: Boolean,
    ): TimePickerDialog = TimePickerDialog(context, onTimeSet, hour, minute, is24Hour)

    @JvmStatic fun popupMenu(context: Context, anchor: View): PopupMenu =
        PopupMenu(context, anchor)
}
