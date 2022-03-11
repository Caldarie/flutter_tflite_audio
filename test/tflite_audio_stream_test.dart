/*
event streams
https://github.com/befovy/fijkplayer/blob/58503f3f2591d41437bef61478de57b7527dff98/test/fijkplayer_test.dart
https://github.com/BugsBunnyBR/plugin_gen.flutter/blob/master/examples/counter_stream_plugin/test/counter_stream_plugin_test.dart

https://github.com/ZaraclaJ/audio_recorder/blob/master/test/audio_recorder_test.dart
*/

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tflite_audio/tflite_audio.dart';

const MethodChannel eventChannel = MethodChannel('startAudioRecognition');

void main() {
  WidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    eventChannel.setMockMethodCallHandler((methodCall) async {
      await ServicesBinding.instance!.defaultBinaryMessenger
          .handlePlatformMessage(
              'startAudioRecognition',
              const StandardMethodCodec()
                  .encodeSuccessEnvelope(<dynamic, dynamic>{
                'hasPermission': 'true',
                'inferenceTime': '189',
                'recognitionResult': 'no'
              }),
              (data) {});
    });
  });

  tearDown(() {
    eventChannel.setMockMethodCallHandler(null);
  });

  test('returns a map stream correctly', () async {
    var value = TfliteAudio.startAudioRecognition(
        sampleRate: 16000, audioLength: 16000, bufferSize: 2000);

    value.listen(expectAsync1((event) {
      expect(event, <dynamic, dynamic>{
        'hasPermission': 'true',
        'inferenceTime': '189',
        'recognitionResult': 'no'
      });
    }));
  });
}
