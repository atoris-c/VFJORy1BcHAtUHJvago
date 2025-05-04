True Random Number Generator (TRNG) Android App
This project implements a True Random Number Generator (TRNG) on an Android device, utilizing the inherent random noise from the camera sensor as the source of entropy.
Table of Contents
What is a Random Number Generator?
Why Do We Need True Randomness?
The Heart of Our TRNG: Camera Noise
Speaking the Language of Randomness: Bits and Binary
Extracting Randomness from the Noise
Cleaning Up the Bits: The Von Neumann Corrector
Making it Cryptographically Strong: SHA-256 Whitening
How the App Works in Different Modes
Why Environmental Conditions Matter
Proving Randomness: Statistical Testing
Getting Started (Add build/run instructions here)
Contributing (Optional section)
License (Optional section)
What is a Random Number Generator?
Imagine flipping a coin. Each flip is unpredictable – it could be heads or tails. A random number generator is something that produces a sequence of results that are similarly unpredictable.
There are two main types:
Pseudo-Random Number Generators (PRNGs): These use a mathematical formula (an algorithm) to produce sequences of numbers that look random. However, if you know the starting point (called a "seed"), you can predict the entire sequence. These are great for many things, like simulations or games, but they aren't truly unpredictable.
True Random Number Generators (TRNGs): These use a physical process that is naturally unpredictable to generate random numbers. Our project is a TRNG because it uses the random noise from your phone's camera sensor.
Why Do We Need True Randomness?
For many applications, like cryptography (keeping information secret and secure), scientific simulations, and even some types of gaming, we need randomness that is truly unpredictable. If someone could guess the random numbers being used, they could potentially break encryption or manipulate systems. TRNGs provide this higher level of unpredictability.
The Heart of Our TRNG: Camera Noise
Our project uses the tiny, random fluctuations happening inside your phone's camera sensor as the source of randomness. Even when the lens is covered and no light is hitting the sensor, there's still electrical "noise" happening at a very fundamental level. This noise is caused by things like heat (thermal noise) and the way electrons move (shot noise, dark current). These physical processes are inherently unpredictable, making them a great source of entropy.
Entropy: Think of entropy as a measure of unpredictability or disorder. A source with high entropy is very unpredictable. The noise in the camera sensor has high entropy.
Speaking the Language of Randomness: Bits and Binary
Computers understand information as bits. A bit is the smallest piece of information and can only be one of two values: 0 or 1.
Binary: This is the number system computers use, based on just two digits: 0 and 1. Everything a computer does, from showing pictures to running apps, is ultimately done using sequences of 0s and 1s (binary code).
Bitstream: A bitstream is simply a sequence of bits (0s and 1s) flowing one after another. Our TRNG generates a bitstream of random 0s and 1s.
Extracting Randomness from the Noise
The camera sensor produces electrical signals based on the light it receives (and the noise). We need to convert this noisy signal into a stream of 0s and 1s.
Image Capture: When you press the button with the camera covered, the app captures an image. Even though it's dark, the image still contains the random noise from the sensor in its pixel data.
Pixel Data: Each tiny point in the image (a pixel) has a color value, usually represented by how much Red, Green, and Blue light it has. These values are stored as numbers in binary.
Least Significant Bit (LSB): We focus on the very last bit (the "least significant bit") of the color values for each pixel. This last bit is the most likely to be affected by the tiny, random noise fluctuations, making it a good source of randomness.
XOR Operation: We combine the LSBs from the Red, Green, and Blue values of each pixel using a simple operation called XOR (Exclusive OR).
XOR is like a simple rule: If the two bits are different (0 and 1), the result is 1. If they are the same (0 and 0, or 1 and 1), the result is 0.
Using XOR helps to mix the randomness from the different color channels in each pixel.
This process gives us a raw bitstream of 0s and 1s extracted from the image noise.
Cleaning Up the Bits: The Von Neumann Corrector
Even after extracting the LSBs and using XOR, the raw bitstream might still have a slight bias (for example, slightly more 0s than 1s). The Von Neumann Corrector is a clever technique to remove this simple bias.
It works by looking at the bitstream two bits at a time (in pairs):
If the pair is "01", it outputs a "0".
If the pair is "10", it outputs a "1".
If the pair is "00" or "11", it discards the pair completely.
This method guarantees that for every "0" it outputs, it discards a "00" and a "11" and processes a "01", and for every "1" it outputs, it discards a "00" and a "11" and processes a "10". This balances the output to have an equal number of 0s and 1s, removing simple bias. The downside is that it discards a lot of the original bits.
Making it Cryptographically Strong: SHA-256 Whitening
After collecting the Von Neumann corrected bits from a batch of images, we have a longer bitstream that is less biased. To make this even more suitable for secure applications, we use a process called whitening or conditioning with a cryptographic hash function called SHA-256.
Hash Function: A hash function is like a digital fingerprint. It takes any amount of data (your long bitstream) and produces a fixed-size output (a hash). Even a tiny change in the input data will result in a completely different hash.
SHA-256: This is a specific, widely used and very secure hash function. It always produces a 256-bit hash (which we show as a 64-character hexadecimal number).
Applying SHA-256 to the collected Von Neumann bits mixes the randomness thoroughly and produces a high-quality, fixed-size random output that is very difficult to predict or manipulate.
How the App Works in Different Modes
Our app has two modes to serve different purposes:
Normal Mode (Advanced Mode Toggle OFF):
Purpose: To generate a single, high-quality random number suitable for applications like generating a cryptographic key or a secure seed.
Process: You select a batch size (number of images). The app captures that many images with the lens covered, extracts and Von Neumann corrects the bits from each, concatenates all these corrected bits, and then applies the SHA-256 function to the entire combined bitstream.
Output: A single 256-bit random number (displayed as a 64-character hexadecimal string).
Advanced Mode (Advanced Mode Toggle ON):
Purpose: To generate a large volume of the intermediate random data (the Von Neumann corrected bitstream) for statistical randomness testing.
Process: You select a batch size. The app captures that many images, extracts and Von Neumann corrects the bits from each, and concatenates all these corrected bits into a single, very long bitstream. The SHA-256 step is skipped for the final output displayed.
Output: A summary showing the total number of bits generated. You can then use the "Save to File" button to export the entire long bitstream to a text file on your device. This file is your test data.
Why Environmental Conditions Matter
The app displays the camera cover status and battery temperature because these conditions can influence the camera sensor noise, and thus the randomness.
Covered Camera: It's crucial to cover the camera lens completely. Light hitting the sensor would introduce predictable patterns, overwhelming the random noise and making the output less random. The real-time luminance check helps you ensure the lens is properly covered.
Temperature: Temperature can affect the level of thermal noise in the sensor. While the Von Neumann corrector and SHA-256 help mitigate some temperature-related biases, being aware of the temperature during generation can be useful for understanding potential influences on the raw noise.
Proving Randomness: Statistical Testing
To prove that our TRNG is truly random and reliable, we use standard statistical test suites. These are sophisticated programs that analyze large bitstreams for various patterns that shouldn't exist in truly random data.
Here's the process:
Generate Test Data: Use the app in Advanced Mode with a large batch size (or multiple batches) to generate a large bitstream of Von Neumann corrected bits. Save this data to a text file using the "Save to File" feature. Aim for at least several megabytes of data.
Use Test Suites: Transfer the saved text file to a computer where you can run randomness test suites like NIST SP 800-22, Dieharder, or TestU01.
Run the Tests: Follow the instructions for the test suite to analyze your bitstream file.
Analyze Results: The test suite will provide a report indicating whether your data passes or fails various statistical tests (e.g., Frequency Test, Runs Test, Spectral Test, etc.).
Our Test Results (Placeholder):
(In your actual documentation/presentation, you will replace this section with the results from the tests you run. You can include tables or charts showing which tests passed and any relevant statistics like p-values. Discuss what the results mean for the randomness of your TRNG source.)
Based on our testing using [mention the test suite(s) used] on a bitstream of [mention the size of the bitstream] generated by our TRNG, the data [mention the overall outcome, e.g., "passed all tests," "passed most tests," etc.]. This demonstrates that the random noise captured by the camera sensor, combined with our extraction and correction methods, produces a statistically random bitstream.
Conclusion
Our Android TRNG project harnesses the natural, unpredictable noise within a camera sensor to generate true random numbers. By using techniques like LSB extraction, XOR, the Von Neumann corrector, and SHA-256 whitening, we can produce both large datasets for proving randomness and high-quality, fixed-size random outputs for practical applications. The ability to demonstrate the statistical randomness of our source through testing is key to showing the reliability of our TRNG.
Feel free to explore the code, contribute, and test the randomness yourself!
