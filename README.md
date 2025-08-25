# TRNG - True Random Number Generator

A mobile True Random Number Generator (TRNG) app for Android that uses camera sensor data to generate cryptographically secure random numbers.

## How It Works

This app generates truly random numbers by leveraging the quantum noise present in your device's camera sensor. Here's the process:

1. **Image Capture**: The app continuously captures images from your device's camera
2. **Bit Extraction**: Random bits are extracted from the least significant bits (LSBs) of the red, green, and blue pixel values
3. **XOR Processing**: The LSBs from each color channel are XORed together to produce raw random bits
4. **Von Neumann Correction**: Raw bits are processed using the Von Neumann algorithm to remove bias
5. **Final Processing**: 
   - **Regular Mode**: Applies SHA-256 hashing for additional whitening and produces hex output
   - **Advanced Mode**: Outputs raw Von Neumann corrected bitstream for statistical testing

## Features

- **Two Generation Modes**:
  - **Regular Mode**: Generates SHA-256 whitened random hex strings suitable for general use
  - **Advanced Mode**: Generates large amounts of raw random data for statistical analysis with tools like NIST SP 800-22

- **Battery Status Integration**: Displays current battery level and charging status
- **Real-time Camera Preview**: Shows live camera feed during random number generation
- **Batch Processing**: Generate multiple random values in sequence
- **Export Functionality**: Save raw test data to files for external analysis
- **Copy to Clipboard**: Easy copying of generated random values

## System Requirements

- **Native Support**: Android 13+ (API level 33+)
- **Full Functionality**: Android 10+ (API level 29+)
- **Limited Functionality**: Android 9 and below (Advanced Mode may not work)
- **Permissions Required**: Camera access for random bit generation

## Building with Android Studio

### Prerequisites

1. **Android Studio**: Download and install [Android Studio](https://developer.android.com/studio)
2. **JDK**: Ensure you have JDK 11 or higher installed
3. **Android SDK**: API level 35 (Android 15) or higher

### Build Instructions

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/atoris-c/VFJORy1BcHAtUHJvago.git
   cd VFJORy1BcHAtUHJvago
   ```

2. **Open in Android Studio**:
   - Launch Android Studio
   - Select "Open an Existing Project"
   - Navigate to the cloned repository folder and select it
   - Wait for Gradle sync to complete

3. **Configure SDK**:
   - Go to File → Project Structure → Project
   - Ensure Gradle Version is 8.11.1 or compatible
   - Set Compile SDK Version to API 35 (Android 15)

4. **Build the Project**:
   - Select Build → Make Project (Ctrl+F9)
   - Or use the terminal: `./gradlew build`

5. **Run on Device/Emulator**:
   - Connect an Android device with USB debugging enabled, or start an emulator
   - Click the "Run" button (green play icon) or press Shift+F10
   - Select your target device and click OK

### Build Variants

- **Debug**: Standard debug build with debugging enabled
- **Release**: Optimized release build (requires signing for distribution)

### Dependencies

Key dependencies include:
- Jetpack Compose for modern UI
- CameraX for camera functionality
- Material 3 for Material Design components
- Kotlin Coroutines for asynchronous operations

## Usage

1. **Grant Permissions**: Allow camera access when prompted
2. **Select Mode**: Choose between Regular or Advanced mode
3. **Set Parameters**: Configure the number of bits or batch size as needed
4. **Generate**: Tap the generate button to start the random number generation process
5. **View Results**: Random output will be displayed in a dialog
6. **Copy/Save**: Use the copy button or save raw data to files as needed

## Security Considerations

- The app uses quantum noise from camera sensors as an entropy source
- Von Neumann bias correction is applied to ensure uniform distribution
- SHA-256 whitening in Regular Mode provides additional cryptographic strength
- Advanced Mode outputs are suitable for statistical randomness testing

## License

This project is open source. Please refer to the license file for details.
