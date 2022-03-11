import Flutter
import CoreLocation 
import UIKit
import TensorFlowLite
import AVFoundation
import RosaKit //recognise
import CoreML //recognise
import RxCocoa
import RxSwift
import os


//  Interpreter result => dictionarty
struct Result: Codable {
    // let recognitionResult: RecognitionResult?
    let recognitionResult: String!
    let inferenceTime: Double
    let hasPermission: Bool
}

public class SwiftTfliteAudioPlugin: NSObject, FlutterPlugin, FlutterStreamHandler {
    
    //placeholder variables
    private var events: FlutterEventSink!
    private var registrar: FlutterPluginRegistrar!
    private var result: FlutterResult!
    private var interpreter: Interpreter!
    
    //recording variables
    private var bufferSize: Int!
    private var sampleRate: Int!
    private var recording: Recording?
    
    //Model variables
    private var model: String!
    private var label: String!
    private var isAsset: Bool!
    private var numThreads: Int!
    private var numOfInferences: Int!
    
    //input/output variables
    private var inputShape: [Int]!
    // private var outputShape: [Int]! //TODO - remove
    private var inputSize: Int!
    private var audioLength: Int!
//    private var outputSize: Int!
    private var inputType: String!
    private var outputRawScores: Bool!
    private var transposeInput: Bool!
    private var transposeOutput: Bool!
    
    //preprocessing variable
    private var audioFile: AudioFile?
    private var audioDirectory: String!
    private var isPreprocessing: Bool = false
    
    //labelsmoothing variables
    private var recognitionResult: LabelSmoothing?
    private var labelArray: [String] = []
    private var detectionThreshold: NSNumber!
    private var averageWindowDuration: Double!
    private var minimumTimeBetweenSamples: Double!
    private var suppressionTime: Double!

    //spectrogram
    private var nMFCC: Int!
    private var nFFT: Int!
    private var nMels: Int!
    private var hopLength: Int!
    
    //threads
    // private let conversionQueue = DispatchQueue(label: "conversionQueue")
    private let preprocessQueue = DispatchQueue(label: "preprocessQueue")
    private let group = DispatchGroup() //notifies whether recognition thread is done
    
    
    init(registrar: FlutterPluginRegistrar) {
        self.registrar = registrar
    }
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let instance = SwiftTfliteAudioPlugin(registrar: registrar)
        
        let channel = FlutterMethodChannel(name: "tflite_audio", binaryMessenger: registrar.messenger())
        registrar.addMethodCallDelegate(instance, channel: channel)
        
        let audioRecognitionChannel = FlutterEventChannel(name: "AudioRecognitionStream", binaryMessenger: registrar.messenger())
        audioRecognitionChannel.setStreamHandler(instance)
        
        let fileRecognitionChannel = FlutterEventChannel(name: "FileRecognitionStream", binaryMessenger: registrar.messenger())
        fileRecognitionChannel.setStreamHandler(instance)
        
    }
    
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        self.result = result
        var arguments: [String: AnyObject]
        
        switch call.method{
        case "loadModel":
            arguments = call.arguments as! [String: AnyObject] //DO NOT CHANGE POSITION
            print("Model argumentss: \(arguments)")

            self.numThreads = arguments["numThreads"] as? Int
            self.inputType = arguments["inputType"] as? String
            self.outputRawScores = arguments["outputRawScores"] as? Bool
            self.model = arguments["model"] as? String
            self.label = arguments["label"] as? String
            self.isAsset = arguments["isAsset"] as? Bool
            loadModel(registrar: registrar)
            break
        case "setSpectrogramParameters":
            arguments = call.arguments as! [String: AnyObject]
            print("Spectrogram arguments: \(arguments)")

            self.nMFCC = arguments["nMFCC"] as? Int
            self.nFFT = arguments["nFFT"] as? Int
            self.nMels = arguments["nMels"] as? Int
            self.hopLength = arguments["hopLength"] as? Int
            break;
        case "stopAudioRecognition":
            forceStopRecognition()
            break
        default: result(FlutterMethodNotImplemented)
        }
    }
    
    
    public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        
        let arguments: [String: AnyObject] = arguments as! [String: AnyObject]
        print("Stream arguments: \(arguments)")

        self.events = events
        self.averageWindowDuration = arguments["averageWindowDuration"] as? Double
        self.detectionThreshold = arguments["detectionThreshold"] as? NSNumber
        self.minimumTimeBetweenSamples = arguments["minimumTimeBetweenSamples"] as? Double
        self.suppressionTime = arguments["suppressionTime"] as? Double

        let method = arguments["method"] as? String
        
        switch method {
        case "setAudioRecognitionStream":
            self.bufferSize = arguments["bufferSize"] as? Int
            self.sampleRate = arguments["sampleRate"] as? Int
            self.numOfInferences = arguments["numOfInferences"] as? Int
            self.audioLength = determineInput(arguments: arguments)
            if(audioLength == 0){ assertionFailure("audioLength is 0")}
            checkPermissions()
            break
        case "setFileRecognitionStream":
            //TODO - dont need to have external permission?
            self.audioDirectory = arguments["audioDirectory"] as? String
            self.sampleRate = arguments["sampleRate"] as? Int
            self.audioLength = determineInput(arguments: arguments)
            if(audioLength == 0){ assertionFailure("Error: Please specify audioLength.")}
            preprocessAudioFile()
            break
        default:
            print("Unknown method type with listener")
        }
        
        return nil
    }
    
    public func onCancel(withArguments arguments: Any?) -> FlutterError? {
        self.events = nil
        return nil
    }

    
    func determineInput(arguments: [String: AnyObject]) -> Int{
        
        let audioLength = arguments["audioLength"] as? Int
        
        let hasValue: Bool = audioLength! > 0
        let isAudioInput: Bool = inputType == "rawAudio" || inputType == "decodedWav"
        
        
        if(hasValue){
            print("AudioLength: \(audioLength!)")
            return audioLength!
        }

        else if(!hasValue && isAudioInput){
            let newAudioLength = inputShape.reduce(1, *)
            print("AudioLength: \(newAudioLength)")
            return newAudioLength
        }

        else if(!hasValue && !isAudioInput){
            print("Warning: Unspecified audio length may cause unintended problems with spectro models")
            print("AudioLength: \(sampleRate!)")
            return sampleRate
        }
        
        else{
            print("Error: Cannot determine audioLength.")
            return 0
        }
        
    }

    // func shouldTranspose(inputShape: [Int]) -> Bool{
        
    //     //check if shape contains element "1" at least once
    //     let count = inputShape.reduce(0) { $1 == 1 ? $0 + 1 : $0}
    //     if(count != 1) {assertionFailure("Problem with input shape: \(inputShape) ") }
    
    //     return inputShape[0] > inputShape[1] ? true : false
    // }
    
    func loadModel(registrar: FlutterPluginRegistrar){
     
        
        var modelPath: String
        var modelKey: String
        
        //Get model path
        if(isAsset){
            modelKey = registrar.lookupKey(forAsset: model)
            modelPath = Bundle.main.path(forResource: modelKey, ofType: nil)!
        } else {
            modelPath = model
        }
        
        // Specify the options for the `Interpreter`.
        var options = Interpreter.Options()
        options.threadCount = numThreads
        
        do {
            // Create the `Interpreter` and allocate memory for the model's input `Tensor`s.
            self.interpreter = try Interpreter(modelPath: modelPath, options: options)
            try interpreter.allocateTensors()
            
            self.inputShape = try interpreter.input(at: 0).shape.dimensions
        
        } catch let error {
            assertionFailure("Failed to create the interpreter with error: \(error.localizedDescription)")
        }
        
        print("Model succesfully loaded.")
        print("InputShape: \(inputShape!)")
        
        //Load labels
        if(label.count > 0){
            if(isAsset){
                let labelKey = registrar.lookupKey(forAsset: label)
                let labelPath = Bundle.main.url(forResource: labelKey, withExtension: nil)!
                loadLabels(labelPath: labelPath as URL)
            } else {
                let labelPath = URL(string: label)
                loadLabels(labelPath: labelPath!)
            }
        }
        
    }
    
    //reads text files and retrieves values to string array
    //also removes any emptyspaces of nil values in array
    private func loadLabels(labelPath: URL){
        let contents = try! String(contentsOf: labelPath, encoding: .utf8)
        labelArray = contents.components(separatedBy: CharacterSet.newlines).filter({ $0 != ""})
        print("labels: \(labelArray)")
    }
    
    func checkPermissions() {
        
        switch AVAudioSession.sharedInstance().recordPermission {
        case .granted:
            print("Permission granted")
            startMicrophone()
        case .denied:
            showAlert(title: "Microphone Permissions", message: "Permission denied. Please accept permission in your settings.")
            let finalResults = Result(recognitionResult: nil, inferenceTime: 0, hasPermission: false)
            let dict = finalResults.dictionary
            if events != nil {
                print(dict!)
                events(dict!)
                self.events(FlutterEndOfEventStream)
            }
        case .undetermined:
            print("requesting permission")
            requestPermissions()
        @unknown default:
            print("Something weird just happened")
        }
    }
    
    
    func requestPermissions() {
        AVAudioSession.sharedInstance().requestRecordPermission { (granted) in
            if granted {
                
                self.startMicrophone()
            }
            else {
                print("check permissions")
                self.checkPermissions()
            }
        }
    }
    
    func showAlert(title: String, message: String) {
        
        DispatchQueue.main.async {
            let alertController = UIAlertController(title: title, message:
                                                        message, preferredStyle: .alert)
            let rootViewController = UIApplication.shared.keyWindow?.rootViewController
            let settingsAction = UIAlertAction(title: "Settings", style: .default) { (_) -> Void in
                guard let settingsUrl = URL(string: UIApplication.openSettingsURLString) else {
                    return
                }
                if UIApplication.shared.canOpenURL(settingsUrl) {
                    UIApplication.shared.open(settingsUrl, completionHandler: { (success) in })
                }
            }
            let cancelAction = UIAlertAction(title: "Cancel", style: .default, handler: nil)
            alertController.addAction(cancelAction)
            alertController.addAction(settingsAction)
            rootViewController?.present(alertController, animated: true, completion: nil)
        }
    }

    //https://stackoverflow.com/questions/34751294/how-can-i-generate-an-array-of-floats-from-an-audio-file-in-swift
     func loadData() -> URL{
        
         var fileURL: URL
    
         if(isAsset){
            let audioKey = registrar.lookupKey(forAsset: audioDirectory)
            let audioPath = Bundle.main.path(forResource: audioKey, ofType: nil)!
            fileURL = URL(string: audioPath)!

        } else {
            let audioPath = audioDirectory!
            fileURL = URL(string: audioPath)!
        }
         
        return fileURL
        
    }
    
    //preprocess queue replaces observeOn or subscribeOn so that it can be forcefully stopped
    func preprocessAudioFile(){
        print("Preprocessing audio file..")
  
        let url = loadData()
        audioFile = AudioFile(fileURL: url, audioLength: audioLength)
         
        self.preprocessQueue.async { [self] in
            _ = audioFile!.getObservable()
                .subscribe(
                    onNext: { audioChunk in self.recognize(onBuffer: audioChunk)},
                    onError: { error in print(error)},
                    onCompleted: { self.stopStream() },
                    onDisposed: {print("Recording stream disposed")})

            audioFile?.splice()
        }
    }
    
    //no noticable difference with subscribeOn and observeOn. (Maybe for different phones?)
    func startMicrophone(){
        print("start microphone")

        recording = Recording(
            bufferSize: bufferSize, 
            audioLength: audioLength,
            sampleRate: sampleRate, 
            numOfInferences: numOfInferences)

        let main = MainScheduler.instance
        let concurrentBackground = ConcurrentDispatchQueueScheduler.init(qos: .background)
        
        //underscore to suppress disposable warning
        //https://github.com/ReactiveX/RxSwift/blob/main/Documentation/Warnings.md
        self.preprocessQueue.async { [self] in
            _ = recording!.getObservable()
                .subscribe(on: concurrentBackground)
                .observe(on: main)   
                .subscribe(
                    onNext: { audioChunk in self.recognize(onBuffer: audioChunk)},
                    onError: { error in print(error)},
                    onCompleted: { self.stopStream() },
                    onDisposed: {print("Recording stream disposed")})
                
            recording!.start()
        }
        
    }
    
//https://stackoverflow.com/questions/12469361/java-algorithm-for-normalizing-audio
    // func normalise(value: Int16, max: Int16) -> Float{
    //     let targetMax: Double = 32767.0
    //     let rawMax: Double = Double(max)
        
    //     let maxAdjust: Double = 1.0 - targetMax / rawMax
    //     let absVal: Double = Double(abs(value))
    //     let factor: Double = maxAdjust * absVal / rawMax

    //     return Float(round(1 - factor) * Double(value))
    // }

    
    
    /* Normalisation formula
     https://stats.stackexchange.com/questions/178626/how-to-normalize-data-between-1-and-1
     https://stackoverflow.com/questions/32169111/distribute-array-values-so-maximum-will-be-1-and-minimum-will-be-0
     */
    func recognize(onBuffer buffer16: [Int16]){
        print("Running model")
        
        if(events == nil){
            print("events is null. Breaking recognition")
            return
        }
        
        var interval: TimeInterval!
        var outputTensor: Tensor!
        let maxRes16: Int16 = 32767
        let minAmp = buffer16.min()!
        let maxAmp = buffer16.max()!
        let bytesInFloat = 4
        let inputSize = inputShape.reduce(1, *) * bytesInFloat //requird for spectro as inputsize varies 
        
        do {
            
            switch(inputType){
            
            case "mfcc":

//                let buffer64 = buffer16.map { Double($0) / Double(maxRes16) }

                let buffer64 = buffer16.map { 2.0 *  ((Double($0) - Double(minAmp)) / (Double(maxAmp) - Double(minAmp))) - 1.0 }
                print(buffer16[0..<40])
                print(buffer64[0..<40])

                let mfcc = buffer64.mfcc(nMFCC: nMFCC, nFFT: nFFT, hopLength: hopLength, sampleRate: sampleRate, melsCount: nMels)
                
//                for i in 1..<mfcc.count{
//                    for n in 1..<mfcc[0].count{
//                        print(mfcc[i][n])
//                    }
//                }
                
                let flatMFCC = mfcc.reduce([], +)
                let minMFCC: Double = flatMFCC.min()!
                let maxMFCC: Double = flatMFCC.max()!
                
//                let buffer32 = flatMel.map { Float($0) }
                let buffer32: [Float] = flatMFCC.map {  Float(($0 - minMFCC) / (maxMFCC - minMFCC)) }
                let inputData = Data(copyingBufferOf: buffer32) //2d to 1d
                
                displayShape(spectro: mfcc)
                displayWarning(dataCount: inputData.count, inputSize: inputSize)
                
                try interpreter.copy(inputData[0..<inputSize], toInputAt: 0)
                break

            case "melSpectrogram":
                let buffer64 =  buffer16.map { Double($0) /  Double(maxRes16)}
                let melSpectrogram = buffer64.melspectrogram(nFFT: nFFT, hopLength: hopLength, sampleRate: sampleRate, melsCount: nMels)
                let flatMel = melSpectrogram.reduce([], +)
                let buffer32 = flatMel.map { Float($0) }
                let inputData = Data(copyingBufferOf: buffer32)
                
                displayShape(spectro: melSpectrogram)
                displayWarning(dataCount: inputData.count, inputSize: inputSize)
                
                try interpreter.copy(inputData[0..<inputSize], toInputAt: 0)
                break
            
            case "spectrogram":
                let buffer64 =  buffer16.map { Double($0) /  Double(maxRes16)}
                let stft = buffer64.stft(nFFT: self.nFFT, hopLength: self.hopLength)
                let spectrogram = stft.map { $0.map { Float(pow($0.real, 2.0) + pow($0.imagine, 2.0)) } } //iterate 2d
                let inputData = Data(copyingBufferOf: spectrogram.flatMap { $0 }) //2d to 1d
                
                displayShape(spectro: spectrogram)
                displayWarning(dataCount: inputData.count, inputSize: inputSize)
                
                try interpreter.copy(inputData[0..<inputSize], toInputAt: 0)
                break
                
            case "rawAudio":
                let buffer32 = buffer16.map { Float($0) / Float(maxRes16) }
                // let buffer32 = buffer16.map { 2.0 *  ((Float($0) - Float(minAmp)) / (Float(maxAmp) - Float(minAmp))) - 1.0 }
                // let rawMax: Int16 = buffer16.max()!
                // let buffer32 = buffer16.map { normalise(value: $0, max: rawMax) }
//                let reddit = buffer32.map { $0 / Float(maxRes16) }
                // print(rawMax)
                // print (buffer16[0..<40])
                // print( buffer32[0..<40])
                let inputData = Data(copyingBufferOf:  buffer32)
                try interpreter.copy(inputData, toInputAt: 0)
                break
                
            case "decodedWav":
                let buffer32 = buffer16.map { Float($0) / Float(maxRes16) }
//                let buffer32 = buffer16.map { 2.0 *  ((Float($0) - Float(minAmp)) / (Float(maxAmp) - Float(minAmp))) - 1.0 }
                let inputData = Data(copyingBufferOf: buffer32)
                try interpreter.copy(inputData, toInputAt: 0)
                
                var rate = Int32(sampleRate)
                let sampleRateData = Data(bytes: &rate, count: MemoryLayout.size(ofValue: rate))
                try interpreter.copy(sampleRateData, toInputAt: 1)
                break
                
            default:
                print("incorrect input type: \(inputType!)")
                
            }
            
            let startDate = Date()
            try interpreter.invoke()
            interval = Date().timeIntervalSince(startDate) * 1000
            outputTensor = try interpreter.output(at: 0)
        

        } catch let error {
                print("Failed to invoke the interpreter with error: \(error.localizedDescription)")
        }
        
        // Gets the formatted and averaged results.
        let scores = [Float32](unsafeData: outputTensor.data) ?? []
        let normalizedScores = scores.map { $0.isFinite ? $0 : 0.0 }
        let finalResults: Result!
        let roundInterval = interval.rounded()

        //Set parameters for label smoothing
        recognitionResult = LabelSmoothing(
            averageWindowDuration: averageWindowDuration!,
            detectionThreshold: detectionThreshold!.floatValue as Float,
            minimumTimeBetweenSamples: minimumTimeBetweenSamples!,
            suppressionTime: suppressionTime!,
            classLabels: labelArray
        )

        
        
        //debugging
        print("Raw Label Scores:")
        dump(scores)
        
        
        if(outputRawScores == false){

            let results = getResults(withScores: normalizedScores)
            finalResults = Result(recognitionResult: results, inferenceTime: roundInterval, hasPermission: true)
        }else{
            //convert array to exact string value
            let data = try? JSONSerialization.data(withJSONObject: normalizedScores)
            let stringValue = String(data: data!, encoding: String.Encoding.utf8)
            finalResults = Result(recognitionResult: stringValue, inferenceTime: roundInterval, hasPermission: true)
        }
        
        // Convert results to dictionary and json.
        let dict = finalResults.dictionary
        if(events != nil){
            print("results: \(dict!)")
            events(dict!)
        }
        
        // self.stopRecognition();
        
         
    }
    
    private func displayShape(spectro: [[Any]]){
        print("mfcc shape: [\(spectro.count), \(spectro[0].count)]")
    }
    
    private func displayWarning(dataCount: Int, inputSize: Int){
        if(dataCount > inputSize){
            print("Warning: inputData(\(dataCount)) exceeds inputSize(\(inputSize)).")}
            print("Warning: Excess may influence inference results")
    }
    
    
    
    // private func getResults(withScores scores: [Float]) -> RecognitionResult? {
    private func getResults(withScores scores: [Float]) -> String? {
        
        // Runs results through recognize commands.
        let command = recognitionResult?.process(
            latestResults: scores,
            currentTime: Date().timeIntervalSince1970 * 1000
        )
        
        //Check if command is new and the identified result is not silence or unknown.
        // guard let newCommand = command,
        //   let index = labelArray.firstIndex(of: newCommand.name),
        //   newCommand.isNew,
        //   index >= labelOffset
        // else {
        //     return nil
        // }
        return command?.name
    }
    
    func forceStopRecognition(){    
        stopPreprocessing()
        stopRecording()
    }

    func stopStream(){
        if(events != nil){
            print("recognition stream stopped")
            self.events(FlutterEndOfEventStream)
        }
    }


    func stopRecording(){
        if let _recording = recording {
            print("Stop recording.")
            _recording.stop()
        } else {
            print("No recording found. Breaking")
        }
    }

    func stopPreprocessing(){
        if let _audioFile = audioFile {
            print("Stop preprocessing")
            _audioFile.stop()
        } else {
            print("No preprocessing found. Breaking")
        }
    }
}


//----------------EXTENSIONS-----------

//Used in runModel()
extension Data {
    /// Creates a new buffer by copying the buffer pointer of the given array.
    ///
    /// - Warning: The given array's element type `T` must be trivial in that it can be copied bit
    ///     for bit with no indirection or reference-counting operations; otherwise, reinterpreting
    ///     data from the resulting buffer has undefined behavior.
    /// - Parameter array: An array with elements of type `T`.
    init<T>(copyingBufferOf array: [T]) {
        self = array.withUnsafeBufferPointer(Data.init)
    }
}

//Used for startMicrophone()
extension Array {
    /// Creates a new array from the bytes of the given unsafe data.
    ///
    /// - Warning: The array's `Element` type must be trivial in that it can be copied bit for bit
    ///     with no indirection or reference-counting operations; otherwise, copying the raw bytes in
    ///     the `unsafeData`'s buffer to a new array returns an unsafe copy.
    /// - Note: Returns `nil` if `unsafeData.count` is not a multiple of
    ///     `MemoryLayout<Element>.stride`.
    /// - Parameter unsafeData: The data containing the bytes to turn into an array.
    init?(unsafeData: Data) {
        guard unsafeData.count % MemoryLayout<Element>.stride == 0 else { return nil }
#if swift(>=5.0)
        self = unsafeData.withUnsafeBytes { .init($0.bindMemory(to: Element.self)) }
#else
        self = unsafeData.withUnsafeBytes {
            .init(UnsafeBufferPointer<Element>(
                start: $0,
                count: unsafeData.count / MemoryLayout<Element>.stride
            ))
        }
#endif  // swift(>=5.0)
    }
}

// Used to encode the struct class Result to json
extension Encodable {
    var dictionary: [String: Any]? {
        guard let data = try? JSONEncoder().encode(self) else { return nil }
        return (try? JSONSerialization.jsonObject(with: data, options: .allowFragments)).flatMap { $0 as? [String: Any] }
    }
}

extension Decodable {
    init(from: Any) throws {
        let data = try JSONSerialization.data(withJSONObject: from, options: .prettyPrinted)
        let decoder = JSONDecoder()
        self = try decoder.decode(Self.self, from: data)
    }
}

//https://stackoverflow.com/questions/28140145/create-an-array-of-random-numbers-in-swift
extension RangeExpression where Bound: FixedWidthInteger {
    func randomElements(_ n: Int) -> [Bound] {
        precondition(n > 0)
        switch self {
        case let range as Range<Bound>: return (0..<n).map { _ in .random(in: range) }
        case let range as ClosedRange<Bound>: return (0..<n).map { _ in .random(in: range) }
        default: return []
        }
    }
}
