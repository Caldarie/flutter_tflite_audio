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
    
    
    // public static var event: TfliteAudioEvent?
    
    //Streamhandler
    // private let recognitionStreamHandler = RecognitionStreamHandler();
    
    //placeholder variables
    private var eventSink: FlutterEventSink?
    private var registrar: FlutterPluginRegistrar!
    private var result: FlutterResult!
    private var arguments: [String: AnyObject]!
    
    /// TensorFlow Lite `Interpreter` object for performing inference on a given model.
    private var interpreter: Interpreter!
    
    //AvAudioEngine used for recording
    private var audioEngine: AVAudioEngine = AVAudioEngine()
    
    //Microphone variables
    private let audioBufferInputTensorIndex = 0
    private let sampleRateInputTensorIndex = 1
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
        // case "loadResultSmoothingVariables":
        //     foo()
        //     break
        default: result(FlutterMethodNotImplemented)
        }
    }

        
    public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        events(arguments)
        return nil
    }
    
    public func onCancel(withArguments arguments: Any?) -> FlutterError? {
        return nil
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
    
    private func loadLabels(labelPath: URL){
        let contents = try! String(contentsOf: labelPath, encoding: .utf8)
        labelArray = contents.components(separatedBy: CharacterSet.newlines)
        print(labelArray)
    }
    

}


