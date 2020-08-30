import 'dart:async';

import 'package:flutter/services.dart';

// ignore: avoid_classes_with_only_static_members
class TfliteAudio {
  static const MethodChannel _channel = MethodChannel('tflite_audio');

  static Future<bool> get checkPermissions async {
    return await _channel.invokeMethod('checkPermissions');
  }

  static Future<bool> requestPermissions() async {
    return _channel.invokeMethod('requestPermissions');
  }

  static Future startRecognition() async {
    return _channel.invokeMethod('startRecognition');
  }

  // ignore: type_annotate_public_apis
  static Future loadModel(
      // ignore: avoid_positional_boolean_parameters
      String model,
      String label,
      int numThreads,
      bool isAsset) async {
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
