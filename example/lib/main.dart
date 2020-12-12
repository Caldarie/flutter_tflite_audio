import 'package:flutter/material.dart';
import 'dart:async';
import 'dart:developer';
import 'package:tflite_audio/tflite_audio.dart';

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
  Stream<Map<dynamic, dynamic>> result;

  //! label list for decodedwav models
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

  //! label list for google's teachable machine model
  // List<String> labelList = ['0 Background Noise', '1 no', '2 yes'];

  @override
  void initState() {
    super.initState();

    TfliteAudio.loadModel(
      numThreads: 1,
      isAsset: true,

      //! asset location for decodedwav models
      model: 'assets/decoded_wav_model.tflite',
      label: 'assets/decoded_wav_label.txt',

      //! asset location for google's teachable machine model
      // model: 'assets/google_teach_machine_model.tflite',
      // label: 'assets/google_teach_machine_label.txt'
    );
  }

  /// get result by calling the future startAudioRecognition future
  /// be sure to comment one of the other to switch model types.
  void getResult() {
    result = TfliteAudio.startAudioRecognition(
      numOfInferences: 1,

      //! example value for decodedwav models
      inputType: 'decodedWav',
      sampleRate: 16000,
      recordingLength: 16000,
      bufferSize: 4000,

      //! recommended value for google's teachable machine model
      // inputType: 'rawAudio',
      // sampleRate: 44100,
      // recordingLength: 44032,
      // bufferSize: 11013,
    );

    ///Logs the results and assigns false when stream is finished.
    result
        .listen((event) => log(event.toString()))
        .onDone(() => isRecording.value = false);
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

  ///  If snapshot data matches the label, it will change colour
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
