# flutter_tflite_audio

This plugin allows you to use tflite to make audio/speech classifications. Supports iOS and Android. The plugin can support two types of models:

1. **(Beginner)** If you are new to machine learning, this package supports audio models from [Google Teachable Machine](https://teachablemachine.withgoogle.com/train/audio), which requires little ML knowledge and coding. This model uses raw audio  **float32[1, 44032]** as the input.

2. **(Advanced)** Also supports models with decoded wave inputs. If you want to code your own model, use the [Tutorial here](https://www.tensorflow.org/tutorials/audio/simple_audio) as a guide. This model uses decodedwav, which uses two inputs. **float32[recording_length, 1]** for raw audio data and **int32[1]** as the sample rate

3. **(Future feature)**  Will support audio embeddings models in the future.

To keep this project alive, please consider contributing to this project, providing constructive feedback or making future requests. A like or a star would also be greatly appreciated too.

<br>

![](audio_recognition_example.jpg)

<br>

## Table of Contents

 * [Known Issues](#known-issues)
 * [Please read if you are using Google's Teachable Machine. Otherwise skip.](#please-read-if-you-are-using-googles-teachable-machine-otherwise-skip)
 * [How to add tflite model and label to flutter](#how-to-add-tflite-model-and-label-to-flutter)
 * [How to use this plugin](#how-to-use-this-plugin)
 * [Rough guide on parameters](#rough-guide-on-the-parameters)
 * [Android Installation & Permissions](#android-installation--permissions)
   * [If you are using Google's Teachable Machine. Otherwise skip.](#android-if-you-are-using-googles-teachable-machine-otherwise-skip)
 * [iOS Installation & Permissions](#ios-installation--permissions)
   * [If you are using Google's Teachable Machine. Otherwise skip.](#ios-if-you-are-using-googles-teachable-machine-model-otherwise-skip)
 * [References](#references)
 
<br>

## Known Issues

1. **Inference isn't accurate** - Its possible that your device doesn't have enough time to record. Simply adjust the bufferSize to a lower value. Likewise, if your bufferSize is too low, the recording length is too long and your model may possibly register it as background noise. Simply adjust the bufferSize to a higher value.

2. **App crashes when runnning GTM model on both android and iOS** - To reduce your app's footprint, this package has disabled GTM feature by default. You will need to manually enable ops-select on your [podfile - step 4 & Xcode - step 5](#ios-if-you-are-using-googles-teachable-machine-model-otherwise-skip) and [build gradle - step 3](#android-if-you-are-using-googles-teachable-machine-otherwise-skip)

3. **App crashes when running GTM model on iOS emulator** - please run your simulation on actual iOS device. Tensorflow support for x86_64 architectures have limited support.

<br>

## Please read if you are using Google's Teachable Machine. Otherwise skip.

**BE AWARE:** Google's Teachable Machine requires [select tensorflow operators](https://www.tensorflow.org/lite/guide/ops_select#using_bazel_xcode) to work. This feature is experimental and will cause the following issues:

1. Increase the overall size of your app. If this is unnacceptable for you, it's recommended that you build your own custom model using the [tutorial here](https://www.tensorflow.org/tutorials/audio/simple_audio) 

2. Emulators for iOS do not work due to limited support for x86_64 architectures. You need to run your simulation on an actual device. Issue can be found below:

  * https://github.com/tensorflow/tensorflow/issues/44997
  
3. You will need to manually implement ops-select on your [podfile - step 4 & Xcode - step 5](#ios-if-you-are-using-googles-teachable-machine-model-otherwise-skip) and [build gradle - step 3](#android-if-you-are-using-googles-teachable-machine-otherwise-skip)

<br>

## How to add tflite model and label to flutter:
1. Place your custom tflite model and labels into the asset folder. 
2. In pubsec.yaml, link your tflite model and label under 'assets'. For example:

```
  assets:
    - assets/decoded_wav_model.tflite
    - assets/decoded_wav_label.txt

```

<br>

## How to use this plugin
Please look at the [example](https://github.com/Caldarie/flutter_tflite_audio/tree/master/example) on how to implement these futures.


1. Import the plugin. For example:

```
import 'package:tflite_audio/tflite_audio.dart';
```


2. To load your model:


```dart
   TfliteAudio.loadModel(
        model: 'assets/conv_actions_frozen.tflite',
        label: 'assets/conv_actions_labels.txt',
        numThreads: 1,
        isAsset: true);
```


3. To start and listen to the stream for inference results:

Example for Google's Teachable Machine models

```dart
TfliteAudio.startAudioRecognition(
  numOfInferences: 1,
  inputType: 'rawAudio',
  sampleRate: 44100,
  recordingLength: 44032,
  bufferSize: 22016,
  )
    .listen(
      //Do something here to collect data
      )
    .onDone(
       //Do something here when stream closes
      );
```

Example for decodedwav models

```dart
TfliteAudio.startAudioRecognition(
  numOfInferences: 1,
  inputType: 'decodedWav',
  sampleRate: 16000,
  recordingLength: 16000,
  bufferSize: 2000,
  )
    .listen(
      //Do something here to collect data
      )
    .onDone(
       //Do something here when stream closes
      );
```

4. To forcibly cancel the stream and recognition while executing:

```dart
TfliteAudio.stopAudioRecognition();
```

## Rough guide on the parameters
  
  * numThreads -  Higher threads will reduce inferenceTime. However, cpu usage will be higher.
  
  * numOfInferences - determines how many times you want to repeat the inference.

  * sampleRate - A higher sample rate will improve accuracy. Recommened values are 16000, 22050, 44100

  * recordingLength - determines the size of your tensor input. If the value is not equal to your tensor input, it will crash.

  * bufferSize - Make sure this value is equal or below your recording length. To lower bufferSize, its important to divide its recording_length by 2. For example 44032, 22016, 11008, 5504... Be aware that a higher value may not allow the recording enough time to capture your voice. A lower value will give more time, but it'll be more cpu intensive. Remember that the optimal value varies depending on the device.
    
<br>

## Android Installation & Permissions
1. Add the permissions below to your AndroidManifest. This could be found in  <YourApp>/android/app/src folder. For example:

```
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

2. Edit the following below to your build.gradle. This could be found in <YourApp>/app/src/For example:

```Gradle
aaptOptions {
        noCompress 'tflite'
```

<br>

#### (Android) If you are using Google's Teachable Machine. Otherwise skip.

3. Enable select-ops under dependencies in your build gradle.

```Gradle
dependencies {
    compile 'org.tensorflow:tensorflow-lite-select-tf-ops:+'
}
```

<br>

## iOS Installation & Permissions
1. Add the following key to Info.plist for iOS. This ould be found in <YourApp>/ios/Runner
```
<key>NSMicrophoneUsageDescription</key>
<string>Record audio for playback</string>
```

2. Change the deployment target to at least 12.0. This could be done by:

    a. Open your project workspace on xcode
  
    b. Select root runner on the left panel
  
    c. Under the info tab, change the iOS deployment target to 12.0
    

3. Open your podfile in your iOS folder and change platform ios to 12. 

```ruby
platform :ios, '12.0'
```

<br>

#### (iOS) If you are using Google's Teachable Machine model. Otherwise skip.

4. Add `pod 'TensorFlowLiteSelectTfOps' under target.

```ruby
target 'Runner' do
  use_frameworks! 
  use_modular_headers!
  pod 'TensorFlowLiteSelectTfOps' #Add this line here. 

  flutter_install_all_ios_pods File.dirname(File.realpath(__FILE__))
end
```

5. Force load Select Ops for Tensorflow. To do that:

    a. Open your project on xcode 
    
    b. click on runner under "Targets"
  
    c. Click on "Build settings" tab

    d. Click on "All" tab

    e. Click on the empty space which is on the right side of "Other Links Flag"

    f. Add: `-force_load $(SRCROOT)/Pods/TensorFlowLiteSelectTfOps/Frameworks/TensorFlowLiteSelectTfOps.framework/TensorFlowLiteSelectTfOps`

![](tflite-select-ops-installation.png)

    For more details, please visit this site:
    https://www.tensorflow.org/lite/guide/ops_select#ios


6. Install the ops-select package to pod. To do this:

    a. cd into iOS folder

    b. Run `flutter pub get` on terminal

    c. Run `pod install` on terminal

    d. Run `flutter clean` on terminal
    
<br>

## References

https://github.com/tensorflow/examples/tree/master/lite/examples/speech_commands
