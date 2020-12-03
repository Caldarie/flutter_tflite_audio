import 'package:flutter/material.dart';
import 'dart:async';
import 'dart:developer';
import 'package:tflite_audio/tflite_audio.dart';

void main() => runApp(MyApp());

//This example app showcases how the plugin can be used.
class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final GlobalKey<ScaffoldState> _scaffoldKey = new GlobalKey<ScaffoldState>();
  final isRecording = ValueNotifier<bool>(false);
  Stream<Map<dynamic, dynamic>> result;
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
    //Initilize the loadModel future
    TfliteAudio.loadModel(
        model: 'assets/conv_actions_frozen.tflite',
        label: 'assets/conv_actions_labels.txt',
        numThreads: 1,
        isAsset: true);
  }

  // get result by calling the future startAudioRecognition future
  void getResult() {
    result = TfliteAudio.startAudioRecognition(
      numOfInferences: 1,

      //parameters for google's teachable machine model
      inputType: 'rawAudio',
      sampleRate: 44100,
      recordingLength: 44032,
      bufferSize: 22016,

      // parameters for decodedwave models
      // inputType: 'decodedWav',
      // sampleRate: 16000,
      // recordingLength: 16000,
      // bufferSize: 8000,
    );

    //Logs the results and assigns false when stream is finished.
    result
        .listen((event) => log(event.toString()))
        .onDone(() => isRecording.value = false);
  }

  //handles null exception if snapshot is null.
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
            body: StreamBuilder<Map<dynamic, dynamic>>(
                stream: result,
                builder: (BuildContext context,
                    AsyncSnapshot<Map<dynamic, dynamic>> snapshot) {
                  switch (snapshot.connectionState) {
                    case ConnectionState.none:
                      return labelListWidget();
                      break;
                    case ConnectionState.waiting:
                      return Stack(children: <Widget>[
                        Align(
                            alignment: Alignment.bottomRight,
                            child: inferenceTimeWidget('calculating..')),
                        labelListWidget(),
                      ]);
                      break;
                    default:
                      return Stack(children: <Widget>[
                        Align(
                            alignment: Alignment.bottomRight,
                            child: inferenceTimeWidget(
                                showResult(snapshot, 'inferenceTime') + 'ms')),
                        labelListWidget(
                            showResult(snapshot, 'recognitionResult'))
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

//  If snapshot data matches the label, it will change color
  Widget labelListWidget([String result]) {
    return Center(
        child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: labelList.map((labels) {
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

//If the future isn't completed, shows 'calculating'. Else shows inference time.
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
