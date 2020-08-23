# flutter_tflite_audio

This plugin allows you to use tflite to make audio/speech classifications. Currently supports android, however will update with an IOS version soon.


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

2. Load your model by linking the model and labels. The values for numThread and isAsset are on default as shown below:

```dart
loadModel(
        model: "assets/conv_actions_frozen.tflite",
        label: "assets/conv_actions_labels.txt",
        numThreads: 1,
        isAsset: true);
```

3. Use the following callbacks to make full use of the plugin. Please look at the [example](https://github.com/Caldarie/flutter_tflite_audio/tree/master/example) on how to implement the plugins

```dart
//Loads your model
 Future loadModel({model, label, numThreads, isAsset}) async {
    return await TfliteAudio.loadModel(model, label, numThreads, isAsset);
  }

//Checks if the user has permissions for voice recording
  Future<bool> checkPermissions() async {
    return await TfliteAudio.checkPermissions;
  }

//Asks for permission should the user have no permissions
  Future<bool> requestPermissions() async {
    return await TfliteAudio.requestPermissions();
  }

//Starts recording and then audio recogntion. Returns the result as a string value.
  Future<String> startRecognition() async {
    return await TfliteAudio.startRecognition();
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


### iOS [Tentative]
Also add the following key to Info.plist for iOS
```
<key>NSMicrophoneUsageDescription</key>
<string>Record audio for playback</string>
```
