import 'dart:async';

import 'package:flutter/services.dart';

// ignore: avoid_classes_with_only_static_members
class TfliteAudio {
  static const MethodChannel _channel = MethodChannel('tflite_audio');

  static Future<dynamic> startAudioRecognition(
      int sampleRate, int recordingLength, int bufferSize) async {
    return _channel.invokeMethod('startAudioRecognition', {
      'sampleRate': sampleRate,
      'recordingLength': recordingLength,
      'bufferSize': bufferSize
    });
  }

  static Future<dynamic> processRecognitionResults(
      int averageWindowDurationMs,
      int minimumTimeBetweenSamples,
      int supressionMs,
      int minimumCount,
      double detectionThreshold) async {
    return _channel.invokeMethod('startAudioRecognition', {
      'averageWindowDurationMs': averageWindowDurationMs,
      'minimumTimeBetweenSamples': minimumTimeBetweenSamples,
      'supressionMs': supressionMs,
      'minimumCount': minimumCount,
      'detectionThreshold': detectionThreshold
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
