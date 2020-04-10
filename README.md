# audio_processing

Tensorflow plugin for audio classification

## Usage
To use this plugin, add `audio_processing` as a [dependency in your pubspec.yaml file]

### Android
Add the following permissions to your Android Manifest
```
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```


### iOS
Also add the following key to Info.plist for iOS
```
<key>NSMicrophoneUsageDescription</key>
<string>Record audio for playback</string>
```
