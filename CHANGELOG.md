## 0.3.0
* BREAK CHANGE: Recording bufferSize now takes in 2x the number of samples. To keep the same recording length, simply divide your previous bufferSize by 2.
* Experimental: Support MFCC, melSpectrogram and spectrogram inputs
* Feature: Can automatically or manually set audio length
* Feature: Can automatically or manually transpose input shape
* Improvement: Stability of asyncronous operations with RxJava and RxSwift
* Improvement: (iOS) Removed meta info when extracting data from audio file.
* Improvement: (Android) Splicing algorithm passes all test case. Audio recogntion should now be more accurate.
* Fixed: (iOS) Duplicate symbol error. Set version of TensorFlowLite to 2.6.0. Problem found [here][i25].
* Fixed: (Android & iOS) Incorrect padding when splicing audio file. All test cases have passed.

[i25]: https://github.com/Caldarie/flutter_tflite_audio/issues/25
  
## 0.2.2+4
* Handled NaN exception for raw output on swift

## 0.2.2+3
* Hot fixed iOS issue where it will record indefintetly.

## 0.2.2+2
* Hot fixed missing AudioProcessing class.

## 0.2.2+1
* Hot fixed issue with unresponsive forced stop recognition.

## 0.2.2
* Feature: Added ability to recognise stored audio files
* Breaking Change: RecordingLength will no longer be required as a parameter.
* Fixed: NaN output for bufferRates that are non divisible to audioLength 
* Fixed: android permission error when granted outside app.

## 0.2.1+2
* Fixed NaN raw score output for Android.

## 0.2.1+1
* Fixed inaccurate numOfInference count for iOS and android.

## 0.2.1
* Improved recognition accuracy for Google Teachable Machine models
* Fixed memory crash on android
* Improved memory performance on iOS
* Added feature to output raw scores
* moved inputType to loadModel() instead of startAudioRecognition()

## 0.2.0
* Fixed crashed on Android when force stopping recognition
* Improve recognition latency on android by reducing number of event calls.

## 0.1.9
* Added support for android V2 embedding
* Breaking change - no longer supports deprecated versions of Android (pre 1.12)

## 0.1.8+2
* Fixed null safety incompatability with example 

## 0.1.8+1
* Fixed the problem with bridge NSNumber to Float
* Merged rawAudioRecognize() and decodedWavRecognize() on native platforms
* Set detection parameters to 0 for better performance.

## 0.1.8
* Added null safety compatability

## 0.1.7+1
* Hotfixed iOS crash when casting double to float for detectionThreshold

## 0.1.7
* Fixed iOS bug where stream wont close when permission has been denied.
* Added feature where you can adjust the detection sensitivity of the model

## 0.1.6+2
* Fixed podsec error
* Fixed iOS incompatability with fluter 2.0.0

## 0.1.6+1
* Hotfixed missing value for recording.

## 0.1.6
* bufferSize no longer needs to be divisible to recording length. 

## 0.1.5+3
* Fixed major android crash, where forcibly stopping the stream causes recorder.stop() to be prematurely called.
* Fixed minor iOS crash, where forcibly stopping the stream during recognition returns a nil exception.
* Cleaned up example for easy switch between decodedWav and Google's Teachable Machine model

## 0.1.5+2
* Disabled Google's Teachable Machine by default to reduce app footprint. (This can be enabled manually)
* Adjusted example's values to improve inference accuracy

## 0.1.5+1
* Added documentation
* Added example model from Google's Teachable Machine.
* Fixed iOS crash when loading text file with empty elements.

## 0.1.5
* Added support for Google Teachable Machine models.
* Fixed inaccurate reading with recording
* Added feature to switch between decodedwav and Google's Teachable machine model.

## 0.1.4
* Added a new feature where you can run multiple inferences per recording.
* Replaced future with stream when getting results from inferences
* Added a button to cancel the stream / inference
* Removed unnecessary code for easier reading.

## 0.1.3+1
* Used reusable widgets for easier to read code.
* Added some documentation 

## 0.1.3
* Hotfix for crash when permission has been denied.
* Added the key 'hasPermission' for the future startAudioRecognitions().
* Added feature in example where it'll show inference times

## 0.1.2
* Instead of returning a single string value, the future startAudioRecognition() now returns a map with the following keys:
    - recognitionResult 
    - inferenceTime
* Fixed issue in example where pressing the record button multiple times will crash the app.
* Added feature in example where pressing the recording button changes color.

## 0.1.1
* Made some fixes with making options explicit
* Added alert dialog when permission is denied.

## 0.1.0
* Added iOS support

## 0.0.4
* Added the following arguments into the future: startAudioRecognition()
    - sampleRate
    - audioLength
    - bufferSize

## 0.0.3
* Merged permission and audio recognition futures into one future.

## 0.0.2
* Fixed image url

## 0.0.1

* Initial release.
