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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxHeight // Import for fillMaxHeight
import androidx.compose.ui.unit.dp // Import for dp
import androidx.compose.ui.Alignment // Import for Alignment
import androidx.compose.foundation.layout.Spacer // Import for Spacer
import androidx.compose.foundation.layout.height // Import for height
import androidx.compose.foundation.layout.Arrangement // Import for Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape // Import for RoundedCornerShape
import androidx.compose.ui.draw.clip // Import for clip
import androidx.compose.material3.MaterialTheme // Import MaterialTheme for colors
import androidx.compose.foundation.background // Import background modifier
import androidx.compose.foundation.layout.Row // Import Row for header layout
import androidx.compose.foundation.layout.fillMaxWidth // Import fillMaxWidth
import androidx.compose.ui.text.font.FontWeight // Import FontWeight
import androidx.compose.ui.graphics.Color // Import Color
import androidx.compose.runtime.SideEffect // Import SideEffect
import androidx.core.view.WindowCompat // Import WindowCompat
import androidx.compose.foundation.layout.WindowInsets // Import WindowInsets
import androidx.compose.foundation.layout.statusBars // Import statusBars
import androidx.compose.foundation.layout.windowInsetsPadding // Import windowInsetsPadding
import androidx.compose.runtime.Composable // Import Composable
import androidx.compose.foundation.layout.padding
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.launch
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.getValue // Import for state delegation
import androidx.compose.runtime.setValue // Import for state delegation


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable drawing behind the system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            QRNGTheme {
                // Get the primary color from the theme for the header and status bar
                val headerColor = MaterialTheme.colorScheme.primary
                val onPrimaryColor = MaterialTheme.colorScheme.onPrimary // Color for text/icons on the primary color

                // Set status bar color
                val systemUiController = rememberSystemUiController()
                SideEffect {
                    systemUiController.setStatusBarColor(
                        color = headerColor,
                        darkIcons = onPrimaryColor != Color.Black // Set dark icons if header color is light
                    )
                    // You might also want to set the navigation bar color
                    // systemUiController.setNavigationBarColor(
                    //     color = MaterialTheme.colorScheme.background // Example: match background color
                    // )
                }

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

                // New state variable for tracking if the Copy button has been pressed
                var isCopied by remember { mutableStateOf(false) }


                val context = LocalContext.current
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                val previewView = remember { PreviewView(context) }
                val imageCapture = remember { ImageCapture.Builder().build() }
                val lifecycleOwner = LocalLifecycleOwner.current

                // Get ClipboardManager
                val clipboardManager = LocalClipboardManager.current

                // Coroutine scope for showing Snackbar
                val coroutineScope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }

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

                // Overall layout container
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars) // Now on the Root
                        .background(Color.White) // Add background here
                ) {

                    // App Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth() // This makes the Row go edge-to-edge
                            .height(56.dp) // Standard AppBar height
                            .background(
                                color = headerColor, // Use primary color for background
                                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp) // Rounded bottom corners
                            )
                            .padding(top = 8.dp), // Add a bit of extra top padding to the header

                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "QRNG v0.1",
                            color = onPrimaryColor,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(horizontal = 16.dp) // Padding ONLY on the text
                        )
                    }

                    //Main UI content.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp) // Add top padding for .content spacing
                            .padding(horizontal = 16.dp), // Add horizontal padding to content
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (hasCameraPermission.value) { // Only show preview if permission is granted
                            AndroidView(
                                factory = { previewView },
                                modifier = Modifier
                                    .fillMaxWidth() // Make it fill the width
                                    .aspectRatio(1f) // Add this to make it a 1:1 aspect ratio (square)
                                    // Removed .fillMaxHeight(0.6f)
                                    .clip(RoundedCornerShape(16.dp)) // Added rounded corners to the camera preview
                            )
                            // Instruction text with padding
                            Text(
                                "Please cover the camera sensor and press the button to capture.",
                                modifier = Modifier.padding(horizontal = 8.dp) // Added horizontal padding to text
                            )
                            // Button with enabled state controlled by isCapturing
                            Button(
                                onClick = {
                                    if (!isCapturing.value) {
                                        if (isCameraOpened.value) {
                                            isCapturing.value = true
                                            showLoading.value = true
                                            imageCapture.takePicture(
                                                ContextCompat.getMainExecutor(context),
                                                object : ImageCapture.OnImageCapturedCallback() {
                                                    override fun onCaptureSuccess(image: ImageProxy) {
                                                        try {
                                                            println("Image captured")
                                                            val bitmap = image.toBitmapNullable()
                                                            image.close()
                                                            if (bitmap != null) {
                                                                try {
                                                                    val number = processImage(bitmap)
                                                                    println("Number: $number")
                                                                    randomNumber.value = number.toString()
                                                                    showResultDialog.value = true
                                                                    showLoading.value = false
                                                                    // Reset isCopied state when a new number is generated and shown
                                                                    isCopied = false

                                                                } catch (e: IllegalStateException) {
                                                                    println("Error: ${e.message}")
                                                                    errorMessage.value = e.message ?: "An error occurred during processing."
                                                                    showErrorDialog.value = true
                                                                    showLoading.value = false
                                                                }
                                                            } else {
                                                                println("Error: Bitmap is null")
                                                                errorMessage.value = "Bitmap is null"
                                                                showErrorDialog.value = true
                                                                showLoading.value = false
                                                            }
                                                        } catch (e: Exception){
                                                            println("Error: ${e.message}")
                                                            errorMessage.value = e.message ?: "An error occurred during processing."
                                                            showErrorDialog.value = true
                                                            showLoading.value = false
                                                        } finally {
                                                            isCapturing.value = false
                                                        }

                                                    }

                                                    override fun onError(exception: ImageCaptureException) {
                                                        println("Image capture error: ${exception.message}")
                                                        errorMessage.value = "Image capture failed: ${exception.message}"
                                                        showErrorDialog.value = true
                                                        showLoading.value = false
                                                        isCapturing.value = false
                                                    }
                                                }
                                            )
                                        }
                                    }
                                },
                                enabled = !isCapturing.value && hasCameraPermission.value // Disable button if capturing or no permission
                            ) {
                                Text("Generate")
                            }
                        } else {
                            // Show a message if permission is not granted
                            Text("Camera permission is required to use the QRNG feature.")
                        }

                        // Loading indicator (positioned within the Column, will be centered horizontally)
                        if (showLoading.value) {
                            CircularProgressIndicator()
                        }

                        // Snackbar host for showing messages
                        SnackbarHost(hostState = snackbarHostState) { data ->
                            Snackbar(
                                snackbarData = data,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                    } // End of Main UI Content Column

                    // Dialogs remain the same (they are drawn on top of the UI)
                    if (showResultDialog.value && randomNumber.value != null) {
                        AlertDialog(
                            onDismissRequest = {
                                showResultDialog.value = false
                                isCopied = false // Reset isCopied when dialog is dismissed
                            },
                            title = { Text("Generated Number") },
                            text = { Text(randomNumber.value!!) },
                            confirmButton = {
                                // Use a Row to place multiple buttons
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp) // Add spacing between buttons
                                ) {
                                    // Copy Button
                                    Button(
                                        onClick = {
                                            // Copy the number to the clipboard
                                            clipboardManager.setText(AnnotatedString(randomNumber.value!!))
                                            // Set isCopied to true
                                            isCopied = true
                                            // Show a confirmation message
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    message = "Generated number copied to clipboard",
                                                    duration = SnackbarDuration.Short
                                                )
                                            }
                                            // Optionally close the dialog after copying
                                            // showResultDialog.value = false
                                        },
                                        enabled = !isCopied // Disable button if already copied
                                    ) {
                                        Text(if (isCopied) "Copied" else "Copy")
                                    }
                                    // OK Button
                                    Button(onClick = {
                                        showResultDialog.value = false
                                        isCopied = false // Reset isCopied when dialog is dismissed
                                    }) {
                                        Text("Close")
                                    }
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
                } // End of Overall layout container
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
    } else {
        val selectedBits = whitenedBits.substring(0, 32)
        return selectedBits.toUInt(2).toInt()
    }
}

@Composable
fun rememberSystemUiController(): com.google.accompanist.systemuicontroller.SystemUiController {
    return com.google.accompanist.systemuicontroller.rememberSystemUiController()
}