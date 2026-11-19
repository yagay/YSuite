package com.yagay.yui
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** State, accessibility, validation and localisation stay in upstream Material3. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun YUiDatePicker(state: DatePickerState, modifier: Modifier = Modifier) =
    DatePicker(state = state, modifier = modifier)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun YUiDateRangePicker(state: DateRangePickerState, modifier: Modifier = Modifier) =
    DateRangePicker(state = state, modifier = modifier)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun YUiDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    DatePickerDialog(
        onDismissRequest = onDismissRequest, confirmButton = confirmButton,
        modifier = modifier, dismissButton = dismissButton,
        shape = RoundedCornerShape(LocalYAppearance.current.dialogRadiusDp.dp),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun YUiTimePicker(state: TimePickerState, modifier: Modifier = Modifier) =
    TimePicker(state = state, modifier = modifier)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun YUiTimeInput(state: TimePickerState, modifier: Modifier = Modifier) =
    TimeInput(state = state, modifier = modifier)
