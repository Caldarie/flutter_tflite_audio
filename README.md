# flutter_tflite_audio

This plugin allows you to use tflite to make audio/speech classifications. Supports iOS and Android. The plugin can support two types of models:

1. **(Beginner)** If you are new to machine learning, this package supports audio models from [Google Teachable Machine](https://teachablemachine.withgoogle.com/train/audio), which requires little ML knowledge and coding. This model uses a raw audio  **float32[1, 44032]** as the input.
2. **(Advanced)** Also supports models with decoded wave inputs. If you want to code your own model, use the [Tutorial here](https://www.tensorflow.org/tutorials/audio/simple_audio) as a guide. This model uses decodedwav, which uses two inputs. **float32[recording_length, 1]** for raw audio data and **int32[1]** as the sample rate

If there are any problems with the plugin, please do not hesistate to create an issue on github.

## What this plugin can do:

1. Switch between decodedwav and rawAudio inputs.
2. Run a stream and collect inference results over time
3. Loop inferences multiples at the user's specification.
4. Manually/forcibly close the inference stream/recording.


![](audio_recognition_example.jpg)


## Please read if you are using Google's Teachable Machine on iOS. 

Skip this section if the heading above does not apply to you.

**BE AWARE:** You need to run your simulation on an actual device. Emulators do not work due to limited support for x86_64 architectures.
  1. https://github.com/tensorflow/tensorflow/issues/41876
  2. https://github.com/tensorflow/tensorflow/issues/44997


**BE AWARE:** Be sure to follow **step 4** correctly, under [iOS Installation & Permissions](https://github.com/Caldarie/flutter_tflite_audio/tree/feature/google_teachable_machine_compatability#ios-installation--permissions)

## How to add tflite_audio as a dependency:
1. Add `tflite_audio` as a [dependency in your pubspec.yaml file]


## How to add tflite model and label to flutter:
1. Place your custom tflite model and labels into the asset folder. 
2. In pubsec.yaml, link your tflite model and label under 'assets'. For example:

```
  assets:
    - assets/conv_actions_frozen.tflite
    - assets/conv_actions_labels.txt

```

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


3. To start and listen to the stream startAudioRecognition for inference results:

```dart
TfliteAudio.startAudioRecognition(
  numOfInferences: 1,

  //parameters for google's teachable machine model. 
  inputType: 'rawAudio',
  sampleRate: 44100,
  recordingLength: 44032,
  bufferSize: 22016,

  // parameters for decodedwav models
  // inputType: 'decodedWav',
  // sampleRate: 16000,
  // recordingLength: 16000,
  // bufferSize: 8000,
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

5. For a rough guide on the parameters
  
  * numThreads -  Higher threads will reduce inferenceTime. However, cpu usage will be higher.
  
  * numOfInferences - determines the number of inferences you want to loop.

  * sampleRate - determines the number of samples per second. Recommened values are 16000, 22050, 44100

  * recordingLength - determines the size of your tensor input. If the value is not below or equal to your tensor input, it will crash.

  * bufferSize - Make sure this value is equal or below your recording length. A very high value may not allow the recording enough time to capture your voice. A lower value will give more time, but it'll be more cpu intensive Remember that this value varies depending on your device.
    


## Android Installation & Permissions
Add the permissions below to your AndroidManifest. This could be found in  <YourApp>/android/app/src folder. For example:

```
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

Edit the following below to your build.gradle. This could be found in <YourApp>/app/src/For example:

```
aaptOptions {
        noCompress 'tflite'
```

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
    

3. Open your podfile in your iOS folder and change platform ios to 12. Also make sure that use_frameworks! is under runner. For example

```
platform :ios, '12.0'
```

```ruby
target 'Runner' do
  use_frameworks! #Make sure you have this line
  use_modular_headers!

  flutter_install_all_ios_pods File.dirname(File.realpath(__FILE__))
end
```

4. If you are using Google Teachable Machine's model, you need to force load Select Ops for Tensorflow. To do that:

    a. Open your project on xcode 
    
    b. click on runner under "Targets"
  
    c. Click on "Build settings" tab

    d. Click on "All" tab

    e. Click on the empty space which is on the right side of "Other Links Flag"

    f. Add: `-force_load $(SRCROOT)/Pods/TensorFlowLiteSelectTfOps/Frameworks/TensorFlowLiteSelectTfOps.framework/TensorFlowLiteSelectTfOps`

![](tflite-select-ops-installation.png)

    For more details, please visit this site:
    https://www.tensorflow.org/lite/guide/ops_select#ios

## References

https://github.com/tensorflow/examples/tree/master/lite/examples/speech_commands
