import 'dart:async';

import 'package:flutter/services.dart';

// ignore: avoid_classes_with_only_static_members
/// Class which manages the future and stream for the plugins
class TfliteAudio {
  static const MethodChannel _channel = MethodChannel('tflite_audio');
  static const EventChannel _eventChannel =
      EventChannel('startAudioRecognition');

  /// [startAudioRecognition] returns map objects with the following values:
  /// 1. String recognitionResult
  /// 2. int inferenceTime
  /// 3. bool hasPermission
  static Stream<Map<dynamic, dynamic>> startAudioRecognition(
      {required int sampleRate,
      required int recordingLength,
      required int bufferSize,
      double detectionThreshold = 0.3,
      int numOfInferences = 1,
      int averageWindowDuration = 0,
      int minimumTimeBetweenSamples = 0,
      int suppressionTime = 0}) {
    final recognitionStream =
        _eventChannel.receiveBroadcastStream(<String, dynamic>{
      'sampleRate': sampleRate,
      'recordingLength': recordingLength,
      'bufferSize': bufferSize,
      'numOfInferences': numOfInferences,
      'averageWindowDuration': averageWindowDuration,
      'detectionThreshold': detectionThreshold,
      'minimumTimeBetweenSamples': minimumTimeBetweenSamples,
      'suppressionTime': suppressionTime
    });

    ///cast the result of the stream a map object.
    return recognitionStream
        .cast<Map<dynamic, dynamic>>()
        .map((event) => Map<dynamic, dynamic>.from(event));
  }

  ///call [stopAudioRecognition] to forcibly stop recording, recognition and
  ///stream.
  static Future stopAudioRecognition() async {
    return _channel.invokeMethod('stopAudioRecognition');
  }

  ///initialize [loadModel] before calling any other streams and futures.
  static Future loadModel(
      {required String model,
      required String label,
      required String inputType,
      bool outputRawScores = false,
      int numThreads = 1,
      bool isAsset = true}) async {
    return _channel.invokeMethod(
      'loadModel',
      {
        'model': model,
        'label': label,
        'inputType': inputType,
        'outputRawScores': outputRawScores,
        'numThreads': numThreads,
        'isAsset': isAsset,
      },
    );
  }
}
