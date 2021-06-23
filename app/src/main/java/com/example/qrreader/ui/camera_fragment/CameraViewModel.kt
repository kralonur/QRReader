package com.example.qrreader.ui.camera_fragment

import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.google.mlkit.vision.barcode.Barcode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest

@ExperimentalCoroutinesApi
class CameraViewModel : ViewModel() {

    private val _cameraProvider = MutableStateFlow<ProcessCameraProvider?>(null)
    val cameraProvider = _cameraProvider.asLiveData()

    private val _camera = MutableStateFlow<Camera?>(null)
    val camera = _camera.asLiveData()

    private val _isTorchAvailable = _camera.mapLatest { it?.cameraInfo?.hasFlashUnit() ?: false }
    val isTorchAvailable = _isTorchAvailable.asLiveData()

    private val _latestQR = MutableStateFlow<Barcode?>(null)
    val latestQR =
        _latestQR.distinctUntilChanged { old, new -> old?.url?.url == new?.url?.url }.asLiveData()

    fun setCameraProvider(provider: ProcessCameraProvider) {
        _cameraProvider.value = provider
    }

    fun setCamera(camera: Camera) {
        _camera.value = camera
    }

    fun setLatestQR(barcode: Barcode) {
        _latestQR.value = barcode
    }
}