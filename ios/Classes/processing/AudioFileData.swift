import Foundation

class AudioFileData{

    private var inputSize: Int
    private var int16DataSize: Int

    private var requirePadding: Bool? = nil
    private var numOfInferences: Int? = nil
    
    private var indexCount = 0
    private var inferenceCount = 1
    
    private var audioChunk: [Int16] = []
    private var result = [Int16]()

    init(inputSize: Int, bufferSize: Int){
        self.inputSize = inputSize
        self.int16DataSize = bufferSize

        let excessSamples = int16DataSize % inputSize;
        let missingSamples = inputSize - excessSamples;
        requirePadding = getPaddingRequirement(excessSamples: excessSamples, missingSamples: missingSamples);

        let totalWithPad = int16DataSize + missingSamples;
        let totalWithoutPad = int16DataSize - excessSamples;
        numOfInferences = getNumOfInferences(totalWithoutPad: totalWithoutPad, totalWithPad: totalWithPad);
    }


    func getPaddingRequirement(excessSamples: Int, missingSamples: Int) -> Bool{
        let hasMissingSamples = missingSamples != 0 || excessSamples != inputSize
        let pctThreshold: Double = 0.75
        let sampleThreshold  = Int(round(Double(inputSize) * pctThreshold))
        let underThreshold = missingSamples < sampleThreshold

        print(hasMissingSamples)
        print(sampleThreshold)
        print(underThreshold)

        if (hasMissingSamples && underThreshold) {return true}
        else if (hasMissingSamples && !underThreshold) {return false}
        else if (!hasMissingSamples && underThreshold) {return false}
        else {return false}

    }

    func getNumOfInferences(totalWithoutPad: Int, totalWithPad: Int) -> Int{
        return requirePadding! ? Int(totalWithPad/inputSize) : Int(totalWithoutPad/inputSize)
    }

    func getState(i: Int) -> String{
        let reachInputSize: Bool = (i + 1) % inputSize == 0 
        let reachFileSize: Bool = i == int16DataSize - 1
        let reachInferenceLimit: Bool = inferenceCount == numOfInferences

        if (reachInputSize && !reachInferenceLimit) {
            return "recognising";
        } // inferences > 1 && < not final
        else if (!reachInputSize && reachInferenceLimit && !reachFileSize) {
            return "appending";
        } // Inferences = 1
        else if (!reachInputSize && !reachInferenceLimit) {
            return "appending";
        } // Inferences > 1
        else if (!reachInputSize && reachInferenceLimit && reachFileSize) {
            return "finalising";
        } // for padding last infernce
        else if (reachInputSize && reachInferenceLimit) {
            return "finalising";
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
        print("inference count: \(inferenceCount)/\(numOfInferences!)")
        return self
    }

    @discardableResult
     func emit(result: @escaping ([Int16]) -> Void) -> AudioFileData{
         print(audioChunk.count)
        result(Array(audioChunk[0..<inputSize]))
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
        let missingSamples = inputSize - indexCount
        if(requirePadding!){
             let paddedArray: [Int16] = (-10...10).randomElements(missingSamples)
             audioChunk.append(contentsOf: paddedArray)
             print( "\(missingSamples) samples have been padded to audio chunk")
        }else{
             print( "\(missingSamples) samples are missing. Padding not required")
        }
        return self
    }
    

}
