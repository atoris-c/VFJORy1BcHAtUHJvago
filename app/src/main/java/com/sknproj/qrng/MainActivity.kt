package com.sknproj.qrng

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
import androidx.camera.core.ImageProxy
import androidx.camera.core.ImageCaptureException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QRNGTheme {
                // State variables
                val isCapturing = remember { mutableStateOf(false) }
                val showLoading = remember { mutableStateOf(false) }
                val showResultDialog = remember { mutableStateOf(false) }
                val showErrorDialog = remember { mutableStateOf(false) } // New error state
                val errorMessage = remember { mutableStateOf("") }
                val randomNumber = remember { mutableStateOf<String?>(null) }

                val context = LocalContext.current
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                val previewView = remember { PreviewView(context) }
                val imageCapture = remember { ImageCapture.Builder().build() }

                // Set up camera
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            this, cameraSelector, preview, imageCapture
                        )
                    } catch (e: Exception) {
                        // Handle camera setup error (e.g., log it)
                    }
                }, ContextCompat.getMainExecutor(context))

                Column(modifier = Modifier.fillMaxSize()) {
                    // Camera preview
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.weight(1f)
                    )
                    // Instructions
                    Text("Please cover the camera sensor and press the button to capture.")
                    // Capture button
                    Button(onClick = {
                        if (!isCapturing.value) {
                            isCapturing.value = true
                            showLoading.value = true
                            imageCapture.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        CoroutineScope(Dispatchers.Default).launch {
                                            val bitmap = image.toBitmap()
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
                        }
                    }) {
                        Text("Capture")
                    }
                    // Loading indicator
                    if (showLoading.value) {
                        CircularProgressIndicator()
                    }
                    // Result dialog
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
                    // Error dialog
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
                }
            }
        }
    }
}

// Helper functions remain unchanged
fun ImageProxy.toBitmap(): Bitmap? {
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

/*@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    QRNGTheme {
        Greeting("Android")
    }
}*/