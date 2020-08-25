import 'dart:async';

import 'package:flutter/services.dart';

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

  static Future loadModel(model, label, numThreads, isAsset) async {
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
