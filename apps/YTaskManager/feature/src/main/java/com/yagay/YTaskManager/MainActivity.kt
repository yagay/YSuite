package com.yagay.YTaskManager

import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import com.yagay.YTaskManager.ui.TaskManagerApp
import com.yagay.yui.YComposeActivity

class MainActivity : YComposeActivity() {
    private val taskViewModel: MainViewModel by viewModels()

    @Composable
    override fun YContent() {
        TaskManagerApp(viewModel = taskViewModel)
    }

    override fun onResume() {
        super.onResume()
        // KernelSU/Root authorization may have changed while the system permission UI was open.
        taskViewModel.refresh()
    }
}
