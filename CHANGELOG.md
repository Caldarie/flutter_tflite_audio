## 0.1.3
* Hotfix for crash when permission has been denied.
* Added the key 'hasPermission' for the future startAudioRecognitions().
* Added feature in example where it'll show inference times

## 0.1.2
* Instead of returning a single string value, the future startAudioRecognition() now returns a map with the following keys:
    - recognitionResult 
    - inferenceTime
* Fixed issue in example where pressing the record button multiple times will crash the app.
* Added feature in example where pressing the recording button changes color.

## 0.1.1
* Made some fixes with making options explicit
* Added alert dialog when permission is denied.

## 0.1.0
* Added iOS support

## 0.0.4
* Added the following arguments into the future: startAudioRecognition()
    - sampleRate
    - recordingLength
    - bufferSize

## 0.0.3
* Merged permission and audio recognition futures into one future.

## 0.0.2
* Fixed image url

## 0.0.1

* Initial release.
