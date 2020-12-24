import Flutter
import CoreLocation 
import UIKit
import TensorFlowLite
import AVFoundation
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
    private var arguments: [String: AnyObject]!
    
    /// TensorFlow Lite `Interpreter` object for performing inference on a given model.
    private var interpreter: Interpreter!

    //AvAudioEngine used for recording
    private var audioEngine: AVAudioEngine = AVAudioEngine()
    
    //Microphone variables
    private let conversionQueue = DispatchQueue(label: "conversionQueue")
    private let maxInt16AsFloat32: Float32 = 32767.0
    
    //label smooth variables
    private var recognitionResult: LabelSmoothing?
    private var labelArray: [String] = []
    private let averageWindowDuration = 1000.0
    private let minTimeBetweenSamples = 30.0
    private let suppressionMs = 1500.0
    private let minimumCount = 3
    private let labelOffset = 2
    
    
    
    init(registrar: FlutterPluginRegistrar) {
        self.registrar = registrar
    }
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let instance = SwiftTfliteAudioPlugin(registrar: registrar)
        
        // MethodChannel
        let channel = FlutterMethodChannel(name: "tflite_audio", binaryMessenger: registrar.messenger())
        registrar.addMethodCallDelegate(instance, channel: channel)
        
        // EventChannel
        let eventChannel = FlutterEventChannel(name: "startAudioRecognition", binaryMessenger: registrar.messenger())
        eventChannel.setStreamHandler(instance)  
        
    }
    
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        
        self.arguments = call.arguments as? [String: AnyObject]
        self.result = result
        
        switch call.method{
        case "loadModel":
            loadModel(registrar: registrar)
            break 
        case "stopAudioRecognition":
            stopAudioRecognition()
        default: result(FlutterMethodNotImplemented)
        }
    }
    

    
    public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        self.events = events
        self.arguments = arguments as? [String: AnyObject]
        checkPermissions()
        return nil
    }
    
    public func onCancel(withArguments arguments: Any?) -> FlutterError? {
        self.events = nil
        return nil
    }

    func stopAudioRecognition(){
        print("Recording stopped.")
        // Closes stream
        if(events != nil){
        self.events(FlutterEndOfEventStream)
        }
        // Stop the recording
        self.audioEngine.stop()
        audioEngine.inputNode.removeTap(onBus: 0)
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
            if(events != nil){
                print(dict!)
                events(dict!)
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
    
    
    func startMicrophone(){
        print("start microphone")
        
        let bufferSize = arguments["bufferSize"] as! Int
        let sampleRate = arguments["sampleRate"] as! Int
        let recordingLength = arguments["recordingLength"] as! Int
        let numOfInferences = arguments["numOfInferences"] as! Int
        let inputType = arguments["inputType"] as! String
        
        let maxRecordingLength = recordingLength * numOfInferences
        let recordingFrameBuffer = bufferSize/2 
        
        //recording frames are appended and stored in this array
        var recordingBuffer: [Int16] = [] 
        var preRecordingCount: Int = 0
        var recordingCount: Int = recordingLength
        
        let inputNode = audioEngine.inputNode
        let inputFormat = inputNode.outputFormat(forBus: 0)
        let recordingFormat = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: Double(sampleRate), channels: 1, interleaved: true)
        guard let formatConverter =  AVAudioConverter(from:inputFormat, to: recordingFormat!) else {
            return
        }
        
        // install a tap on the audio engine and loops the frames into recordingBuffer
        audioEngine.inputNode.installTap(onBus: 0, bufferSize: AVAudioFrameCount(bufferSize), format: inputFormat) { (buffer, time) in
            
            self.conversionQueue.async {
                
                //let pcmBuffer = AVAudioPCMBuffer(pcmFormat: recordingFormat!, frameCapacity: AVAudioFrameCount(recordingFormat!.sampleRate * 2.0))
                let pcmBuffer = AVAudioPCMBuffer(pcmFormat: recordingFormat!, frameCapacity: AVAudioFrameCount(recordingFrameBuffer))
                var error: NSError? = nil
                
                let inputBlock: AVAudioConverterInputBlock = {inNumPackets, outStatus in
                    outStatus.pointee = AVAudioConverterInputStatus.haveData
                    return buffer
                }
                
                formatConverter.convert(to: pcmBuffer!, error: &error, withInputFrom: inputBlock)
                
                if error != nil {
                    print(error!.localizedDescription)
                }
                else if let channelData = pcmBuffer!.int16ChannelData {
                    
                    let channelDataValue = channelData.pointee
                    let channelDataValueArray = stride(from: 0,
                                                       to: Int(pcmBuffer!.frameLength),
                                                       by: buffer.stride).map{ channelDataValue[$0] }
                    
                    recordingBuffer.append(contentsOf: channelDataValueArray)
                    print("recordingBuffer length: \(recordingBuffer.count)")
                    
                    
                    if(recordingBuffer.count >= recordingCount){
                        print("reached threshold")
                        switch inputType{
                            case "decodedWav":
                                self.runDecodedWaveModel(onBuffer: Array(recordingBuffer[preRecordingCount..<recordingCount]))
                            case "rawAudio":
                                self.runRawAudioModel(onBuffer: Array(recordingBuffer[preRecordingCount..<recordingCount]))
                            default:
                                fatalError("inputType does not match the values decodedWav or rawAudio")

                        }
                        if(recordingBuffer.count >= maxRecordingLength){
                            self.stopAudioRecognition()
                        }else{
                            print("Looping...")
                            recordingCount += recordingLength
                            preRecordingCount += recordingLength
                            print(recordingCount)
                            print(preRecordingCount)
                        }
                    }
                    
                } //channeldata
            } //conversion queue
        } //installtap
        
        audioEngine.prepare()
        do {
            try audioEngine.start()
        }
        catch {
            print(error.localizedDescription)
        }
        
        
        
    }
    
    func runRawAudioModel(onBuffer buffer: [Int16]){
        print("Running model")

        if(events == nil){
            print("events is null. Breaking recognition")
            return
        }
       
        var interval: TimeInterval!
        var outputTensor: Tensor!
        
        do {
            // Copy the `[Int16]` buffer data as an array of `Float`s to the audio buffer input `Tensor`'s.
            let audioBufferData = Data(copyingBufferOf: buffer.map { Float($0) / maxInt16AsFloat32 })
            try interpreter.copy(audioBufferData, toInputAt: 0)
          
            
            // Calculate inference time
            let startDate = Date()
            try interpreter.invoke() //required!!! Do not touch
            interval = Date().timeIntervalSince(startDate) * 1000
            
            // Get the output `Tensor` to process the inference results.
            outputTensor = try interpreter.output(at: 0)

        } catch let error {
            print("Failed to invoke the interpreter with error: \(error.localizedDescription)")
        }
        
        // Gets the formatted and averaged results.
        let scores = [Float32](unsafeData: outputTensor.data) ?? []
        let results = getResults(withScores: scores)
        let roundInterval = interval.rounded();
        let finalResults = Result(recognitionResult: results, inferenceTime: roundInterval, hasPermission: true)
        // print(scores.count)
        // dump(scores)

        // Convert results to dictionary and then json
        let dict = finalResults.dictionary
        if(events != nil){
            print("results: \(dict!)")
            events(dict!)        
        }
    }   

    
    // func runModel(onBuffer buffer: [Int16]) -> Result? {
    func runDecodedWaveModel(onBuffer buffer: [Int16]){
        print("Running model")

        if(events == nil){
            print("events is null. Breaking recognition")
            return
        }

        let sampleRate = arguments["sampleRate"] as! Int
        var interval: TimeInterval!
        var outputTensor: Tensor!
        
        do {
            // Copy the `[Int16]` buffer data as an array of `Float`s to the audio buffer input `Tensor`'s.
            let audioBufferData = Data(copyingBufferOf: buffer.map { Float($0) / maxInt16AsFloat32 })
            try interpreter.copy(audioBufferData, toInputAt: 0)

            // Copy the sample rate data to the sample rate input `Tensor`.
            var rate = Int32(sampleRate)
            let sampleRateData = Data(bytes: &rate, count: MemoryLayout.size(ofValue: rate))
            try interpreter.copy(sampleRateData, toInputAt: 1)
            
            // Calculate inference time
            let startDate = Date()
            try interpreter.invoke() //required!!! Do not touch
            interval = Date().timeIntervalSince(startDate) * 1000
            
            // Get the output `Tensor` to process the inference results.
            outputTensor = try interpreter.output(at: 0)
            
            
            
        } catch let error {
            print("Failed to invoke the interpreter with error: \(error.localizedDescription)")
        }
        
        // Gets the formatted and averaged results.
        let scores = [Float32](unsafeData: outputTensor.data) ?? []
        let results = getResults(withScores: scores)
        let roundInterval = interval.rounded();
        let finalResults = Result(recognitionResult: results, inferenceTime: roundInterval, hasPermission: true)

        //Convert results to dictionary and then json
        let dict = finalResults.dictionary
        if(events != nil){
            print("results: \(dict!)")
            events(dict!)        
        }
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
    
    func loadModel(registrar: FlutterPluginRegistrar){
        
        let isAsset = arguments["isAsset"] as! Bool
        
        var modelPath: String
        var modelKey: String
        let model = arguments["model"] as! String
        
        
        //Get model path
        if(isAsset){
            modelKey = registrar.lookupKey(forAsset: model)
            modelPath = Bundle.main.path(forResource: modelKey, ofType: nil)!
        } else {
            modelPath = model
        }
        
        // Specify the options for the `Interpreter`.
        let threadCount = arguments["numThreads"] as! Int
        var options = Interpreter.Options()
        options.threadCount = threadCount
        
        do {
            // Create the `Interpreter`.
            interpreter = try Interpreter(modelPath: modelPath, options: options)
            // Allocate memory for the model's input `Tensor`s.
            try interpreter.allocateTensors()
        } catch let error {
            print("Failed to create the interpreter with error: \(error.localizedDescription)")
            //return nil
        }
        
        //Load labels
        let label = arguments["label"] as! String
        
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
        recognitionResult = LabelSmoothing(
            averageWindowDuration: averageWindowDuration,
            detectionThreshold: 0.3,
            minimumTimeBetweenSamples: minTimeBetweenSamples,
            suppressionTime: suppressionMs,
            minimumCount: minimumCount,
            classLabels: labelArray
        )
    }
    
    //reads text files and retrieves values to string array
    //also removes any emptyspaces of nil values in array
    private func loadLabels(labelPath: URL){
        let contents = try! String(contentsOf: labelPath, encoding: .utf8)
        labelArray = contents.components(separatedBy: CharacterSet.newlines).filter({ $0 != ""})
        print(labelArray)
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
