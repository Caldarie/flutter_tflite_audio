import 'package:flutter/material.dart';
import 'dart:async';
import 'dart:developer';
import 'package:tflite_audio/tflite_audio.dart';
import 'package:flutter/services.dart';
import 'dart:convert';

void main() => runApp(MyApp());

///This example showcases how to take advantage of all the futures and streams
///from the plugin.
class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final GlobalKey<ScaffoldState> _scaffoldKey = new GlobalKey<ScaffoldState>();
  final isRecording = ValueNotifier<bool>(false);
  Stream<Map<dynamic, dynamic>>? result;

  // //!example values for decodedwav models
  // final String model = 'assets/decoded_wav_model.tflite';
  // final String label = 'assets/decoded_wav_label.txt';
  // final String audioDirectory = 'assets/sample_audio_16k_mono.wav';
  // final String inputType = 'decodedWav';
  // final int sampleRate = 16000;
  // final int bufferSize = 2000;

  //!example values for google's teachable machine model
  // final String model = 'assets/google_teach_machine_model.tflite';
  // final String label = 'assets/google_teach_machine_label.txt';
  // final String inputType = 'rawAudio';
  // final String audioDirectory = 'assets/sample_audio_44k_mono.wav';
  // final int sampleRate = 44100;
  // final int bufferSize = 22016;
  // final int bufferSize = 11008;

  static const String model =
      'assets/ser_angry_bored.tflite,assets/ser_calm_disgusted.tflite,assets/ser_fearful_neutral.tflite,assets/ser_happy_sad.tflite';
  static const String label =
      'assets/angry_bored_labels.txt,assets/calm_disgusted_labels.txt,assets/fearful_neutral_labels.txt,assets/happy_sad_labels.txt';
  final String inputType = 'rawAudio';
  final int sampleRate = 16000;
  final int bufferSize = 2000;
  final int recordingLength = 16000;

  //!Optional parameters you can adjust to modify your interence.
  final bool outputRawScores = false;
  final int numOfInferences = 1;
  final int numThreads = 1;
  final bool isAsset = true;

  //!Adjust the values below when tuning model detection.
  // final double detectionThreshold = 0.3;
  // final int averageWindowDuration = 1000;
  // final int minimumTimeBetweenSamples = 30;
  // final int suppressionTime = 1500;

  @override
  void initState() {
    super.initState();
    TfliteAudio.loadModel(
      // numThreads: this.numThreads,
      // isAsset: this.isAsset,
      inputType: inputType,
      outputRawScores: outputRawScores,
      model: model,
      label: label,
    );
  }

  /// get result by calling the stream startAudioRecognition
  /// Uncomment the parameters below if you wish to adjust the values
  void getResult() {
    //example for stored audio file recognition
    // result = TfliteAudio.startFileRecognition(
    //   audioDirectory: this.audioDirectory,
    //   // detectionThreshold: this.detectionThreshold,
    //   // averageWindowDuration: this.averageWindowDuration,
    //   // minimumTimeBetweenSamples: this.minimumTimeBetweenSamples,
    //   // suppressionTime: this.suppressionTime,
    // );

    ///example for recording recognition
    result = TfliteAudio.startAudioRecognition(
      sampleRate: sampleRate,
      bufferSize: bufferSize,
      numOfInferences: numOfInferences,
      recordingLength: recordingLength,
      // detectionThreshold: this.detectionThreshold,
      // averageWindowDuration: this.averageWindowDuration,
      // minimumTimeBetweenSamples: this.minimumTimeBetweenSamples,
      // suppressionTime: this.suppressionTime,
    );

    //Event returns a map. Keys are:
    //event["recognitionResult"], event["hasPermission"], event["inferenceTime"]
    result
        ?.listen((event) =>
            log("Recognition Result: " + event["recognitionResult"].toString()))
        .onDone(() => isRecording.value = false);
  }

  //fetches the labels from the text file in assets
  Future<List<String>> fetchLabelList() async {
    List<String> _labelList = [];
    final labelFiles = label.split(",");
    for (var i = 0; i < labelFiles.length; i++) {
      await rootBundle.loadString(labelFiles[i]).then((q) {
        for (String s in LineSplitter().convert(q)) {
          if (!_labelList.contains(s)) {
            _labelList.add(s);
          }
        }
      });
    }
    return _labelList;
  }

  ///handles null exception if snapshot is null.
  String showResult(AsyncSnapshot snapshot, String key) =>
      snapshot.hasData ? snapshot.data[key].toString() : 'null ';

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
        home: Scaffold(
            key: _scaffoldKey,
            appBar: AppBar(
              title: const Text('Tflite-audio/speech'),
            ),
            //Streambuilder for inference results
            body: StreamBuilder<Map<dynamic, dynamic>>(
                stream: result,
                builder: (BuildContext context,
                    AsyncSnapshot<Map<dynamic, dynamic>> inferenceSnapshot) {
                  //futurebuilder for getting the label list
                  return FutureBuilder(
                      future: fetchLabelList(),
                      builder: (BuildContext context,
                          AsyncSnapshot<List<String>> labelSnapshot) {
                        switch (inferenceSnapshot.connectionState) {
                          case ConnectionState.none:
                            //Loads the asset file.
                            if (labelSnapshot.hasData) {
                              return labelListWidget(labelSnapshot.data);
                            } else {
                              return CircularProgressIndicator();
                            }
                            break;
                          case ConnectionState.waiting:
                            //Widets will let the user know that its loading when waiting for results
                            return Stack(children: <Widget>[
                              Align(
                                  alignment: Alignment.bottomRight,
                                  child: inferenceTimeWidget('calculating..')),
                              labelListWidget(labelSnapshot.data),
                            ]);
                            break;
                          //Widgets will display the final results.
                          default:
                            return Stack(children: <Widget>[
                              Align(
                                  alignment: Alignment.bottomRight,
                                  child: inferenceTimeWidget(showResult(
                                          inferenceSnapshot, 'inferenceTime') +
                                      'ms')),
                              labelListWidget(
                                  labelSnapshot.data,
                                  showResult(
                                      inferenceSnapshot, 'recognitionResult'))
                            ]);
                        }
                      });
                }),
            floatingActionButtonLocation:
                FloatingActionButtonLocation.centerFloat,
            floatingActionButton: Container(
                child: ValueListenableBuilder(
                    valueListenable: isRecording,
                    builder: (context, value, widget) {
                      if (value == false) {
                        return FloatingActionButton(
                          onPressed: () {
                            isRecording.value = true;
                            setState(() {
                              getResult();
                            });
                          },
                          backgroundColor: Colors.blue,
                          child: const Icon(Icons.mic),
                        );
                      } else {
                        return FloatingActionButton(
                          onPressed: () {
                            log('Audio Recognition Stopped');
                            //Press button again to cancel audio recognition
                            TfliteAudio.stopAudioRecognition();
                          },
                          backgroundColor: Colors.red,
                          child: const Icon(Icons.adjust),
                        );
                      }
                    }))));
  }

  ///  If snapshot data matches the label, it will change colour
  Widget labelListWidget(List<String>? labelList, [String? result]) {
    return Center(
        child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: labelList!.map((labels) {
              if (labels == result) {
                return Padding(
                    padding: const EdgeInsets.all(5.0),
                    child: Text(labels.toString(),
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 25,
                          color: Colors.green,
                        )));
              } else {
                return Padding(
                    padding: const EdgeInsets.all(5.0),
                    child: Text(labels.toString(),
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          color: Colors.black,
                        )));
              }
            }).toList()));
  }

  ///If the future isn't completed, shows 'calculating'. Else shows inference time.
  Widget inferenceTimeWidget(String result) {
    return Padding(
        padding: const EdgeInsets.all(20.0),
        child: Text(result,
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontWeight: FontWeight.bold,
              fontSize: 20,
              color: Colors.black,
            )));
  }
}
