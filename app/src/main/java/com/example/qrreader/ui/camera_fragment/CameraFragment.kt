package com.example.qrreader.ui.camera_fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.qrreader.databinding.FragmentCameraBinding
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.CompositePermissionListener
import com.karumi.dexter.listener.single.PermissionListener
import com.karumi.dexter.listener.single.SnackbarOnDeniedPermissionListener
import timber.log.Timber
import java.util.concurrent.ExecutionException

class CameraFragment : Fragment() {
    private lateinit var binding: FragmentCameraBinding

    private val lensFacing = CameraSelector.LENS_FACING_BACK
    private val cameraSelector by lazy {
        CameraSelector.Builder().requireLensFacing(lensFacing).build()
    }
    private val cameraProviderFuture by lazy { ProcessCameraProvider.getInstance(requireContext()) }
    private val mainExecutor by lazy { ContextCompat.getMainExecutor(requireContext()) }

    private lateinit var cameraProvider: ProcessCameraProvider

    private var preview: Preview? = null
    private var analysis: ImageAnalysis? = null

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
        cameraProviderFuture.addListener(
            {
                try {
                    cameraProvider = cameraProviderFuture.get()
                } catch (e: ExecutionException) {
                    Timber.e(e)
                } catch (e: InterruptedException) {
                    Timber.e(e)
                }
                bindUseCases()

            },
            mainExecutor
        )

    }

    private fun bindUseCases() {
        bindPreview()
        bindAnalyse()
    }

    private fun bindPreview() {
        if (preview != null) {
            cameraProvider.unbind(preview)
        }

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setTargetRotation(Surface.ROTATION_0)
            .build()

        preview!!.setSurfaceProvider(binding.preview.surfaceProvider)

        try {
            cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview)
        } catch (e: IllegalStateException) {
            Timber.e(e)
        } catch (e: IllegalArgumentException) {
            Timber.e(e)
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindAnalyse() {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)

        if (analysis != null) {
            cameraProvider.unbind(analysis)
        }

        analysis = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setTargetRotation(Surface.ROTATION_0)
            .build()

        analysis!!.setAnalyzer(mainExecutor, { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        barcodes.forEach {
                            when (it.valueType) {
                                Barcode.TYPE_URL -> {
                                    it?.let { barcode ->
                                        barcode.url?.url?.let {
                                            openURLOnSnackbar(it, it)
                                        }
                                    }
                                }
                                else -> Timber.i("${it.rawValue} - ${it.valueType}")
                            }
                        }
                    }
                    .addOnFailureListener {
                        Timber.e(it)
                    }.addOnCompleteListener {
                        // When the image is from CameraX analysis use case, must call image.close() on received
                        // images when finished using them. Otherwise, new images may not be received or the camera
                        // may stall.
                        imageProxy.close()
                    }
            }
        })

        try {
            cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, analysis)
        } catch (e: IllegalStateException) {
            Timber.e(e)
        } catch (e: IllegalArgumentException) {
            Timber.e(e)
        }
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
}