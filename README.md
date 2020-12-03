# flutter_tflite_audio

This plugin allows you to use tflite to make audio/speech classifications. Supports iOS and Android. The plugin can support two types of models:

1. (Beginner) If you are new to machine learning, this package supports audio models from [Google Teachable Machine](https://teachablemachine.withgoogle.com/train/audio), which requires little ML knowledge and coding. This model uses a raw audio  float32[1, 44032] as the input.
2. (Advanced) Also supports models with decoded wave inputs. If you want to code your own mode, use the [Tutorial here](https://www.tensorflow.org/tutorials/audio/simple_audio) as a guide. This model uses decodedwav, which uses two inputs. float32[recording_length, 1] for raw audio data and int32[1] as the sample rate


The plugin can do the following tasks:

1. Change input type of the model
2. Run a stream and collect inference results over time
3. Can loop inferences once or multiple times, which can be specified in the parameters
4. Can manually close the inference and/or recording at the end user's discretion.

If there are any problems with the plugin, please do not hesistate to create an issue or request features on github.

![](audio_recognition_example.jpg)


## Limitations

BE AWARE: For iOS users, you need to run your simulation on an actual device. Emulators do not work due to limited support for x86_64 architectures.
https://github.com/tensorflow/tensorflow/issues/41876
https://github.com/tensorflow/tensorflow/issues/44997

Android devices should work fine on emulators.

BE AWARE: For iOS, select ops still do not work seamlessly. Be sure to follow [step 4](#ios-installation-Permissions) correct, under "iOS Installation & Permissions"


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
 //1. call the the future loadModel()
 //2. assign the appropriate values to the arguments like below:
   TfliteAudio.loadModel(
        model: 'assets/conv_actions_frozen.tflite',
        label: 'assets/conv_actions_labels.txt',
        numThreads: 1,
        isAsset: true);
```


3. To collect the results from the stream, invoke startAudioRecognition. Depending on the type of model, choose one of the following below:

a) For those using Googles Teachable Machine.

```dart
//For decoded wav, use these parameters
TfliteAudio.startAudioRecognition(
        inputType: 'rawAudio'
        sampleRate: 44100, 
        recordingLength: 44032, 
        bufferSize: 22016, //For longer recording time, use a lower number
        numOfInferences: 1)
          .listen(
            //Do something here to collect data
          )
          .onDone(
            //Do something here when stream closes
          );
```

b) For those using decoded wav:

```dart
//For decoded wav, use these parameters
TfliteAudio.startAudioRecognition(
        inputType: 'decodedWav'
        sampleRate: 16000, 
        recordingLength: 16000, 
        bufferSize: 8000,
        numOfInferences: 1)
          .listen(
            //Do something here to collect data
          )
          .onDone(
            //Do something here when stream closes
          );
```

4. To forcibly cancel the stream and recognition while executing

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

  a. Click on runner under "Targets"
  
  b. Click on "Build settings" tab

  c. Click on "All" tab

  d. Click on the empty space which is on the right side of "Other Links Flag"

  e. Add: `-force_load $(SRCROOT)/Pods/TensorFlowLiteSelectTfOps/Frameworks/TensorFlowLiteSelectTfOps.framework/TensorFlowLiteSelectTfOps`

## References

https://github.com/tensorflow/examples/tree/master/lite/examples/speech_commands
