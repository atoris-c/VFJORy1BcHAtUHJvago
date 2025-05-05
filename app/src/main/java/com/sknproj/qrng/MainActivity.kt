package com.sknproj.qrng

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri // Import Uri
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.sknproj.qrng.ui.theme.QRNGTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.security.MessageDigest // Import for hashing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Import Material Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy // Icon for copy
import androidx.compose.material.icons.filled.Save // Icon for save
import androidx.compose.material.icons.filled.Info // Import Info icon
import kotlin.coroutines.resumeWithException

class MainActivity : ComponentActivity() {

    // State variable to hold the current battery temperature
    private var currentBatteryTemperature by mutableIntStateOf(0) // Temperature in tenths of a degree Celsius

    // State variables for the real-time status indicators
    private var isCameraCovered by mutableStateOf(false)
    private var isTemperatureHigh by mutableStateOf(false)
    private val tempThreshold = 45.0 // Warning threshold in Celsius for display
    // Reduced luminance threshold slightly for potentially better detection in very dark conditions
    private val cameraCoverLuminanceThreshold = 25.0 // Luminance threshold for real-time camera cover detection

    // Executor for image analysis and processing
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var processingExecutor: ExecutorService // New executor for heavy processing

    // Variable to hold the large bitstream when in Advanced Mode (NOT a Compose State)
    private var testDataBitstream: String? = null

    // BroadcastReceiver to listen for battery changes
    private val batteryInfoReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Intent.ACTION_BATTERY_CHANGED == intent.action) {
                // Get the temperature from the intent extra
                currentBatteryTemperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                // Update temperature status based on the threshold
                isTemperatureHigh = (currentBatteryTemperature / 10.0) > tempThreshold
            }
        }
    }

    // Analyzer for real-time luminance check
    private inner class LuminanceAnalyzer(private val onLuminanceAnalyzed: (Double) -> Unit) : ImageAnalysis.Analyzer {

        // Helper function to convert ByteBuffer to ByteArray
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {
            // Access the Y plane (luminance) which is at index 0
            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()

            // Calculate average luminance from the Y plane data
            // We only need the unsigned byte values for luminance calculation
            val pixels = data.map { it.toInt() and 0xFF }

            val averageLuminance = if (pixels.isNotEmpty()) pixels.average() else 0.0

            // Call the callback with the average luminance
            onLuminanceAnalyzed(averageLuminance)

            // Close the image proxy to release the buffer
            image.close()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the executors
        cameraExecutor = Executors.newSingleThreadExecutor()
        processingExecutor = Executors.newSingleThreadExecutor() // Initialize processing executor

        // Register the battery info receiver when the activity is created
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // Use ContextCompat.registerReceiver for better compatibility and handling exported receivers
        ContextCompat.registerReceiver(this, batteryInfoReceiver, batteryFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

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
                }

                // State variables
                val isCapturing = remember { mutableStateOf(false) }
                val showLoading = remember { mutableStateOf(false) }
                val showResultDialog = remember { mutableStateOf(false) }
                val showErrorDialog = remember { mutableStateOf(false) }
                val errorMessage = remember { mutableStateOf("") } // Declared here
                // randomOutput now stores the final hashed output OR a summary string for test data
                val randomOutput = remember { mutableStateOf<String?>(null) } // Single string output for display
                val isCameraOpened = remember { mutableStateOf(false) }
                val cameraState = remember { mutableStateOf<Camera?>(null) }
                val showPermissionDialog = remember { mutableStateOf(true) }
                val hasCameraPermission = remember { mutableStateOf(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
                // Removed hasStoragePermission as it's no longer needed for SAF

                // New state variables for batch processing - Wrapped in remember
                var batchSize by remember { mutableStateOf("10") } // Default batch size
                var currentBatchProgress by remember { mutableIntStateOf(0) } // Progress counter
                var totalBatchSize by remember { mutableIntStateOf(0) } // Total images in the batch
                var batchProcessingStatus by remember { mutableStateOf("") } // Status message for batch

                // New state variable for tracking if the Copy button has been pressed
                var isCopied by remember { mutableStateOf(false) }

                // State variable for Advanced Mode (Test Data Generation)
                var isAdvancedMode by remember { mutableStateOf(false) }
                // State variable to control visibility of the Advanced Mode info dialog
                var showAdvancedModeInfoDialog by remember { mutableStateOf(false) }


                val context = LocalContext.current
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                val previewView = remember { PreviewView(context) }
                val imageCapture = remember { ImageCapture.Builder().build() }
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

                // Get ClipboardManager using LocalClipboardManager.current
                val clipboardManager = LocalClipboardManager.current

                // Coroutine scope for showing Snackbar and managing batch process
                val coroutineScope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }

                // Permission request launcher for Camera
                val cameraPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted: Boolean ->
                    hasCameraPermission.value = isGranted // Update permission status
                    if (isGranted) {
                        // If permission is granted after request, proceed with camera setup
                        // Pass the state variables to setupCamera
                        setupCamera(context, cameraProviderFuture, previewView, imageCapture, cameraExecutor, lifecycleOwner, cameraState, errorMessage, showErrorDialog)
                    } else {
                        // If permission is denied after request
                        errorMessage.value = "Camera permission is required to use this feature."
                        showErrorDialog.value = true
                    }
                }

                // Activity result launcher for creating a document (saving file)
                val createDocumentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("text/plain") // Specify MIME type for text files
                ) { uri: Uri? ->
                    // Handle the result URI here
                    if (uri != null) {
                        // User selected a location, now write the data to the URI
                        coroutineScope.launch {
                            val success = writeDataToUri(context, uri, testDataBitstream)
                            val message = if (success) "Test data saved successfully!" else "Failed to save test data."
                            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
                        }
                    } else {
                        // User cancelled the file picker
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("File save cancelled.", duration = SnackbarDuration.Short)
                        }
                    }
                }


                // Request camera permission on launch
                LaunchedEffect(Unit) {
                    if (!hasCameraPermission.value) {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    } else {
                        // Setup camera if permission is already granted
                        // Pass the state variables to setupCamera
                        setupCamera(context, cameraProviderFuture, previewView, imageCapture, cameraExecutor, lifecycleOwner, cameraState, errorMessage, showErrorDialog)
                    }
                    // No need to request WRITE_EXTERNAL_STORAGE permission anymore
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
                            text = "TRNG v0.10", // Updated version text
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
                            .padding(horizontal = 16.dp) // Add horizontal padding to content
                            .verticalScroll(rememberScrollState()), // Make content scrollable
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp) // Increased spacing
                    ) {
                        if (hasCameraPermission.value) { // Only show preview if camera permission is granted
                            AndroidView(
                                factory = { previewView },
                                modifier = Modifier
                                    .fillMaxWidth() // Make it fill the width
                                    .aspectRatio(1f) // Add this to make it a 1:1 aspect ratio (square)
                                    .clip(RoundedCornerShape(16.dp)) // Added rounded corners to the camera preview
                            )
                            // Instruction text with padding
                            Text(
                                "Please cover the camera sensor and press 'Generate Batch' to capture images.",
                                modifier = Modifier.padding(horizontal = 8.dp) // Added horizontal padding to text
                            )

                            // --- Real-time Status Indicators with Icons ---
                            // Camera Cover Status
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isCameraCovered) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Camera Covered",
                                        tint = Color.Green,
                                        modifier = Modifier.size(20.dp).padding(end = 4.dp)
                                    )
                                }
                                Text(
                                    text = if (isCameraCovered) "Camera is covered!" else "Please cover the camera lens!",
                                    color = if (isCameraCovered) Color.Green else Color.Red,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            // Temperature Status
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isTemperatureHigh) { // Show checkmark if temperature is NOT high (i.e., normal)
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Temperature Normal",
                                        tint = Color.Green,
                                        modifier = Modifier.size(20.dp).padding(end = 4.dp)
                                    )
                                }
                                Text(
                                    text = if (isTemperatureHigh) "Temperature is high (${currentBatteryTemperature / 10.0}°C) - May increase noise" else "Temperature is normal (${currentBatteryTemperature / 10.0}°C)",
                                    color = if (isTemperatureHigh) Color.Yellow else Color.Green, // Use Yellow for warning
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            // --- End Real-time Status Indicators with Icons ---

                            // Batch Size Input
                            OutlinedTextField(
                                value = batchSize,
                                onValueChange = { newValue ->
                                    // Allow only digits
                                    batchSize = newValue.filter { it.isDigit() }
                                },
                                label = { Text("Batch Size (Number of Images)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Advanced Mode Toggle with Info Icon
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Advanced Mode (Generate Test Data)")
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Info Icon Button
                                    IconButton(onClick = { showAdvancedModeInfoDialog = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Advanced Mode Info",
                                            tint = MaterialTheme.colorScheme.primary // Use primary color for the icon
                                        )
                                    }
                                    // Advanced Mode Switch
                                    Switch(
                                        checked = isAdvancedMode,
                                        onCheckedChange = {
                                            isAdvancedMode = it
                                            // Clear previous result when toggling mode
                                            randomOutput.value = null
                                            testDataBitstream = null // Also clear the stored test data
                                        }
                                    )
                                }
                            }


                            // Generate Batch Button
                            Button(
                                onClick = {
                                    val size = batchSize.toIntOrNull() ?: 0
                                    if (size <= 0) {
                                        errorMessage.value = "Please enter a valid batch size (greater than 0)."
                                        showErrorDialog.value = true
                                        return@Button
                                    }
                                    // No need to check for storage permission here anymore


                                    if (!isCapturing.value) {
                                        if (isCameraOpened.value) {
                                            isCapturing.value = true
                                            showLoading.value = true
                                            totalBatchSize = size // Use the state variable
                                            currentBatchProgress = 0 // Use the state variable
                                            batchProcessingStatus = "Starting batch..." // Use the state variable
                                            randomOutput.value = null // Clear previous result
                                            testDataBitstream = null // Clear previous test data

                                            coroutineScope.launch {
                                                val collectedBits = StringBuilder()
                                                var batchError: String? = null

                                                // Loop for batch processing
                                                for (i in 1..totalBatchSize) {
                                                    currentBatchProgress = i // Update the state variable
                                                    batchProcessingStatus = "Capturing image $i of $totalBatchSize..." // Update the state variable

                                                    // Capture image using suspendCancellableCoroutine
                                                    val imageProxy = try {
                                                        suspendCancellableCoroutine<ImageProxy?> { continuation ->
                                                            imageCapture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                                                                override fun onCaptureSuccess(imageResult: ImageProxy) {
                                                                    continuation.resume(imageResult) {
                                                                        imageResult.close() // Ensure image is closed if coroutine is cancelled
                                                                    }
                                                                }

                                                                override fun onError(exception: ImageCaptureException) {
                                                                    continuation.resumeWithException(exception)
                                                                }
                                                            })
                                                        }
                                                    } catch (e: ImageCaptureException) {
                                                        batchError = "Image capture failed for image $i: ${e.message}"
                                                        break // Stop batch on error
                                                    } catch (e: Exception) {
                                                        batchError = "Unexpected error during image capture for image $i: ${e.message}"
                                                        break // Stop batch on error
                                                    }

                                                    // Now, continue processing the 'imageProxy' after the suspend call resumes.
                                                    if (imageProxy != null) {
                                                        batchProcessingStatus = "Processing image $i of $totalBatchSize..." // Update the state variable
                                                        try {
                                                            val bitmap = imageProxy.toBitmapNullable()
                                                            imageProxy.close() // Close the image proxy after converting or processing

                                                            if (bitmap != null) {
                                                                // --- Environmental Checks on Captured Image ---
                                                                val isDarkOnCapture = isImageDark(bitmap, threshold = 20.0)
                                                                if (!isDarkOnCapture) {
                                                                    batchError = "Image $i is not dark. Please ensure the camera lens is completely covered."
                                                                    bitmap.recycle() // Recycle bitmap if not dark and stopping
                                                                    break // Stop batch if image is not dark
                                                                }

                                                                // Temperature status is already updated by the BroadcastReceiver
                                                                if (isTemperatureHigh) {
                                                                    // Optionally log a warning for high temp during batch
                                                                    println("Warning: High temperature (${currentBatteryTemperature / 10.0}°C) during image $i.")
                                                                }
                                                                // --- End Environmental Checks ---

                                                                // Process the image to get Von Neumann corrected bits
                                                                val correctedBits = processImageForCorrectedBits(bitmap)
                                                                collectedBits.append(correctedBits)

                                                                bitmap.recycle() // Recycle bitmap AFTER processing

                                                            } else {
                                                                batchError = "Failed to get bitmap from image $i."
                                                                break // Stop batch on error
                                                            }
                                                        } catch (e: Exception) {
                                                            batchError = "Error processing image $i: ${e.message}"
                                                            break // Stop batch on error
                                                        }
                                                    } else {
                                                        batchError = "Image proxy was null for image $i."
                                                        break // Stop batch on error
                                                    }
                                                } // End of batch loop

                                                // Batch processing finished
                                                showLoading.value = false
                                                isCapturing.value = false // Ensure capturing state is reset

                                                if (batchError != null) {
                                                    errorMessage.value = batchError
                                                    showErrorDialog.value = true
                                                    batchProcessingStatus = "Batch failed." // Update the state variable
                                                } else {
                                                    // --- Conditional Output based on Advanced Mode ---
                                                    if (isAdvancedMode) {
                                                        // Advanced Mode: Store the full concatenated Von Neumann bitstream
                                                        testDataBitstream = collectedBits.toString()
                                                        // Update UI state with a summary message
                                                        randomOutput.value = "Test data generated: ${testDataBitstream?.length ?: 0} bits."
                                                        batchProcessingStatus = "Batch complete. Test data ready." // Update the state variable
                                                    } else {
                                                        // Normal Mode: Apply SHA-256 whitening and output the hash
                                                        batchProcessingStatus = "Batch complete. Applying SHA-256 whitening..." // Update the state variable
                                                        val finalHashedOutput = applySha256Whitening(collectedBits.toString())
                                                        randomOutput.value = finalHashedOutput // Store the final hashed output
                                                        batchProcessingStatus = "Whitening complete. Generated ${finalHashedOutput.length * 4} bits (Hexadecimal)." // SHA-256 is 256 bits = 64 hex chars // Update the state variable
                                                        testDataBitstream = null // Clear test data if not in advanced mode
                                                    }
                                                    // --- End Conditional Output ---
                                                    showResultDialog.value = true // Show dialog with the result
                                                    isCopied = false // Reset copied state
                                                }
                                            }
                                        }
                                    }
                                },
                                enabled = !isCapturing.value && hasCameraPermission.value // Button enabled state simplified
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Generate Batch",
                                    modifier = Modifier.size(20.dp).padding(end = 4.dp)
                                )
                                Text("Generate Batch") // Button text remains "Generate Batch"
                            }

                            // Batch Processing Status
                            if (showLoading.value) {
                                Text(batchProcessingStatus)
                                LinearProgressIndicator(
                                    progress = if (totalBatchSize > 0) currentBatchProgress.toFloat() / totalBatchSize else 0f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }


                            // Snackbar host for showing messages
                            SnackbarHost(hostState = snackbarHostState) { data ->
                                Snackbar(
                                    snackbarData = data,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }

                        } else {
                            // Show a message if camera permission is not granted
                            Text("Camera permission is required to use the TRNG feature.")
                        }
                    } // End of Main UI Content Column

                    // Dialogs remain the same (they are drawn on top of the UI)
                    if (showResultDialog.value && randomOutput.value != null) {
                        AlertDialog(
                            onDismissRequest = {
                                showResultDialog.value = false
                                isCopied = false // Reset isCopied when dialog is dismissed
                            },
                            // --- Conditional Title based on Advanced Mode ---
                            title = {
                                Text(if (isAdvancedMode) "Generated Test Data Summary" else "Generated Random Output (SHA-256)")
                            },
                            // --- End Conditional Title ---
                            text = {
                                // Display the generated output in a scrollable text
                                Column {
                                    // --- Conditional Content Display ---
                                    if (isAdvancedMode) {
                                        // In Advanced Mode, display the summary from randomOutput state
                                        Text(randomOutput.value!!)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Tap 'Save to File' to export the full bitstream.")
                                    } else {
                                        // In Normal Mode, display the SHA-256 hash
                                        Text("Generated Bits: ${randomOutput.value!!.length * 4} (Hexadecimal)") // SHA-256 is 256 bits = 64 hex chars
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = randomOutput.value!!, // Display the hex string output
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 200.dp) // Limit height and make it scrollable
                                                .verticalScroll(rememberScrollState())
                                        )
                                    }
                                    // --- End Conditional Content Display ---
                                }
                            },
                            confirmButton = {
                                // Use a Row to place multiple buttons
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp) // Add spacing between buttons
                                ) {
                                    // Conditional Button based on Advanced Mode
                                    if (isAdvancedMode) {
                                        // Save to File Button for Advanced Mode
                                        Button(
                                            onClick = {
                                                // Launch the file creation intent
                                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                                val filename = "TRNG_TestData_$timestamp.txt"
                                                createDocumentLauncher.launch(filename) // Launch the SAF document creator
                                            },
                                            enabled = testDataBitstream != null // Enable only if test data exists
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Save,
                                                contentDescription = "Save to File",
                                                modifier = Modifier.size(20.dp).padding(end = 4.dp)
                                            )
                                            Text("Save to File")
                                        }
                                    } else {
                                        // Copy Button for Normal Mode
                                        Button(
                                            onClick = {
                                                // Copy the output to the clipboard
                                                val textToCopy = randomOutput.value!!
                                                clipboardManager.setText(AnnotatedString(textToCopy))
                                                // Set isCopied to true
                                                isCopied = true
                                                // Show a confirmation message
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = "Generated random output copied to clipboard",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                }
                                            },
                                            enabled = !isCopied && randomOutput.value != null // Disable if copied or no data
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy Output",
                                                modifier = Modifier.size(20.dp).padding(end = 4.dp)
                                            )
                                            Text(if (isCopied) "Copied" else "Copy Output") // Button text remains "Copy Output"
                                        }
                                    }
                                    // OK Button (remains the same)
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
                                // Only request permission if not already granted
                                if (!hasCameraPermission.value) {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                                // No need to request WRITE_EXTERNAL_STORAGE permission anymore
                            },
                            title = { Text("Welcome!") }, // Updated title
                            text = {
                                // Display the current status for camera permission
                                Column {
                                    Text("Camera permission is ${if (hasCameraPermission.value) "Granted" else "Denied"}")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("This app is native to Android 13+ but should be fully functional for Android 10 and above.")
                                    Text("For Android 9 and below, you may not be able to use the Advanced Mode.")
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    showPermissionDialog.value = false
                                    // Request camera permission if not granted
                                    if (!hasCameraPermission.value) {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }) {
                                    Text("OK")
                                }
                            }
                        )
                    }

                    // Advanced Mode Info Dialog
                    if (showAdvancedModeInfoDialog) {
                        AlertDialog(
                            onDismissRequest = { showAdvancedModeInfoDialog = false },
                            title = { Text("Introducing Advanced Mode") },
                            text = {
                                Text(
                                    "Advanced Mode is for generating large amounts of raw random data (Von Neumann corrected bits) for statistical testing using external tools like NIST SP 800-22. \n\n" +
                                            "In this mode, the app saves the concatenated bitstream from the batch directly to a file instead of applying the final SHA-256 whitening."
                                )
                            },
                            confirmButton = {
                                Button(onClick = { showAdvancedModeInfoDialog = false }) {
                                    Text("Got It")
                                }
                            }
                        )
                    }
                } // End of Overall layout container
            }
        }
    }

    // Helper function to set up the camera
    private fun setupCamera(
        context: Context,
        cameraProviderFuture: com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider>,
        previewView: PreviewView,
        imageCapture: ImageCapture,
        cameraExecutor: ExecutorService,
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        cameraState: MutableState<Camera?>,
        // Pass state variables here
        errorMessage: MutableState<String>,
        showErrorDialog: MutableState<Boolean>
    ) {
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            // Setup ImageAnalysis for real-time luminance check
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // Only analyze the latest frame
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, LuminanceAnalyzer { luminance ->
                        // Update the camera covered status based on real-time luminance
                        isCameraCovered = luminance < cameraCoverLuminanceThreshold
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                // Bind Preview, ImageCapture, and ImageAnalysis
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageCapture, imageAnalyzer
                )
                cameraState.value = camera // Store the Camera instance

            } catch (e: Exception) {
                // Use the passed state variables to update the error message and show the dialog
                errorMessage.value = "Camera binding failed: ${e.message}"
                showErrorDialog.value = true
            }
        }, ContextCompat.getMainExecutor(context))
    }


    // Helper function to write data to a given Uri using SAF
    private suspend fun writeDataToUri(context: Context, uri: Uri, data: String?): Boolean {
        if (data == null) return false

        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(data.toByteArray())
                }
                true // Indicate success
            } catch (e: Exception) {
                println("Error writing data to Uri: ${e.message}")
                false // Indicate failure
            }
        }
    }


    // Unregister the receiver and shut down the executors when the activity is destroyed
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryInfoReceiver)
        cameraExecutor.shutdown() // Shut down the camera executor
        processingExecutor.shutdown() // Shut down the processing executor
    }

    // Helper function to check if the image is predominantly dark (lens covered)
    // Calculates the average luminance and checks if it's below a threshold.
    // This is still used for a final check on the captured image.
    fun isImageDark(bitmap: Bitmap, threshold: Double): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        var totalLuminance = 0.0
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Calculate total luminance
        for (pixel in pixels) {
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            // Calculate luminance using a common formula
            val luminance = 0.299 * red + 0.587 * green + 0.114 * blue
            totalLuminance += luminance
        }

        // Calculate average luminance
        val averageLuminance = totalLuminance / (width * height)

        // Check if average luminance is below the threshold
        return averageLuminance < threshold
    }

    // Helper function to convert ImageProxy to Bitmap (can return null)
    fun ImageProxy.toBitmapNullable(): Bitmap? {
        try {
            val buffer = planes[0].buffer
            buffer.rewind()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            println("Error converting ImageProxy to Bitmap: ${e.message}")
            return null
        }
    }

    // Von Neumann Corrector function - remains the same
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

    // Modified processImage function to return Von Neumann corrected bits string
    fun processImageForCorrectedBits(bitmap: Bitmap): String {
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
            // Use Kotlin's xor extension function for clarity
            val randomBit = lsbRed xor lsbGreen xor lsbBlue
            randomBits.append(randomBit)
        }

        val rawBits = randomBits.toString()
        // Return the Von Neumann corrected bits
        return vonNeumannCorrector(rawBits)
    }

    // Function to apply SHA-256 whitening to a bitstring
    fun applySha256Whitening(bits: String): String {
        // Convert the bit string to a byte array.
        // This requires packing the bits into bytes (8 bits per byte).
        // If the total number of bits is not a multiple of 8, the last byte will be padded with zeros.
        val bytes = ByteArray((bits.length + 7) / 8) // Calculate number of bytes needed
        for (i in bits.indices) {
            if (bits[i] == '1') {
                // Set the corresponding bit in the byte array
                bytes[i / 8] = (bytes[i / 8].toInt() or (1 shl (7 - (i % 8)))).toByte()
            }
        }

        // Apply SHA-256 hash
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)

        // Convert the hash bytes to a hexadecimal string for display
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
