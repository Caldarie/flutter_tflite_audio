# flutter_tflite_audio

This plugin allows you to use tflite to make audio/speech classifications. Can now support ios and android. 

If you have any feature requests or would like to contribute to this plugin, please do not hesistate to contact me.

![](audio_recognition_example.jpg)

### How to add tflite_audio as a dependency:
1. Add `tflite_audio` as a [dependency in your pubspec.yaml file]

### How to add tflite model and label to flutter:
1. Place your custom tflite model and labels into the asset folder. 
2. In pubsec.yaml, link your tflite model and label under 'assets'. For example:

```
  assets:
    - assets/conv_actions_frozen.tflite
    - assets/conv_actions_labels.txt

```

### How to use this plugin

1. Import the plugin. For example:

```
import 'package:tflite_audio/tflite_audio.dart';
```

2. Use the following futures to make use of this plugin. Please look at the [example](https://github.com/Caldarie/flutter_tflite_audio/tree/master/example) on how to implement these futures.

```dart
//Loads your model
//Higher numThreads will be reduce inference times, but is more intensive on cpu
 Future loadModel({model, label, numThreads, isAsset}) async {
    return await TfliteAudio.loadModel(model, label, numThreads, isAsset);
  }

 Future<dynamic> startAudioRecognition(
      {int sampleRate, int recordingLength, int bufferSize}) async {
    return await TfliteAudio.startAudioRecognition(
        sampleRate, recordingLength, bufferSize);
  }

```

3. Call the future loadModel() and assign the appropriate arguments. The values for numThread and isAsset are on default as shown below:

```dart
loadModel(
        model: "assets/conv_actions_frozen.tflite",
        label: "assets/conv_actions_labels.txt",
        numThreads: 1,
        isAsset: true);
```

4. Call the future startAudioRecognition() and assign values for the arguments:

    **sampleRate** - determines the number of samples per second

    **recordingLength** - determines the max length of the recording buffer. Make sure the value is <= your tensor input array

    **bufferSize** - A higher value has more latency, less cpu intensive, and shorter recording time. A lower value has less latency, more cpy intensive, and       longer recording time.
  
Please take a look at the example below. The values used  example model's input parameters.

```dart
  Future<String> startAudioRecognition() async {
     await startAudioRecognition(
           sampleRate: 16000, 
           recordingLength: 16000, 
           bufferSize: 1280)
  }

```


### Android 
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


### iOS
1. Add the following key to Info.plist for iOS. This ould be found in <YourApp>/ios/Runner
```
<key>NSMicrophoneUsageDescription</key>
<string>Record audio for playback</string>
```

2. Change the deployment target to at least 12.0. This could be done by:
  a. Open your project workspace on xcode
  b. Select root runner on the left hand side pannel
  c. Under the info tab, change the iOS deployment target to 12.0

3. Open your podfile in your iOS folder and change platform ios to 12. Also make sure that use_frameworks! is under runner. For example

```
platform :ios, '12.0'
```

```
target 'Runner' do
  use_frameworks! #Make sure you have this line
  use_modular_headers!

  flutter_install_all_ios_pods File.dirname(File.realpath(__FILE__))
end
```

## References

https://github.com/tensorflow/examples/tree/master/lite/examples/speech_commands
