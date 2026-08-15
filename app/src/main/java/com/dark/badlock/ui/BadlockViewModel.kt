package com.dark.badlock.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dark.badlock.data.AppUpdateInfo
import com.dark.badlock.data.InstalledModule
import com.dark.badlock.data.ModuleRepository
import com.dark.badlock.data.ModuleState
import com.dark.badlock.logic.UpdateChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BadlockViewModel(private val repository: ModuleRepository, private val context: Context) : ViewModel() {

    private val _moduleState = MutableStateFlow<ModuleState>(ModuleState.Loading)
    val moduleState: StateFlow<ModuleState> = _moduleState.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<InstalledModule>> = combine(_moduleState, _searchQuery) { state, query ->
        if (query.isEmpty() || state !is ModuleState.Success) return@combine emptyList()

        state.modules.values.flatten().filter { 
            it.name.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) 
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _appUpdateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val appUpdateInfo: StateFlow<AppUpdateInfo?> = _appUpdateInfo.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    fun refreshData(force: Boolean = false) {
        if (_moduleState.value !is ModuleState.Success || force) {
            _moduleState.value = ModuleState.Loading
        }

        viewModelScope.launch {
            // Check for app's own update
            launch {
                val update = UpdateChecker.checkAppUpdate()
                val currentVersion = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0"
                } catch (e: Exception) { "0.0" }

                if (update != null && UpdateChecker.isUpdateAvailable(currentVersion, update.latestVersion)) {
                    _appUpdateInfo.value = update
                    _showUpdateDialog.value = true
                }
            }

            val newState = repository.loadData(force)
            _moduleState.value = newState
        }
    }

    class Factory(private val repository: ModuleRepository, private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BadlockViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return BadlockViewModel(repository, context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
