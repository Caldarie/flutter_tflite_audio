import 'dart:async';

import 'package:flutter/services.dart';

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
