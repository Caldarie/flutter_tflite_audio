import 'dart:async';

import 'package:flutter/services.dart';

// Class which manages the futures for the plugins
class TfliteAudio {
  static const MethodChannel _channel = MethodChannel('tflite_audio');
  static const EventChannel _eventChannel =
      EventChannel('startAudioRecognition');

// Streams  event. Can return multiple map objects with the following values:
//1. recognitionResult - string value
//2. inferenceTime - int value
//3. hasPermission - boolean which checks for permission
//4. numOfRecording - number of inferences you want to make per recording.
  static Stream<Map<dynamic, dynamic>> startAudioRecognition(
      {String inputType,
      int sampleRate,
      int recordingLength,
      int bufferSize,
      int numOfInferences}) {
    final recognitionStream =
        _eventChannel.receiveBroadcastStream(<String, dynamic>{
      'inputType': inputType,
      'sampleRate': sampleRate,
      'recordingLength': recordingLength,
      'bufferSize': bufferSize,
      'numOfInferences': numOfInferences,
    });
    return recognitionStream
        .cast<Map<dynamic, dynamic>>()
        .map((event) => Map<dynamic, dynamic>.from(event));
  }

  //Future for loading your model and label
//a larger num of threads will reduce inferenceTime.
  static Future stopAudioRecognition() async {
    return _channel.invokeMethod('stopAudioRecognition');
  }

//Future for loading your model and label
//a larger num of threads will reduce inferenceTime.
  static Future loadModel(
      {String model, String label, int numThreads, bool isAsset}) async {
    return _channel.invokeMethod(
      'loadModel',
      {
        'model': model,
        'label': label,
        'numThreads': numThreads,
        'isAsset': isAsset,
      },
    );
  }
}
