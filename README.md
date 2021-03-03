# TFlite Audio Plugin for Flutter

[![pub package](https://img.shields.io/pub/v/tflite_audio.svg?label=version&color=blue)](https://pub.dev/packages/tflite_audio)
[![likes](https://badges.bar/tflite_audio/likes)](https://pub.dev/packages/tflite_audio/score)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![style: effective dart](https://img.shields.io/badge/style-effective_dart-40c4ff.svg)](https://pub.dev/packages/effective_dart)

This plugin allows you to use tflite to make audio/speech classifications. Supports iOS and Android. The plugin can support several model types:

1. **(Beginner)** [Google Teachable Machine](https://teachablemachine.withgoogle.com/train/audio), which requires little ML knowledge and coding.

2. **(Advanced)** Supports models with decoded wave inputs. For more information on how to train your own model:
   * For a simple guide, take a look [here](https://www.tensorflow.org/tutorials/audio/simple_audio). (Model in tutorial not compatabile with this plugin)
   * For a detailed guide, take a look [here](https://github.com/tensorflow/docs/blob/master/site/en/r1/tutorials/sequences/audio_recognition.md)
   * To train a decoded wave with MFCC, take a look at the example [here](https://github.com/tensorflow/tensorflow/tree/r1.15/tensorflow/examples/speech_commands)
   * To train a raw model, take a look [here](https://github.com/tensorflow/examples/tree/master/lite/examples/speech_commands/ml).

3. **(Future feature)**  Adjustable input size

4. **(Future feature)**  Model with mutliple outputs

5. **(Future feature)**  Audio Embeddings

To keep this project alive, please consider being a contributer. Technology is always evolving, and constant maintenance is required. A star is also appreciated.

<br>

Recording            |  Inference result
:-------------------------:|:-------------------------:
![](https://github.com/Caldarie/flutter_tflite_audio/blob/master/pictures/finish.png) | ![](https://github.com/Caldarie/flutter_tflite_audio/blob/master/pictures/start.png) 

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

### **a) Model won't load** 

You need to configures permissions and dependencies to use this plugin. Please follow the steps below:
* [Android installation & permissions](#android-installation--permissions)
* [iOS installation & permissions](#ios-installation--permissions)

### **b) Inference isn't accurate** 

Its possible that your device doesn't have enough time to record. Simply adjust the bufferSize to a lower value. Likewise, if your bufferSize is too low, the recording length will be too long and your model may possibly register it as background noise. Simply adjust the bufferSize to a higher value.

### **c) TensorFlow Lite Error: Regular TensorFlow ops are not supported by this interpreter. Make sure you apply/link the Flex delegate before inference** 

Please make sure that you have enabled ops-select on your [podfile - step 4 & Xcode - step 5](#ios-if-you-are-using-googles-teachable-machine-model-otherwise-skip) and [build gradle - step 3](#android-if-you-are-using-googles-teachable-machine-otherwise-skip)

If you tried above, please run the example on a device (not emulator). If you still recieved this error, its very likely that theres an issue with cocoapod or Xcode configuration. Please check the [issue #7](https://github.com/Caldarie/flutter_tflite_audio/issues/7)

If you recieved this error from your custom model (not GTM), its likely that you're using unsupported tensorflow features for tflite, as found in [issue #5](https://github.com/Caldarie/flutter_tflite_audio/issues/5#issuecomment-789260402). Forr more details, search [here](https://github.com/tensorflow/tensorflow/issues/44543)

### **d) (iOS) App crashes when running Google's Teachable Machine model** 

Please run your simulation on actual iOS device. As of this moment, there's [limited support](https://github.com/tensorflow/tensorflow/issues/44997#issuecomment-734001671) for x86_64 architectures from the Tensorflow Lite select-ops framework. If you absolutely need to run it on an emulator, you can consider building the select ops framework yourself. Instructions can be found [here](https://www.tensorflow.org/lite/guide/ops_select#ios)

### **e) (Android) Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0xfffffff4 in tid 5403** 

It seems like the latest tflite package for android is causing this issue. Until this issue is fixed, please run this package on an actual Android Device. 

### **f) Failed to invoke the interpreter with error: Provided data count (number) must match the required count (number).** 

Please make that your recording length matches your model input size. For example, google's teachable machine requires recording length is 44032

<br>

**If you have found any other issues not listed above, please create a new issue.**

<br>

## Please read if you are using Google's Teachable Machine. Otherwise skip.

**BE AWARE:** Google's Teachable Machine requires [select tensorflow operators](https://www.tensorflow.org/lite/guide/ops_select#using_bazel_xcode) to work. This feature is experimental and will cause the following issues:

1. Increase the overall size of your app. If this is unnacceptable for you, it's recommended that you build your own custom model using the [tutorial here](https://www.tensorflow.org/tutorials/audio/simple_audio) 

2. Emulators for iOS do not work due to limited support for x86_64 architectures. You need to run your simulation on an actual device. Issue can be found [here](https://github.com/tensorflow/tensorflow/issues/44997)
  
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

  * bufferSize - Make sure this value is equal or below your recording length. Be aware that a higher value may not allow the recording enough time to capture your voice. A lower value will give more time, but it'll be more cpu intensive. Remember that the optimal value varies depending on the device.
    
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


6. Install the ops-select package to pod. To do this:

    a. cd into iOS folder

    b. Run `flutter pub get` on terminal

    c. Run `pod install` on terminal

    d. Run `flutter clean` on terminal
    
<br>

## References

1. https://github.com/tensorflow/examples/tree/master/lite/examples/speech_commands
2. https://www.tensorflow.org/lite/guide/ops_select
