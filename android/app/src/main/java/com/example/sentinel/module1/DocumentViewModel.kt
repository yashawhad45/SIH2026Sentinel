package com.example.sentinel.module1

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sentinel.core.AggregatedReport
import com.example.sentinel.core.LayerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DocumentUiState(
    val capturedBitmap: Bitmap? = null,
    val capturedUri: Uri? = null,
    val isAnalysing: Boolean = false,
    val completedLayers: List<LayerResult> = emptyList(),
    val report: AggregatedReport? = null,
    val errorMessage: String? = null
)

class DocumentViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DocumentUiState())
    val uiState: StateFlow<DocumentUiState> = _uiState.asStateFlow()

    fun onImageSelected(uri: Uri, bitmap: Bitmap) {
        _uiState.update {
            DocumentUiState(capturedBitmap = bitmap, capturedUri = uri)
        }
    }

    fun runPipeline() {
        val bitmap = _uiState.value.capturedBitmap ?: return
        _uiState.update { it.copy(isAnalysing = true, completedLayers = emptyList(), report = null, errorMessage = null) }

        val pipeline = DocumentForensicPipeline(getApplication())

        viewModelScope.launch {
            try {
                val report = pipeline.run(bitmap) { layerResult ->
                    _uiState.update { state ->
                        state.copy(completedLayers = state.completedLayers + layerResult)
                    }
                }
                _uiState.update { it.copy(isAnalysing = false, report = report) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isAnalysing = false, errorMessage = e.message ?: "Unknown error") }
            } finally {
                pipeline.release()
            }
        }
    }

    fun resetState() {
        _uiState.update { DocumentUiState() }
    }
}
