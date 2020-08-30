import Flutter
import UIKit
import TensorFlowLite

public class SwiftTfliteAudioPlugin: NSObject, FlutterPlugin {
    
    private var registrar: FlutterPluginRegistrar!
    private var result: FlutterResult!
    
    private var interpreter: Interpreter!
    
    let threadCountLimit = 10
    //let sampleRate = 16000
    
    private var labelArray: [String] = []
    /// TensorFlow Lite `Interpreter` object for performing inference on a given model.
    
    
    init(_ _registrar: FlutterPluginRegistrar) {
        registrar = _registrar  
    }
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "tflite_audio", binaryMessenger: registrar.messenger())
        let instance = SwiftTfliteAudioPlugin(registrar)
        registrar.addMethodCallDelegate(instance, channel: channel)
    }
    
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        let arguments = call.arguments as! [String: AnyObject]
        self.result = result
        
        switch call.method{
        case "loadModel":
            loadModel(registrar: registrar, args: arguments)
            break 
        case "startRecognition":
            print("button")
            break
        default: result(FlutterMethodNotImplemented)
        }
    }
    
    func loadModel(registrar: FlutterPluginRegistrar, args:[String:AnyObject]){
        
        let isAsset = args["isAsset"] as! Bool
        
        var modelPath: String
        var modelKey: String
        let model = args["model"] as! String
        
        
        //Get model path
        if(isAsset){
            modelKey = registrar.lookupKey(forAsset: model)
            modelPath = Bundle.main.path(forResource: modelKey, ofType: nil)!
        } else {
            modelPath = model
        }
        
        // Specify the options for the `Interpreter`.
        let threadCount = args["numThreads"] as! Int
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
        var labelPath: String
        var labelKey: String
         let label = args["label"] as! String
        
        if(label.count > 0){
            if(isAsset){
                labelKey = registrar.lookupKey(forAsset: label)
                labelPath = Bundle.main.path(forResource: labelKey, ofType: nil)!
            } else {
                labelPath = label
            }

         loadLabels(labelPath: labelPath)

        }
        
    }
    
    
    private func loadLabels(labelPath: String){
        do {
            //let contents = try String(contentsOf: label, encoding: .utf8)
            let contents = try String(describing: labelPath.cString(using: String.Encoding.utf8))
            labelArray = contents.components(separatedBy: .newlines)
            result(labelArray)
        } catch {
            fatalError("Labels cannot be read. Please add a valid labels file and try again.")
        }
        
        
    }
}
