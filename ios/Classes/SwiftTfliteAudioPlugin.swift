import Flutter
import UIKit
import TensorFlowLite

public class SwiftTfliteAudioPlugin: NSObject, FlutterPlugin {
    
    private var registrar: FlutterPluginRegistrar!
    private var result: FlutterResult!

    private var interpreter: Interpreter!
    
    let threadCountLimit = 10
    //let sampleRate = 16000


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
        
        var modelPath: String
        var modelKey: String
        //isAsset shared with label and model
        let isAsset = args["isAsset"] as! Bool
        
         //Get model path
        if(isAsset){
            modelKey = registrar.lookupKey(forAsset: args["model"] as! String)
            modelPath = Bundle.main.path(forResource: modelKey, ofType: nil)!
        } else {
            modelPath = args["model"] as! String
        }
        
        // Specify the options for the `Interpreter`.
        var threadCount = args["numThreads"] as! Int
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
        
        result("reddit")
        // //Load labels
        // var labelPath: String
        // var labelKey: String

        // if(labelPath.count > 0){
        //   if(isAsset){
        //     labelKey = registrar.lookupKey(forAsset: args["label"] as! String)
        //     labelPath = Bundle.main.path(forResource: labelKey, ofType: nil) as! String
        //   } else {
        //     labelPath = args["label"] as! String
        //   }
        
        // loadLabels(labelPath: labelPath)
        // }
    }
    

    
    // func startRecognition(){
    //     result("start recogniton")
    // }


    // func loadLabels(labelPath: String){
    //   result(labelPath)
    // }
}
