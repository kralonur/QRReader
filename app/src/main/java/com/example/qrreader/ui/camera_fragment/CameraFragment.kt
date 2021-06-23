package com.example.qrreader.ui.camera_fragment

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import com.example.qrreader.QRAnalyzer
import com.example.qrreader.QRListListener
import com.example.qrreader.databinding.FragmentCameraBinding
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.barcode.Barcode
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.CompositePermissionListener
import com.karumi.dexter.listener.single.PermissionListener
import com.karumi.dexter.listener.single.SnackbarOnDeniedPermissionListener
import kotlinx.coroutines.ExperimentalCoroutinesApi
import timber.log.Timber
import java.util.concurrent.ExecutionException


@ExperimentalCoroutinesApi
class CameraFragment : Fragment(), QRListListener {
    private val viewModel by viewModels<CameraViewModel>()
    private lateinit var binding: FragmentCameraBinding

    private val lensFacing = CameraSelector.LENS_FACING_BACK
    private val cameraSelector by lazy {
        CameraSelector.Builder().requireLensFacing(lensFacing).build()
    }
    private val cameraProviderFuture by lazy { ProcessCameraProvider.getInstance(requireContext()) }
    private val mainExecutor by lazy { ContextCompat.getMainExecutor(requireContext()) }

    private var camera: Camera? = null
    private var torchState = TorchState.OFF
    private var isTorchAvailable = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkPermission()

        viewModel.cameraProvider.observe(viewLifecycleOwner) {
            it?.let {
                bindUseCases(it)
            }
        }

        viewModel.camera.observe(viewLifecycleOwner) {
            camera = it
            it?.let {
                observeTorchState(it.cameraInfo.torchState)
            }
        }

        viewModel.isTorchAvailable.observe(viewLifecycleOwner) {
            isTorchAvailable = it
            setTorchColor(if (!isTorchAvailable) Color.RED else Color.YELLOW)
        }

        viewModel.latestQR.observe(viewLifecycleOwner) { barcodeNullable ->
            barcodeNullable?.let { barcode ->
                barcode.url?.url?.let {
                    openURLOnSnackbar(it, it)
                }
            }
        }

        binding.torch.setOnClickListener {
            if (isTorchAvailable) camera?.cameraControl?.enableTorch(torchState != TorchState.ON)
        }
    }

    private fun checkPermission() {
        val permissionListener = object : PermissionListener {
            override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
                setup()
            }

            override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
            }

            override fun onPermissionRationaleShouldBeShown(
                p0: PermissionRequest?,
                p1: PermissionToken?
            ) {
                p1?.continuePermissionRequest()
            }
        }

        val snackbarOnDeniedPermissionListener = SnackbarOnDeniedPermissionListener.Builder
            .with(
                binding.root,
                "Camera access is needed to read QR code"
            )
            .withOpenSettingsButton("Settings")
            .build()

        val compositePermissionListener =
            CompositePermissionListener(permissionListener, snackbarOnDeniedPermissionListener)

        Dexter.withContext(requireContext())
            .withPermission(Manifest.permission.CAMERA)
            .withListener(compositePermissionListener)
            .check()
    }

    private fun setup() {
        Timber.i("Setup called")
        cameraProviderFuture.addListener(
            {
                try {
                    viewModel.setCameraProvider(cameraProviderFuture.get())
                } catch (e: ExecutionException) {
                    Timber.e(e)
                } catch (e: InterruptedException) {
                    Timber.e(e)
                }
            },
            mainExecutor
        )

    }

    private fun bindUseCases(cameraProvider: ProcessCameraProvider) {
        cameraProvider.unbindAll()
        val camera = cameraProvider.bindToLifecycleWithExceptionHandling(
            viewLifecycleOwner,
            cameraSelector,
            getPreviewUseCase(),
            getAnalyzeUseCase()
        )

        if (camera != null) viewModel.setCamera(camera)
    }

    private fun getPreviewUseCase(): Preview {
        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setTargetRotation(Surface.ROTATION_0)
            .build()

        preview.setSurfaceProvider(binding.preview.surfaceProvider)

        return preview
    }

    private fun getAnalyzeUseCase(): ImageAnalysis {
        val analysis = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setTargetRotation(Surface.ROTATION_0)
            .build()

        analysis.setAnalyzer(mainExecutor, QRAnalyzer(this))

        return analysis
    }

    override fun qrList(list: List<Barcode>) {
        list.forEach {
            when (it.valueType) {
                Barcode.TYPE_URL -> {
                    viewModel.setLatestQR(it)
                }
                else -> Timber.i(it.rawValue)
            }
        }
    }

    private fun observeTorchState(torchStateLiveData: LiveData<Int>) {
        torchStateLiveData.observe(viewLifecycleOwner) {
            torchState = it
            if (isTorchAvailable) setTorchColor(if (torchState == TorchState.ON) Color.WHITE else Color.YELLOW)
        }
    }

    private fun setTorchColor(@ColorInt tint: Int) {
        DrawableCompat.setTint(
            DrawableCompat.wrap(binding.torch.drawable),
            tint
        )
    }

    private fun openURLOnSnackbar(title: String, url: String) {
        Snackbar.make(binding.root, title, Snackbar.LENGTH_INDEFINITE)
            .setAction("GO") {
                val i = Intent(Intent.ACTION_VIEW)
                i.data = Uri.parse(url)
                startActivity(i)
            }
            .show()
    }

    private fun ProcessCameraProvider.bindToLifecycleWithExceptionHandling(
        lifecycleOwner: LifecycleOwner,
        cameraSelector: CameraSelector,
        vararg useCases: UseCase
    ): Camera? {
        try {
            return this.bindToLifecycle(lifecycleOwner, cameraSelector, *useCases)
        } catch (e: IllegalStateException) {
            Timber.e(e)
        } catch (e: IllegalArgumentException) {
            Timber.e(e)
        }
        return null
    }
}