import 'dart:async';

import 'package:flutter/services.dart';

// Class which manages the futures for the plugins
class TfliteAudio {
  static const MethodChannel _channel = MethodChannel('tflite_audio');
  static const EventChannel _eventChannel =
      EventChannel('startAudioRecognition');

// Future to obtain results. Returns a map with the following values:
//1. recognitionResult - string value
//2. inferenceTime - int value
//3. hasPermission - boolean which checks for permission
  static Stream<Map<dynamic, dynamic>> startAudioRecognition(
      {int sampleRate,
      int recordingLength,
      int bufferSize,
      int numOfRecordings}) {
    final recognitionStream =
        _eventChannel.receiveBroadcastStream(<String, dynamic>{
      'sampleRate': sampleRate,
      'recordingLength': recordingLength,
      'bufferSize': bufferSize,
      'numOfRecordings': numOfRecordings,
    });
    return recognitionStream
        .cast<Map<dynamic, dynamic>>()
        .map((event) => Map<dynamic, dynamic>.from(event));
  }

  //   return _channel.invokeMethod('startAudioRecognition', {
  //   'sampleRate': sampleRate,
  //   'recordingLength': recordingLength,
  //   'bufferSize': bufferSize,
  //   'numOfRecordings': numOfRecordings,
  // });

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
