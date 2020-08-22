import 'package:flutter/material.dart';
import 'dart:async';
import 'dart:developer';

import 'package:audio_processing/audio_processing.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  Future<String> result;
  List<String> labelList = [
    'silence',
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
        model: "assets/conv_actions_frozen.tflite",
        label: "assets/conv_actions_labels.txt",
        numThreads: 1,
        isAsset: true);
  }

  Future<String> startAudioRecognition() async {
    String _result;
    await checkPermissions().then((permissionStatus) async {
      //starts recognition if theres permissions
      if (permissionStatus == true) {
        await startRecognition().then((result) => _result = result);
      } else {
        //requests for permission if there are no permissions.
        await requestPermissions().then((permissionStatus) async {
          if (permissionStatus == true) {
            await startRecognition().then((result) => _result = result);
          } else {
            log("Please accept permission");
          }
        }).catchError((e) => "$e");
      }
    });
    return _result;
  }

  Future loadModel({model, label, numThreads, isAsset}) async {
    return await AudioProcessing.loadModel(model, label, numThreads, isAsset);
  }

  Future<bool> checkPermissions() async {
    return await AudioProcessing.checkPermissions;
  }

  Future<bool> requestPermissions() async {
    return await AudioProcessing.requestPermissions();
  }

  Future<String> startRecognition() async {
    return await AudioProcessing.startRecognition();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
        home: Scaffold(
            appBar: AppBar(
              title: const Text('Tflite-audio/speech'),
            ),
            body: Center(
                child: FutureBuilder<String>(
              future: result,
              builder: (BuildContext context, AsyncSnapshot<String> snapshot) {
                return Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: labelList.map((labels) {
                    if (labels == snapshot.data) {
                      return Padding(
                          padding: const EdgeInsets.all(5.0),
                          child: Text(
                            labels.toString(),
                            textAlign: TextAlign.center,
                            style: TextStyle(
                                fontSize: 25,
                                fontWeight: FontWeight.bold,
                                color: Colors.green),
                          ));
                    } else {
                      return Padding(
                          padding: const EdgeInsets.all(5.0),
                          child: Text(labels.toString(),
                              textAlign: TextAlign.center,
                              style: TextStyle(
                                fontWeight: FontWeight.bold,
                                color: Colors.black,
                              )));
                    }
                  }).toList(),
                );
              },
            )),
            floatingActionButton: FloatingActionButton(onPressed: () {
              setState(() {
                result = startAudioRecognition();
              });
            })));
  }
}
