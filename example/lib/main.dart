import 'package:flutter/material.dart';
import 'dart:async';
import 'dart:developer';
import 'package:tflite_audio/tflite_audio.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final GlobalKey<ScaffoldState> _scaffoldKey = new GlobalKey<ScaffoldState>();
  Future<Map<dynamic, dynamic>> result;
  final isRecording = ValueNotifier<bool>(false);
  List<String> labelList = [
    '_silence_',
    '_unknown_',
    'yes',
    'no',
    'up',
    'down',
    'left',
    'right',
    'on',
    'off',
    'stop',
    'go'
  ];

  @override
  void initState() {
    super.initState();
    loadModel(
        model: 'assets/conv_actions_frozen.tflite',
        label: 'assets/conv_actions_labels.txt',
        numThreads: 1,
        isAsset: true);
  }

  Future loadModel(
      {String model, String label, int numThreads, bool isAsset}) async {
    return await TfliteAudio.loadModel(model, label, numThreads, isAsset);
  }

  Future<Map<dynamic, dynamic>> startAudioRecognition(
      {int sampleRate, int recordingLength, int bufferSize}) async {
    return await TfliteAudio.startAudioRecognition(
        sampleRate, recordingLength, bufferSize);
  }

  Future<Map<dynamic, dynamic>> getResult() async {
    Map<dynamic, dynamic> _result;
    await startAudioRecognition(
            sampleRate: 16000, recordingLength: 16000, bufferSize: 1280)
        // .then((map) => _result = map);
        .then((value) {
      _result = value;
      log(value.toString());
    });
    return _result;
  }

  void showInSnackBar(String value) {
    _scaffoldKey.currentState.showSnackBar(new SnackBar(
      content: new Text(value),
      duration: const Duration(milliseconds: 1600),
    ));
  }

  String mapToValue(dynamic value) {
    String reddit = value.toString() ?? null;
    return reddit;
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
        home: Scaffold(
            key: _scaffoldKey,
            appBar: AppBar(
              title: const Text('Tflite-audio/speech'),
            ),
            body: FutureBuilder<Map<dynamic, dynamic>>(
                future: result,
                builder: (BuildContext context,
                    AsyncSnapshot<Map<dynamic, dynamic>> snapshot) {
                  switch (snapshot.connectionState) {
                    case ConnectionState.none:
                      return Center(
                          child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.center,
                        children: labelList.map((labels) {
                          return Padding(
                              padding: const EdgeInsets.all(5.0),
                              child: Text(labels.toString(),
                                  textAlign: TextAlign.center,
                                  style: const TextStyle(
                                    fontWeight: FontWeight.bold,
                                    color: Colors.black,
                                  )));
                        }).toList(),
                      ));
                      break;
                    case ConnectionState.waiting:
                      return Stack(children: <Widget>[
                        const Align(
                            alignment: Alignment.bottomRight,
                            child: const Padding(
                                padding: const EdgeInsets.all(20.0),
                                child: const Text('calculating..',
                                    textAlign: TextAlign.center,
                                    style: const TextStyle(
                                      fontWeight: FontWeight.bold,
                                      fontSize: 20,
                                      color: Colors.black,
                                    )))),
                        Center(
                            child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          crossAxisAlignment: CrossAxisAlignment.center,
                          children: labelList.map((labels) {
                            return Padding(
                                padding: const EdgeInsets.all(5.0),
                                child: Text(labels.toString(),
                                    textAlign: TextAlign.center,
                                    style: const TextStyle(
                                      fontWeight: FontWeight.bold,
                                      color: Colors.black,
                                    )));
                          }).toList(),
                        ))
                      ]);
                      break;
                    default:
                      return Stack(children: <Widget>[
                        Align(
                            alignment: Alignment.bottomRight,
                            child: Padding(
                                padding: const EdgeInsets.all(20.0),
                                child: Text(
                                    snapshot.data['inferenceTime'].toString() +
                                        'ms',
                                    textAlign: TextAlign.center,
                                    style: const TextStyle(
                                      fontWeight: FontWeight.bold,
                                      fontSize: 20,
                                      color: Colors.black,
                                    )))),
                        Center(
                            child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          crossAxisAlignment: CrossAxisAlignment.center,
                          children: labelList.map((labels) {
                            if (labels == snapshot.data['recognitionResult']) {
                              return Padding(
                                  padding: const EdgeInsets.all(5.0),
                                  child: Text(labels.toString(),
                                      textAlign: TextAlign.center,
                                      style: const TextStyle(
                                        fontWeight: FontWeight.bold,
                                        fontSize: 30,
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
                          }).toList(),
                        ))
                      ]);
                  }
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
                            // value == true;
                            setState(() {
                              result = getResult().whenComplete(
                                  () => isRecording.value = false);
                            });
                          },
                          backgroundColor: Colors.blue,
                          child: const Icon(Icons.mic),
                        );
                      } else {
                        return FloatingActionButton(
                          onPressed: () {
                            log('button pressed too many times');
                          },
                          backgroundColor: Colors.red,
                          child: const Icon(Icons.adjust),
                        );
                      }
                    }))));
  }
}
