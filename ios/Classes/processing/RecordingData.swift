import Foundation

/* emit function 
allow chaining to continue after callback clouse (notice two "->")
@escaping callback closure allows parameter to return in arguments
https://abhimuralidharan.medium.com/chaining-methods-in-swift-not-optional-chaining-3007d1714985
http://www.albertopasca.it/whiletrue/swift-chaining-methods/
*/

/* @discardableResult
supress call warning
*/

/* optionals "?" on class variables
avoid need to call init() since we are using setters
*/

class RecordingData{

    private var bufferSize: Int?
    private var inputSize: Int?
    private var sampleRate: Int?
    private var numOfInferences: Int?

    private var inferenceCount = 1
    private var recordingBuffer: [Int16] = []
    private var result = [Int16]()

    func setBufferSize(bufferSize: Int){
        self.bufferSize = bufferSize
    }
    
    func setInputSize(inputSize: Int){
        self.inputSize = inputSize
    }

    func setSampleRate(sampleRate: Int){
        self.sampleRate = sampleRate
    }

    func setNumOfInferences(numOfInferences: Int){
        self.numOfInferences = numOfInferences
    }

    func getState() -> String{
        var state: String = ""

        if(inferenceCount <= numOfInferences! && recordingBuffer.count < inputSize!){
            state = "append"
        } else if (inferenceCount < numOfInferences! && recordingBuffer.count == inputSize!){
            state =  "recognise"
        } else if (inferenceCount < numOfInferences! && recordingBuffer.count > inputSize!) {
            state = "trimAndRecognise"
        } else if (inferenceCount == numOfInferences! && recordingBuffer.count >= inputSize!) {
            state = "finalise"
        } else {
            state = "Incorrect state: \(displayLogCount())"
        }

        return state
    }

    func displayLogCount() -> String{
        return "\(inferenceCount)/\(numOfInferences!) | \(recordingBuffer.count)/\(inputSize!)"
    }

    @discardableResult
    func append(data: [Int16]) -> RecordingData{
         recordingBuffer.append(contentsOf: data)
         print("recordingBuffer length: \(displayLogCount())")
         return self
    }
    
    func emit(result: @escaping ([Int16]) -> Void) -> RecordingData{
        // Swift.print(recordingBuffer[0..<inputSize])
        result(Array(recordingBuffer[0..<inputSize!]))
        return self
        
    }

    @discardableResult
    func trimExcessToNewBuffer() -> RecordingData{
        let excessRecordingBuffer: [Int16] = Array(recordingBuffer[inputSize!..<recordingBuffer.count])
        recordingBuffer = excessRecordingBuffer
        return self
    }

    func updateCount() -> RecordingData{
         inferenceCount += 1
         return self
    }

    @discardableResult
    func clear() -> RecordingData{
        recordingBuffer = []  
        return self
    }

    @discardableResult
    func resetCount() -> RecordingData{
        inferenceCount = 1
        return self
    }

}
