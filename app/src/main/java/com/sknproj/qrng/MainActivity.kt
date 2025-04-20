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
import androidx.compose.material3.Button
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QRNGTheme {
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
                        // Handle error (e.g., log it)
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
                        imageCapture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    // Convert ImageProxy to Bitmap (you’ll need a helper function)
                                    val bitmap = image.toBitmap()
                                    val number = processImage(bitmap)
                                    randomNumber.value = number.toString()
                                    image.close() // Important to free resources
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    // Handle error (e.g., show a message)
                                }
                            }
                        )
                    }) {
                        Text("Capture")
                    }
                    // Display random number if available
                    randomNumber.value?.let {
                        Text("Generated Number: $it")
                    }
                }
            }
        }
    }
}

fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    buffer.rewind()
    val bytes = ByteArray(buffer.capacity())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
fun processImage(bitmap: Bitmap): Int {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val randomBits = StringBuilder()
    for (pixel in pixels) {
        val blue = pixel and 0xFF // Get blue channel
        val lsb = blue and 1 // Least significant bit
        randomBits.append(lsb)
        if (randomBits.length >= 32) break // Collect 32 bits
    }
    return randomBits.toString().toInt(2) // Convert binary string to integer
}

@Composable
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
}