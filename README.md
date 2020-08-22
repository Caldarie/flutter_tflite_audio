# flutter_tflite_audio

My first plugin for flutter. This plugin allows you to use tflite to make audio/speech classifications. Currently supports android, however will update with an IOS version soon.

If you have any feedback, or would like to help contribute in improving this plugin, please do not hesistate to let me know.

## Usage
To use this plugin, add `audio_processing` as a [dependency in your pubspec.yaml file]

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
Also add the following key to Info.plist for iOS
```
<key>NSMicrophoneUsageDescription</key>
<string>Record audio for playback</string>
```
