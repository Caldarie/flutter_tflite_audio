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
    // private var arguments: [String: AnyObject]! 

    //recording variables
    private var bufferSize: Int!
    private var sampleRate: Int!
    private var numOfInferences: Int!

    //Model variables
    private var inputSize: Int!
    private var inputType: String!
    private var inputShape: [Int]!
    private var outputRawScores: Bool!
    private var model: String!
    private var label: String!
    private var isAsset: Bool!
    private var numThreads: Int!
    
    //preprocessing variable
    private var audioDirectory: String!
    
    //labelsmoothing variables 
    private var detectionThreshold: NSNumber!
    private var averageWindowDuration: Double!
    private var minimumTimeBetweenSamples: Double!
    private var suppressionTime: Double!

    
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
        
        switch call.method{
        case "loadModel":
            let arguments: [String: AnyObject] = call.arguments as! [String: AnyObject] //DO NOT CHANGE POSITION
            self.numThreads = arguments["numThreads"] as? Int
            self.inputType = arguments["inputType"] as? String
            self.outputRawScores = arguments["outputRawScores"] as? Bool
            self.model = arguments["model"] as? String
            self.label = arguments["label"] as? String
            self.isAsset = arguments["isAsset"] as? Bool
            loadModel(registrar: registrar)
            break 
        case "stopAudioRecognition":
            stopAudioRecognition()
            break
        default: result(FlutterMethodNotImplemented)
        }
    }
    

    
    public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
     
        let arguments: [String: AnyObject] = arguments as! [String: AnyObject]
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
            checkPermissions()
            break
        case "setFileRecognitionStream":
            //TODO - dont need to have external permission?
            self.audioDirectory = arguments["audioDirectory"] as? String
            preprocessAudioFile()
            break
            // checkPermissions(permissionType: "requestExternalPermission") 
        default:
            print("Unknown method type with listener")
        }

        return nil
    }
    
    public func onCancel(withArguments arguments: Any?) -> FlutterError? {
        self.events = nil
        return nil
    }

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
            self.inputSize = inputShape.max()
            print("Input shape: \(inputShape!)")
            print("Input size \(inputSize!)")
            print("Input type \(inputType!)")

        } catch let error {
            print("Failed to create the interpreter with error: \(error.localizedDescription)")
            //return nil
        }
        
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
        print(labelArray)
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
    
    func preprocessAudioFile(){
        print("Preprocessing audio file..")

        var data: Data
        // var recordingBuffer: [Int16] = []
        
        if(isAsset){
            let audioKey = registrar.lookupKey(forAsset: audioDirectory)
            let audioPath = Bundle.main.path(forResource: audioKey, ofType: nil)!
            // data = try! Data(contentsOfFile: audioPath)
            data = NSData(contentsOfFile: audioPath)! as Data
            
        } else {
            let audioPath = audioDirectory;
            let url = URL(string: audioPath!)
            data = try! Data(contentsOf: url!)
        }

      
        let format = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 44100, channels: 1, interleaved: true)
        // let pcmBuffer = AVAudioPCMBuffer(pcmFormat: format!, frameCapacity: AVAudioFrameCount(data.count/2))

        let d = data.convertedTo(format!) 
        let i16array = data.withUnsafeBytes {
            UnsafeBufferPointer<Int16>(start: $0, count: data.count/2).map(Int16.init(littleEndian:))
        }
        recognize(onBuffer: Array(i16array[0..<inputSize]))

    }

    
    func startMicrophone(){
        print("start microphone")
        
        let recordingFrameBuffer = bufferSize/2
        var recordingBuffer: [Int16] = []
        var inferenceCount: Int = 1
        let numOfInferences = self.numOfInferences
        let inputSize = self.inputSize
        
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
                
                    

                    //Append frames onto the recording buffer until it reaches the input size
                    //Do not change inferenceCount <= numOfInferences! - counts last inference
                    if(inferenceCount <= numOfInferences! && recordingBuffer.count < inputSize!){
                        recordingBuffer.append(contentsOf: channelDataValueArray)
                        print("recordingBuffer length: \(recordingBuffer.count) | inferenceCount: \(inferenceCount)/\(numOfInferences!)")
                    
                    //Starts recognition when recording bufffer is full. Resest recording buffer for next inference
                    }else if(inferenceCount < numOfInferences! && recordingBuffer.count == inputSize!){
                            
                        print("reached threshold")
                        self.recognize(onBuffer: Array(recordingBuffer[0..<inputSize!]))
                        
                        inferenceCount += 1
                        recordingBuffer = []
                        print("Looping recording")
                        print("Clearing recording buffer")

                    //when buffer exeeds max record length, trim and resize the buffer, append, and then start inference
                    //Resets recording buffer after inference
                    }else if(inferenceCount < numOfInferences! && recordingBuffer.count > inputSize!){
                        
                        print("Exceeded threshold")
                        self.recognize(onBuffer: Array(recordingBuffer[0..<inputSize!]))
                        
                        inferenceCount += 1
                        let excessRecordingBuffer: [Int16] = Array(recordingBuffer[inputSize!..<recordingBuffer.count])
                        recordingBuffer = []
                        recordingBuffer.append(contentsOf: excessRecordingBuffer)
                        print("Looping recording")
                        print("trimmed excess recording. Excess count: \(excessRecordingBuffer.count)")
                        print("Clearing recording buffer")
                        print("appended excess to recording buffer")
                        
                    //Final inference. Stops recognitions and recording.
                    //No need to trim excess data as this is the final inference. (not applicable on fixed arrays from android/java)
                    }else if(inferenceCount == numOfInferences! && recordingBuffer.count >= inputSize!){
                        self.recognize(onBuffer: Array(recordingBuffer[0..<inputSize!]))
                        self.stopAudioRecognition()

                        inferenceCount = 1
                        recordingBuffer = []                    
                    }else{
                        print("Something weird happened")
                        print("recording buffer: \(recordingBuffer.count)")
                        print("inferenceCount: \(inferenceCount)")
                        print("numOfInferences: \(numOfInferences!)")
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
    
    func recognize(onBuffer buffer: [Int16]){
        print("Running model")

        if(events == nil){
            print("events is null. Breaking recognition")
            return
        }
       
        var interval: TimeInterval!
        var outputTensor: Tensor!
        // print(outputRawScores!)
        // let outputRawScores = true
        
        do {
            // Copy the `[Int16]` buffer data as an array of `Float`s to the audio buffer input `Tensor`'s.
            let audioBufferData = Data(copyingBufferOf: buffer.map { Float($0) / maxInt16AsFloat32 })
            try interpreter.copy(audioBufferData, toInputAt: 0)

            if(inputType != "decodedWav" && inputType != "rawAudio"){
                assertionFailure("Input type does not match decodedWav or rawAudio")
            }

            if(inputType == "decodedWav"){
                     // Copy the sample rate data to the sample rate input `Tensor`.
                    var rate = Int32(sampleRate)
                    let sampleRateData = Data(bytes: &rate, count: MemoryLayout.size(ofValue: rate))
                    try interpreter.copy(sampleRateData, toInputAt: 1)
            }

            // Calculate inference time
            let startDate = Date()
            try interpreter.invoke() //required!!! Do not touch
            interval = Date().timeIntervalSince(startDate) * 1000
            
            // Get the output `Tensor` to process the inference results.
            outputTensor = try interpreter.output(at: 0)

        } catch let error {
            print("Failed to invoke the interpreter with error: \(error.localizedDescription)")
        }
        
        print(detectionThreshold!)
        
        recognitionResult = LabelSmoothing(
            averageWindowDuration: averageWindowDuration!,
            detectionThreshold: detectionThreshold!.floatValue as Float,
            minimumTimeBetweenSamples: minimumTimeBetweenSamples!,
            suppressionTime: suppressionTime!,
            classLabels: labelArray
        )

        // Gets the formatted and averaged results.
        let scores = [Float32](unsafeData: outputTensor.data) ?? []
        let finalResults: Result!
        let roundInterval = interval.rounded()

        //debugging
        print("Raw Label Scores:")
        dump(scores)


        if(outputRawScores == false){
            let results = getResults(withScores: scores)
            finalResults = Result(recognitionResult: results, inferenceTime: roundInterval, hasPermission: true)
        }else{
            //convert array to exact string value
            let data = try? JSONSerialization.data(withJSONObject: scores)
            let stringValue = String(data: data!, encoding: String.Encoding.utf8)
            finalResults = Result(recognitionResult: stringValue, inferenceTime: roundInterval, hasPermission: true)
        }
        
        // Convert results to dictionary and json.
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
