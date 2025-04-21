package com.sknproj.qrng

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.sknproj.qrng.ui.theme.QRNGTheme
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.Camera
import androidx.camera.core.CameraState
import androidx.camera.core.ImageProxy
import androidx.camera.core.ImageCaptureException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.asFlow
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.observe
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QRNGTheme {
                // State variables
                val isCapturing = remember { mutableStateOf(false) }
                val showLoading = remember { mutableStateOf(false) }
                val showResultDialog = remember { mutableStateOf(false) }
                val showErrorDialog = remember { mutableStateOf(false) }
                val errorMessage = remember { mutableStateOf("") }
                val randomNumber = remember { mutableStateOf<String?>(null) }
                val isCameraOpened = remember { mutableStateOf(false) } // New state
                val cameraState = remember { mutableStateOf<Camera?>(null) } // To store Camera instance
                // showPermissionDialog is now initially true to always show it on startup
                val showPermissionDialog = remember { mutableStateOf(true) }
                // hasCameraPermission checks the status on startup
                val hasCameraPermission = remember { mutableStateOf(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }


                val context = LocalContext.current
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                val previewView = remember { PreviewView(context) }
                val imageCapture = remember { ImageCapture.Builder().build() }
                val lifecycleOwner = LocalLifecycleOwner.current

                // Permission request launcher
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted: Boolean ->
                    hasCameraPermission.value = isGranted // Update permission status
                    if (isGranted) {
                        // If permission is granted after request, proceed with camera setup
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                val camera = cameraProvider.bindToLifecycle(
                                    this@MainActivity, cameraSelector, preview, imageCapture
                                )
                                cameraState.value = camera // Store the Camera instance

                            } catch (e: Exception) {
                                errorMessage.value = "Camera binding failed: ${e.message}"
                                showErrorDialog.value = true
                            }
                        }, ContextCompat.getMainExecutor(context))
                    } else {
                        // If permission is denied after request
                        errorMessage.value = "Camera permission is required to use this feature."
                        showErrorDialog.value = true
                    }
                }

                // Always show the permission dialog on launch
                LaunchedEffect(Unit) {
                    // The dialog is already initialized to true, so we just need to ensure
                    // the camera setup happens if permission is already granted.
                    // The dialog itself will display the status.
                    if (hasCameraPermission.value) {
                        // Proceed with camera setup if permission is already granted
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                val camera = cameraProvider.bindToLifecycle(
                                    this@MainActivity, cameraSelector, preview, imageCapture
                                )
                                cameraState.value = camera // Store the Camera instance

                            } catch (e: Exception) {
                                errorMessage.value = "Camera binding failed: ${e.message}"
                                showErrorDialog.value = true
                            }
                        }, ContextCompat.getMainExecutor(context))
                    }
                    // showPermissionDialog is already true, so the dialog will be shown.
                }


                // Set up camera (only if permission is granted and dialog is hidden)
                // This block is now primarily for initial setup if permission is already granted
                // The LaunchedEffect handles the case where permission is granted after the dialog/request
                // We keep this check here as a fallback/alternative trigger for camera setup
                if (hasCameraPermission.value && cameraState.value == null) {
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(
                                this@MainActivity, cameraSelector, preview, imageCapture
                            )
                            cameraState.value = camera // Store the Camera instance

                        } catch (e: Exception) {
                            errorMessage.value = "Camera binding failed: ${e.message}"
                            showErrorDialog.value = true
                        }
                    }, ContextCompat.getMainExecutor(context))
                }


                // Observe camera state
                DisposableEffect(cameraState.value) {
                    val camera = cameraState.value
                    // only if we have a bound Camera
                    camera?.cameraInfo?.cameraState?.observe(lifecycleOwner) { state ->
                        isCameraOpened.value = (state.type == CameraState.Type.OPEN && state.error == null)
                        if (state.error != null) {
                            println("Camera error: ${state.error}")
                            // Optionally, show a message like "Camera error, please try again"
                        }
                    }
                    // Clean up when cameraState.value changes or leaves composition
                    onDispose { /* LiveData.observe is self-removing when lifecycleOwner is destroyed */ }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    if (hasCameraPermission.value) { // Only show preview if permission is granted
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.weight(1f)
                        )
                        Text("Please cover the camera sensor and press the button to capture.")
                        Button(onClick = {
                            if (!isCapturing.value) {
                                if (isCameraOpened.value) {
                                    isCapturing.value = true
                                    showLoading.value = true
                                    imageCapture.takePicture(
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageCapturedCallback() {
                                            override fun onCaptureSuccess(image: ImageProxy) {
                                                CoroutineScope(Dispatchers.Default).launch {
                                                    val bitmap = image.toBitmapNullable()
                                                    image.close()
                                                    if (bitmap == null) {
                                                        withContext(Dispatchers.Main) {
                                                            showLoading.value = false
                                                            isCapturing.value = false
                                                            errorMessage.value = "Failed to decode image"
                                                            showErrorDialog.value = true
                                                        }
                                                        return@launch
                                                    }
                                                    try {
                                                        val number = processImage(bitmap)
                                                        withContext(Dispatchers.Main) {
                                                            randomNumber.value = number.toString()
                                                            showResultDialog.value = true
                                                            showLoading.value = false
                                                            isCapturing.value = false
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) {
                                                            showLoading.value = false
                                                            isCapturing.value = false
                                                            errorMessage.value = "Failed to process image: ${e.message}"
                                                            showErrorDialog.value = true
                                                        }
                                                    }
                                                }
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                CoroutineScope(Dispatchers.Main).launch {
                                                    showLoading.value = false
                                                    isCapturing.value = false
                                                    errorMessage.value = "Image capture failed: ${exception.message}"
                                                    showErrorDialog.value = true
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    errorMessage.value = "Camera is not ready. Please try again."
                                    showErrorDialog.value = true
                                }
                            }
                        }) {
                            Text("Capture")
                        }
                    } else {
                        // Show a message if permission is not granted
                        Text("Camera permission is required to use the QRNG feature.")
                    }


                    if (showLoading.value) {
                        CircularProgressIndicator()
                    }
                    if (showResultDialog.value && randomNumber.value != null) {
                        AlertDialog(
                            onDismissRequest = { showResultDialog.value = false },
                            title = { Text("Generated Number") },
                            text = { Text(randomNumber.value!!) },
                            confirmButton = {
                                Button(onClick = { showResultDialog.value = false }) {
                                    Text("OK")
                                }
                            }
                        )
                    }
                    if (showErrorDialog.value) {
                        AlertDialog(
                            onDismissRequest = { showErrorDialog.value = false },
                            title = { Text("Error") },
                            text = { Text(errorMessage.value) },
                            confirmButton = {
                                Button(onClick = { showErrorDialog.value = false }) {
                                    Text("OK")
                                }
                            }
                        )
                    }

                    // Permission Status Dialog - Always displayed on startup
                    if (showPermissionDialog.value) {
                        AlertDialog(
                            onDismissRequest = {
                                showPermissionDialog.value = false
                                // Only request permission if it's not already granted
                                if (!hasCameraPermission.value) {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            title = { Text("Camera Permission Status") }, // Updated title
                            text = {
                                // Display the current status
                                if (hasCameraPermission.value) {
                                    Text("Camera permission has been granted. You can now use the QRNG feature.")
                                } else {
                                    Text("Camera permission is required to use this feature. Please grant the permission when prompted.")
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    showPermissionDialog.value = false
                                    // Only request permission if it's not already granted
                                    if (!hasCameraPermission.value) {
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }) {
                                    // Button text can be "OK" regardless of status
                                    Text("OK")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// Helper functions remain unchanged
fun ImageProxy.toBitmapNullable(): Bitmap? {
    val buffer = planes[0].buffer
    buffer.rewind()
    val bytes = ByteArray(buffer.remaining()) // Use remaining() instead of capacity()
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

fun vonNeumannCorrector(bits: String): String {
    val correctedBits = StringBuilder()
    for (i in 0 until bits.length - 1 step 2) {
        val pair = bits.substring(i, i + 2)
        when (pair) {
            "01" -> correctedBits.append('0')  // 01 becomes 0
            "10" -> correctedBits.append('1')  // 10 becomes 1
            // "00" and "11" are discarded
        }
    }
    return correctedBits.toString()
}

fun processImage(bitmap: Bitmap): Int {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val randomBits = StringBuilder()
    for (pixel in pixels) {
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        val lsbRed = red and 1
        val lsbGreen = green and 1
        val lsbBlue = blue and 1
        val randomBit = lsbRed xor lsbGreen xor lsbBlue
        randomBits.append(randomBit)
    }

    val rawBits = randomBits.toString()
    var whitenedBits = vonNeumannCorrector(rawBits)

    // If not enough bits, process more data or retry
    if (whitenedBits.length < 32) {
        // For simplicity, pad with zeros or retry; in practice, gather more bits
        throw IllegalStateException("Only ${whitenedBits.length} bits after whitening, need 32")
        // Alternative: while (whitenedBits.length < 32) whitenedBits += "0"
    }

    val selectedBits = whitenedBits.substring(0, 32)
    return selectedBits.toInt(2)
}
