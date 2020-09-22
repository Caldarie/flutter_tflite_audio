import 'dart:async';

import 'package:flutter/services.dart';

// ignore: avoid_classes_with_only_static_members
class TfliteAudio {
  static const MethodChannel _channel = MethodChannel('tflite_audio');

  static Future<Map<dynamic, dynamic>> startAudioRecognition(
      int sampleRate, int recordingLength, int bufferSize) async {
    return _channel.invokeMethod('startAudioRecognition', {
      'sampleRate': sampleRate,
      'recordingLength': recordingLength,
      'bufferSize': bufferSize
    });
  }

  static Future loadModel(
      String model, String label, int numThreads, bool isAsset) async {
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
