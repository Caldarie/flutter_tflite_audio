/*
check if argument passes
https://github.com/flutter/plugins/blob/f93314bb3779ebb0151bc326a0e515ca5f46533c/packages/image_picker/image_picker_platform_interface/test/new_method_channel_image_picker_test.dart
*/

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tflite_audio/tflite_audio.dart';

final List<MethodCall> log = <MethodCall>[];
const MethodChannel channel = MethodChannel('tflite_audio');
// const MethodChannel eventChannel = MethodChannel('startAudioRecognition');

void main() {
  WidgetsFlutterBinding.ensureInitialized();

  group('loadModel() test', () {
    setUp(() async {
      channel.setMockMethodCallHandler((methodCall) async {
        log.add(methodCall);
        return '';
      });
      log.clear();
    });

    tearDown(() {
      channel.setMockMethodCallHandler(null);
    });

    test('passes optional and required arguments correctly', () async {
      await TfliteAudio.loadModel(
        inputType: 'decodedWav',
        model: 'assets/decoded_wav_model.tflite',
        label: 'assets/decoded_wav_label.txt',
      );

      await TfliteAudio.loadModel(
        inputType: 'decodedWav',
        model: 'assets/google_teach_machine_model.tflite',
        label: 'assets/google_teach_machine_label.txt',
        numThreads: 3,
        isAsset: false,
      );

      expect(
        log,
        <Matcher>[
          isMethodCall(
            'loadModel',
            arguments: <dynamic, dynamic>{
              'model': 'assets/decoded_wav_model.tflite',
              'label': 'assets/decoded_wav_label.txt',
              'numThreads': 1,
              'isAsset': true,
            },
          ),
          isMethodCall(
            'loadModel',
            arguments: <dynamic, dynamic>{
              'model': 'assets/google_teach_machine_model.tflite',
              'label': 'assets/google_teach_machine_label.txt',
              'numThreads': 3,
              'isAsset': false,
            },
          ),
        ],
      );
    });
  });

  // group('startAudioRecognition() test', () {
  //   setUp(() async {
  //     eventChannel.setMockMethodCallHandler((methodCall) async {
  //       log.add(methodCall);
  //       return '';
  //     });
  //     log.clear();
  //   });

  //   tearDown(() {
  //     eventChannel.setMockMethodCallHandler(null);
  //   });

  //   test('passes optional and required arguments correctly', () async {
  //     TfliteAudio.startAudioRecognition(
  //         inputType: 'decodedWav',
  //         sampleRate: 16000,
  //         audioLength: 16000,
  //         bufferSize: 2000);

  //     expect(
  //       log,
  //       <Matcher>[
  //         isMethodCall(
  //           'startAudioRecognition',
  //           arguments: <dynamic, dynamic>{
  //             'inputType': 'decodedWav',
  //             'sampleRate': 16000,
  //             'audioLength': 16000,
  //             'bufferSize': 2000,
  //           },
  //         ),
  //       ],
  //     );
  //   });
  // });
}
