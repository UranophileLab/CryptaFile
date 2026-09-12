package dev.UranophileLab.cryptafile.ui.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.UranophileLab.cryptafile.CryptFile
import dev.UranophileLab.cryptafile.PendingFile
import dev.UranophileLab.cryptafile.data.VaultRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class MainViewModel(private val repository: VaultRepository) : ViewModel() {

    val vaultFiles = mutableStateListOf<CryptFile>()
    
    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent

    init {
        loadVaultFiles()
    }

    fun loadVaultFiles() {
        viewModelScope.launch {
            val files = repository.getRecentFiles() // This actually gets all encrypted files currently
            vaultFiles.clear()
            vaultFiles.addAll(files)
        }
    }

    fun executeBatchAction(files: List<PendingFile>, passphrase: String, isEncrypt: Boolean) {
        viewModelScope.launch {
            var successCount = 0
            files.forEach { file ->
                if (repository.processFile(file.path, passphrase, isEncrypt, file.originalUri)) {
                    successCount++
                }
            }
            _uiEvent.emit(UiEvent.ShowToast("Processed $successCount/${files.size} files"))
            loadVaultFiles()
        }
    }

    fun decryptFile(cryptFile: CryptFile, pass: String) {
        viewModelScope.launch {
            val success = repository.processFile(cryptFile.path, pass, false)
            if (success) {
                _uiEvent.emit(UiEvent.ShowToast("File decrypted successfully"))
                loadVaultFiles()
            } else {
                _uiEvent.emit(UiEvent.ShowToast("Decryption failed"))
            }
        }
    }

    fun toggleTrust(cryptFile: CryptFile) {
        viewModelScope.launch {
            repository.toggleTrust(cryptFile.path)
            loadVaultFiles()
            val isTrustedNow = !cryptFile.isTrusted
            _uiEvent.emit(UiEvent.ShowToast(if (isTrustedNow) "File Trusted" else "Trust Removed"))
        }
    }

    sealed class UiEvent {
        data class ShowToast(val message: String) : UiEvent()
    }
}
