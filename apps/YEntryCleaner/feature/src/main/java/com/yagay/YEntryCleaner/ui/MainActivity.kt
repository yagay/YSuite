package com.yagay.YEntryCleaner.ui

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.YEntryCleaner.R
import com.yagay.YEntryCleaner.data.readBackupText
import com.yagay.yui.YActionSpec
import com.yagay.yui.YAppShell
import com.yagay.yui.YComposeActivity
import com.yagay.yui.YCustomScaffold
import com.yagay.yui.YFormDialog
import com.yagay.yui.YNavigationSpec
import com.yagay.yui.YPageRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
class MainActivity : YComposeActivity() {
    @Composable
    override fun YContent() {
        YEntryCleanerScreen()
    }

    @Composable
    private fun YEntryCleanerScreen(vm: MainViewModel = viewModel()) {
        val state by vm.state.collectAsStateWithLifecycle()
        var searchExpanded by rememberSaveable { mutableStateOf(false) }
        var restartPromptDismissed by rememberSaveable { mutableStateOf(false) }
        val collectingDiagnostics by vm.collectingDiagnostics.collectAsStateWithLifecycle()
        val exportMessage by vm.exportMessage.collectAsStateWithLifecycle()
        LaunchedEffect(exportMessage) {
            exportMessage?.let { toast(it, true); vm.clearExportMessage() }
        }
        LaunchedEffect(state.module.outdated) {
            if (!state.module.outdated) restartPromptDismissed = false
        }
        if (state.module.outdated && !restartPromptDismissed) {
            YFormDialog(
                title = stringResource(R.string.restart_required_title),
                onDismissRequest = { restartPromptDismissed = true },
                actions = listOf(
                    YActionSpec(
                        label = stringResource(R.string.common_ok),
                        onClick = { restartPromptDismissed = true },
                    ),
                    YActionSpec(
                        label = stringResource(R.string.restart_required_open_status),
                        onClick = {
                            restartPromptDismissed = true
                            vm.setDestination(Destination.DASHBOARD)
                        },
                    ),
                ),
            ) {
                androidx.compose.material3.Text(stringResource(R.string.restart_required_message))
            }
        }
        val keyboard = LocalSoftwareKeyboardController.current
        val closeSearch: () -> Unit = {
            vm.setQuery("")
            searchExpanded = false
            keyboard?.hide()
        }
        BackHandler(enabled = searchExpanded, onBack = closeSearch)
        LifecycleResumeEffect(vm) {
            vm.refreshModuleStatus()
            onPauseOrDispose { }
        }

        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val output = contentResolver.openOutputStream(uri) ?: error(getString(R.string.backup_create_failed))
                        output.bufferedWriter().use { it.write(vm.exportJson()) }
                    }
                    toast(getString(R.string.backup_exported))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.e(TAG, "Backup export failed", failure)
                    toast(getString(R.string.backup_export_failed), true)
                }
            }
        }
        val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        vm.importJson(readLimitedText(uri, MainViewModel.MAX_BACKUP_CHARS))
                    }
                    toast(getString(R.string.backup_restored))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.e(TAG, "Backup restore failed", failure)
                    toast(getString(R.string.backup_restore_failed), true)
                }
            }
        }
        val diagnosticExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            vm.exportDiagnostics(uri)
        }
        val fileCheck = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(vm::inspectFile)
        }

        val navigation = Destination.entries.map { destination ->
            YNavigationSpec(
                key = destination.name,
                label = stringResource(destination.labelRes()),
                icon = destination.icon,
                selectedIcon = destination.icon,
            )
        }

        YAppShell(
            selectedKey = state.destination.name,
            items = navigation,
            onSelected = { item ->
                Destination.entries.firstOrNull { it.name == item.key }?.let(vm::setDestination)
            },
        ) {
            YCustomScaffold(
                role = if (state.destination == Destination.DASHBOARD) YPageRole.DASHBOARD else YPageRole.MANAGER,
                topBar = {
                    MainToolbar(
                        state.query,
                        searchExpanded,
                        vm::setQuery,
                        { searchExpanded = true },
                        closeSearch,
                        { if (state.destination == Destination.TILES) vm.refreshComponents() else vm.refresh(forceCatalog = true) },
                        { restore.launch(arrayOf("application/json", "text/plain")) },
                        { export.launch("YEntryCleaner-backup.json") }
                    )
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    when (state.destination) {
                        Destination.RULES -> RulesTab(state, vm)
                        Destination.PRIORITY -> PriorityTab(state, vm)
                        Destination.TILES -> RootComponentsScreen(state, vm)
                        Destination.DASHBOARD -> UnifiedDashboardTabContent(
                            state,
                            vm,
                            { restore.launch(arrayOf("application/json", "text/plain")) },
                            { export.launch("YEntryCleaner-backup.json") },
                            collectingDiagnostics,
                            {
                                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                                diagnosticExport.launch("YEntryCleaner-diagnostic-$stamp.zip")
                            },
                            { fileCheck.launch(arrayOf("*/*")) }
                        )
                    }
                }
            }
        }
    }

    private fun readLimitedText(uri: Uri, maxChars: Int): String {
        val input = contentResolver.openInputStream(uri) ?: error(getString(R.string.backup_read_failed))
        return input.bufferedReader().use {
            it.readBackupText(maxChars, getString(R.string.backup_too_large))
        }
    }

    private fun toast(message: String, long: Boolean = false) =
        Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

    private companion object {
        const val TAG = "YEntryCleaner.MainActivity"
    }
}
