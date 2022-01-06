# TFlite Audio Plugin for Flutter

[![pub package](https://img.shields.io/pub/v/tflite_audio.svg?label=version&color=blue)](https://pub.dev/packages/tflite_audio)
[![likes](https://badges.bar/tflite_audio/likes)](https://pub.dev/packages/tflite_audio/score)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![style: effective dart](https://img.shields.io/badge/style-effective_dart-40c4ff.svg)](https://pub.dev/packages/effective_dart)

<br>

Audio classification Tflite package for flutter (iOS & Android). Can also support Google Teachable Machine models. 

If you are a complete newbie to audio classification, you can read the tutorial [here](https://carolinamalbuquerque.medium.com/audio-recognition-using-tensorflow-lite-in-flutter-application-8a4ad39964ae). Credit to [Carolina](https://github.com/cmalbuquerque) for writing a comprehensive article.

To keep this project alive, consider giving a star or a like. Pull requests or bug reports are also welcome.

<br>

Recording            |  Inference result
:-------------------------:|:-------------------------:
![](https://github.com/Caldarie/flutter_tflite_audio/blob/master/pictures/finish.png) | ![](https://github.com/Caldarie/flutter_tflite_audio/blob/master/pictures/start.png) 

<br>

## Table of Contents

 * [About this plugin](#about-this-plugin)
 * [Known Issues/Commonly asked questions](#known-issuescommonly-asked-questions)
 * [Please read if you are using Google's Teachable Machine. Otherwise skip.](#please-read-if-you-are-using-googles-teachable-machine-otherwise-skip)
 * [How to add tflite model and label to flutter](#how-to-add-tflite-model-and-label-to-flutter)
 * [How to use this plugin](#how-to-use-this-plugin)
 * [Rough guide on parameters](#rough-guide-on-the-parameters)
 * [Android Installation & Permissions](#android-installation--permissions)
 * [iOS Installation & Permissions](#ios-installation--permissions)
 * [References](#references)
 
<br>

## About This Plugin

<br>

The plugin allows you to run inferences on recording or stored audio.  It can also support several model types:

<br>

1. Models from Google Teachable Machine

   * For beginners with little to no machine learning knowledge. You can read can read the tutorial [here](https://carolinamalbuquerque.medium.com/audio-recognition-using-tensorflow-lite-in-flutter-application-8a4ad39964ae) if you are a newbie.
   * Training can be done [here](https://teachablemachine.withgoogle.com/train/audio) 
  
<br>

1. Raw audio inputs. 

   * Can recognize the following inputs: float32[recordingLength, 1] or float32[1, recordingLength]
   * For more information on how to train your own model, take a look [here](https://github.com/tensorflow/examples/tree/master/lite/examples/speech_commands/ml).

<br>

2. Supports models with decoded wave inputs. 

   * Supports two inputs: float32[recordingLength, 1] and int32[1]
   * For more information on how to train your own model. Take a look [here](https://github.com/tensorflow/docs/blob/master/site/en/r1/tutorials/sequences/audio_recognition.md)
   * To train a decoded wave with MFCC, take a look [here](https://github.com/tensorflow/tensorflow/tree/r1.15/tensorflow/examples/speech_commands)

<br>

3. **(Currently worked on feature)** Raw audio with additional dynamic inputs. Take a look at this [branch](https://github.com/Caldarie/flutter_tflite_audio/tree/experimental_dynamic_input) for work on progress

    * Supports two inputs: float32[recordingLength, 1] and float32[dynamic input, 1] 
    * Also supports reverse inputs: float32[1, recordingLength] and float32[1, dynamic input] 
    * Will support dynamic outputs
    * Will add dynamic support for different input/output data types
    * Add support on iOS

<br>

4. **(Future feature)**  Spectogram as an input type. Will support model from this [tutorial](https://www.tensorflow.org/tutorials/audio/simple_audio). 

<br>

## Known Issues/Commonly asked questions

<br/>

1. **My Model won't load**

   You need to configures permissions and dependencies to use this plugin. Please follow the steps below:

   * [Android installation & permissions](#android-installation--permissions)
  
   * [iOS installation & permissions](#ios-installation--permissions)         
<br/>

2. **How to adjust the recording length/time**

   There are two ways to reduce adjust recording length/time:

   * You can increase the recording time by adjusting the bufferSize to a lower value. 
  
   * You can also increase recording time by lowering the sample rate.     
  
   **Note:** That stretching the value too low will cause problems with model accuracy. In that case, you may want to consider lowering your sample rate as well. Likewise, a very low sample rate can also cause problems with accuracy. It is your job to find the sweetspot for both values.

<br/>

3. **How to reduce false positives in my model**

   To reduce false positives, you may want to adjust the default values of `detectionThreshold=0.3` and `averageWindowDuration=1000` to a higher value. A good value for both respectively are `0.7`  and `1500`. For more details about these parameters, please visit this [section](#rough-guide-on-the-parameters).

<br/>

4. **I am getting build errors on iOS**

   There are several ways to fix this:

   * Some have reported to fix this issue by replacing the following line:

     ```ruby
     target 'Runner' do
       use_frameworks! 
       use_modular_headers!
       #pod 'TensorFlowLiteSelectTfOps' #Old line
       pod'TensorFlowLiteSelectTfOps','~> 2.6.0' #New line

       flutter_install_all_ios_pods File.dirname(File.realpath(__FILE__))
     end
     ```

   * Others have fixed this issue building the app without the line: `pod 'TensorFlowLiteSelectTfOps`. Then rebuilding the app by re-adding the line again.


<br/>

5. **I am getting TensorFlow Lite Error on iOS. -  Regular TensorFlow ops are not supported by this interpreter. Make sure you apply/link the Flex delegate before inference** 

   *  Please make sure that you have enabled ops-select on your [podfile - step 4 & Xcode - step 5](#ios-if-you-are-using-googles-teachable-machine-model-otherwise-skip) and [build gradle - step 3](#android-if-you-are-using-googles-teachable-machine-otherwise-skip)

   * If you tried above, please run the example on a device (not emulator). If you still recieved this error, its very likely that theres an issue with cocoapod or Xcode configuration. Please check the [issue #7](https://github.com/Caldarie/flutter_tflite_audio/issues/7)

   * If you recieved this error from your custom model (not GTM), its likely that you're using unsupported tensorflow operators for tflite, as found in [issue #5](https://github.com/Caldarie/flutter_tflite_audio/issues/5#issuecomment-789260402). For more details on which operators are supported, look at the official documentation [here](https://www.tensorflow.org/lite/guide/ops_compatibility)

   * Take a looking at issue number 4 if none of the above works.  

<br/>

6. **(iOS) App crashes when running Google's Teachable Machine model** 

   Please run your simulation on actual iOS device. Running your device on M1 macs should also be ok.
  
   As of this moment, there's [limited support](https://github.com/tensorflow/tensorflow/issues/44997#issuecomment-734001671) for x86_64 architectures from the Tensorflow Lite select-ops framework. If you absolutely need to run it on an emulator, you can consider building the select ops framework yourself. Instructions can be found [here](https://www.tensorflow.org/lite/guide/ops_select#ios)

<br/>

7. **(Android) Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0xfffffff4 in tid 5403** 

   It seems like the latest tflite package for android is causing this issue. Until this issue is fixed, please run this package on an actual Android Device. 

<br>

## Please Read If You Are Using Google's Teachable Machine. (Otherwise Skip)

<br>

**BE AWARE:** Google's Teachable Machine requires [select tensorflow operators](https://www.tensorflow.org/lite/guide/ops_select#using_bazel_xcode) to work. This feature is experimental and will cause the following issues:

1. Increase the overall size of your app. If this is unnacceptable for you, it's recommended that you build your own custom model. Tutorials can be found in the [About this plugin section](#about-this-plugin)

2. Emulators for iOS do not work due to limited support for x86_64 architectures. You need to run your simulation on an actual device. Issue can be found [here](https://github.com/tensorflow/tensorflow/issues/44997)
  
3. You will need to manually implement ops-select on your [podfile - step 4 & Xcode - step 5](#note-skip-below-if-your-are-not-using-google-teachable-machine-ios) and [build gradle - step 3](#note-skip-below-if-your-are-not-using-google-teachable-machine-android)

<br>

## How to add tflite model and label to flutter:

<br>

1. Place your custom tflite model and labels into the asset folder. 
2. In pubsec.yaml, link your tflite model and label under 'assets'. For example:

```
  assets:
    - assets/decoded_wav_model.tflite
    - assets/decoded_wav_label.txt

```

<br>

## How to use this plugin

<br>

Please look at the [example](https://github.com/Caldarie/flutter_tflite_audio/tree/master/example) on how to implement these futures.


1. Import the plugin. For example:

```
import 'package:tflite_audio/tflite_audio.dart';
```


2. To load your model:


```dart
  //Example for decodedWav models
   TfliteAudio.loadModel(
        model: 'assets/conv_actions_frozen.tflite',
        label: 'assets/conv_actions_label.txt',
        inputType: 'decodedWav');


  //Example for Google's Teachable Machine models
    TfliteAudio.loadModel(
        model: 'assets/google_teach_machine_model.tflite',
        label: 'assets/google_teach_machine_label.txt',
        inputType: 'rawAudio');

  //Example if you want to take advantage of all optional parameters from loadModel()
    TfliteAudio.loadModel(
      model: 'assets/conv_actions_frozen.tflite',
      label: 'assets/conv_actions_label.txt',
      inputType: 'decodedWav',
      outputRawScores: false, 
      numThreads: 1,
      isAsset: this.isAsset,
    );
```


3. To start and listen to the stream for inference results:

    * Declare stream value
      ```dart
      Stream<Map<dynamic, dynamic>> recognitionStream;
      ```

    * If you want to use the recognition stream for recording:
      ```dart
      //Example values for Google's Teachable Machine models
      recognitionStream = TfliteAudio.startAudioRecognition(
        sampleRate: 44100,
        bufferSize: 22016,
        )

      //Example values for decodedWav
      recognitionStream = TfliteAudio.startAudioRecognition(
        sampleRate: 16000,
        bufferSize: 2000,
        )
        
      //Example for advanced users who want to utilise all optional parameters from this package. 
      //Note the values are default.
      recognitionStream = TfliteAudio.startAudioRecognition(
        sampleRate: 44100,
        bufferSize: 22016,
        numOfInferences: 5,
        detectionThreshold: 0.3, 
        averageWindowDuration = 1000,
        minimumTimeBetweenSamples = 30,
        suppressionTime = 1500,
        )

      ```

    * If you want to use the recognition stream for stored audio files

       ```dart
       //Example values for both GTM or decodedwav
       recognitionStream = TfliteAudio.startFileRecognition(
         audioDirectory: "assets/sampleAudio.wav",
         );

       //Example for advanced users who want to utilise all optional parameters from this package. 
       recognitionStream = TfliteAudio.startFileRecognition(
         audioDirectory: "assets/sampleAudio.wav",
         detectionThreshold: 0.3,
         averageWindowDuration: 1000,
         minimumTimeBetweenSamples: 30,
         suppressionTime: 1500,
         );
       ```

    * Listen for results 
      ```dart
      String result = '';
      int inferenceTime = 0;

      recognitionStream.listen((event){
            result = event["inferenceTime"];
            inferenceTime = event["recognitionResult"];
            })
          .onDone(
             //Do something here when stream closes
           );
      ```

4. To forcibly cancel recognition stream
  
   ```dart
   TfliteAudio.stopAudioRecognition();
   ```

## Rough guide on the parameters

<br>
  
  * outputRawScores - Will output the result as an array in string format. For example `'[0.2, 0.6, 0.1, 0.1]'`

  * numThreads -  Higher threads will reduce inferenceTime. However, will utilise the more cpu resource.

  * isAsset - is your model, label or audio file in the asset file? If yes, set true. If the files are outside (such as external storage), set false.
  
  * numOfInferences - determines how many times you want to loop the recording and inference. For example:
`numOfInference = 3` will repeat the recording three times, so recording length will be (1 to 2 seconds) x 3 = (3 to 6 seconds). Also the model will output the scores three times.

  * sampleRate - A higher sample rate may improve accuracy. Recommened values are 16000, 22050, 44100

  * recordingLength - determines the size of your tensor input. If the value is not equal to your tensor input, it will crash.

  * bufferSize - Make sure this value is equal or below your recording length. Be aware that a higher value may not allow the recording enough time to capture your voice. A lower value will give more time, but it'll be more cpu intensive. Remember that the optimal value varies depending on the device.
    
  * detectionThreshold - Will ignore any predictions where its probability does not exceed the detection threshold. Useful for situations where you pickup unwanted/unintentional sounds. Lower the value if your model's performance isn't doing too well.

  * suppressionMs - If your detection triggers too early, the result may be poor or inaccurate. Adjust the values to avoid this situation.

  * averageWindowDurationMs - Use to remove earlier results that are too old.

  * minimumTimeBetweenSamples - Ignore any results that are coming in too frequently

<br>

## Android Installation & Permissions

<br>

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

<br/>

### **NOTE:** Skip below if your are not using Google Teachable Machine (Android)

<br/>


1. Enable select-ops under dependencies in your build gradle.

   ```Gradle
   dependencies {
       compile 'org.tensorflow:tensorflow-lite-select-tf-ops:+'
   }
   ```

<br>

## iOS Installation & Permissions
___

<br>

1. Add the following key to Info.plist for iOS. This ould be found in <YourApp>/ios/Runner
   ```
   <key>NSMicrophoneUsageDescription</key>
   <string>Record audio for playback</string>
   ```

2. Change the deployment target to at least 12.0. This could be done by:

   * Open your project workspace on xcode
  
   * Select root runner on the left panel
  
   * Under the info tab, change the iOS deployment target to 12.0
    
3. Open your podfile in your iOS folder and change platform ios to 12. 

   ```ruby
   platform :ios, '12.0'
   ```

<br/>

### **NOTE:** Skip below if your are not using Google Teachable Machine (iOS)
___

<br/>


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

   * Open your project on xcode 
    
   * click on runner under "Targets"
  
   * Click on "Build settings" tab

   * Click on "All" tab

   * Click on the empty space which is on the right side of "Other Links Flag"

   * Add: `-force_load $(SRCROOT)/Pods/TensorFlowLiteSelectTfOps/Frameworks/TensorFlowLiteSelectTfOps.framework/TensorFlowLiteSelectTfOps`

   ![](https://github.com/Caldarie/flutter_tflite_audio/blob/master/pictures/tflite-select-ops-installation.png)


6. Install the ops-select package to pod. To do this:

     * cd into iOS folder

     * Run `flutter pub get` on terminal

     * Run `pod install` on terminal

     * Run `flutter clean` on terminal
    
<br>

## References

<br>

1. https://github.com/tensorflow/examples/tree/master/lite/examples/speech_commands
2. https://www.tensorflow.org/lite/guide/ops_select
