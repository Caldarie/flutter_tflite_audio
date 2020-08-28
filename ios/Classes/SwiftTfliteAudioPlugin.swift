import Flutter
import UIKit

public class SwiftTfliteAudioPlugin: NSObject, FlutterPlugin {
    
    let registrar: FlutterPluginRegistrar;
    var result: FlutterResult!
         
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
        self.result = result;

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
      //dump(args)
      var key: String;
      let isAsset = args["isAsset"] as! Bool;
      let model = args["model"] as! String;
      if(isAsset){
      key = registrar.lookupKey(forAsset: model);
      }
      result("success")
    }

    func startRecognition(){
      result("success");
    }
}
