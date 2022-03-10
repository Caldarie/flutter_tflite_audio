import Foundation

class AudioFileData{

    private var audioLength: Int
    private var int16DataSize: Int

    private var requirePadding: Bool? = nil
    private var numOfInferences: Int? = nil
    
    private var indexCount = 0
    private var inferenceCount = 1
    
    private var audioChunk: [Int16] = []
    private var result = [Int16]()

    init(audioLength: Int, bufferSize: Int){
        self.audioLength = audioLength
        self.int16DataSize = bufferSize

        let excessSamples = int16DataSize % audioLength;
        let missingSamples = audioLength - excessSamples;
        requirePadding = getPaddingRequirement(excessSamples: excessSamples, missingSamples: missingSamples);
    
        let totalWithPad = int16DataSize + missingSamples;
        let totalWithoutPad = int16DataSize - excessSamples;
        numOfInferences = getNumOfInferences(totalWithoutPad: totalWithoutPad, totalWithPad: totalWithPad);

        // //Force pad data that is lacking
        // let isLackingData = numOfInferences == 0 && requirePadding == false
        // if(isLackingData){
        //     print("Insufficient Data. Audio Chunk set to be padded regardless of threshold")
        //     self.requirePadding == true
        //     self.numOfInferences == 1
        // }
    }


    func getPaddingRequirement(excessSamples: Int, missingSamples: Int) -> Bool{
        let hasMissingSamples = missingSamples != 0 || excessSamples != audioLength
        let missingSampleThreshold: Double = 0.40
        let missingSamplesRatio: Double = Double(missingSamples) / Double(audioLength)
        let shouldPad = missingSamplesRatio < missingSampleThreshold 
        // print(missingSampleThreshold) //testing
        // print(missingSamplesRatio) //testing
        // print(shouldPad) //testing
    
        if (hasMissingSamples && shouldPad) {return true}
        else if (hasMissingSamples && !shouldPad) {return false}
        else if (!hasMissingSamples && shouldPad) {return false}
        else {return false}

    }

    func getNumOfInferences(totalWithoutPad: Int, totalWithPad: Int) -> Int{
        return requirePadding! ? Int(totalWithPad/audioLength) : Int(totalWithoutPad/audioLength)
    }

    func getState(i: Int) -> String{
        let reachInputSize: Bool = (i + 1) % audioLength == 0 
        let reachFileSize: Bool = (i + 1) == int16DataSize
        let reachInferenceLimit: Bool = inferenceCount == numOfInferences

        if (reachInputSize && !reachInferenceLimit) {
            return "recognise";
        } // inferences > 1 && < not final
        else if (!reachInputSize && reachInferenceLimit && !reachFileSize) {
            return "append";
        } // Inferences = 1
        else if (!reachInputSize && !reachInferenceLimit) {
            return "append";
        } // Inferences > 1
        else if (!reachInputSize && reachInferenceLimit && reachFileSize) {
            return "padAndFinalise";
        } // for padding last infernce
        else if (reachInputSize && reachInferenceLimit) {
            return "finalise";
        } // inference is final
        else {
            return "Error";
        }

    }

    //TODO - add after rest
    @discardableResult
    func append(data: Int16) -> AudioFileData{
        audioChunk.append(data)
        indexCount += 1
        return self
    }

    @discardableResult
    func displayCount() -> AudioFileData{
        // print("\(inferenceCount)/\(numOfInferences!) | \(audioChunk.count)/\(audioLength)") //TESTING
        print("inference count: \(inferenceCount)/\(numOfInferences!)")
        return self
    }

    @discardableResult
     func emit(result: @escaping ([Int16]) -> Void) -> AudioFileData{
        result(Array(audioChunk[0..<audioLength]))
        return self
        
    }

    @discardableResult
    func reset() -> AudioFileData{
        indexCount = 0;
        inferenceCount += 1
        audioChunk.removeAll()
        return self
    }

    @discardableResult
    func padSilence(i: Int) -> AudioFileData{
        let missingSamples = audioLength - indexCount
        if(requirePadding!){
             let paddedArray: [Int16] = (-10...10).randomElements(missingSamples)
             audioChunk.append(contentsOf: paddedArray)
             print( "(\(missingSamples)) samples have been padded to audio chunk")
        }else{
             print( "Under threshold. Padding not required")
        }
        return self
    }
    

}
