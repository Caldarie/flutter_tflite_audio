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

/* @escaping
https://www.donnywals.com/what-is-escaping-in-swift/
*/

/* optionals "?" on class variables
avoid need to call init() since we are using setters
*/

class RecordingData{

    private var audioLength: Int?
    private var numOfInferences: Int?

    private var inferenceCount = 1
    private var recordingBuffer: [Int16] = []
    private var result = [Int16]()
    
    func setAudioLength(audioLength: Int){
        self.audioLength = audioLength
    }

    func setNumOfInferences(numOfInferences: Int){
        self.numOfInferences = numOfInferences
    }

    func getState() -> String{
        var state: String = ""

        if(inferenceCount <= numOfInferences! && recordingBuffer.count < audioLength!){
            state = "append"
        } else if (inferenceCount < numOfInferences! && recordingBuffer.count == audioLength!){
            state =  "recognise"
        } else if (inferenceCount < numOfInferences! && recordingBuffer.count > audioLength!) {
            state = "trimAndRecognise"
        } else if (inferenceCount == numOfInferences! && recordingBuffer.count >= audioLength!) {
            state = "finalise"
        } else {
            state = "Incorrect state: \(displayLogCount())"
        }

        return state
    }

    func displayLogCount() -> String{
        return "\(inferenceCount)/\(numOfInferences!) | \(recordingBuffer.count)/\(audioLength!)"
    }

    @discardableResult
    func append(data: [Int16]) -> RecordingData{
         recordingBuffer.append(contentsOf: data)
         print("recordingBuffer length: \(displayLogCount())")
         return self
    }
    
    func emit(result: @escaping ([Int16]) -> Void) -> RecordingData{
        // Swift.print(recordingBuffer[0..<audioLength])
        result(Array(recordingBuffer[0..<audioLength!]))
        return self
        
    }

    @discardableResult
    func trimExcessToNewBuffer() -> RecordingData{
        let excessRecordingBuffer: [Int16] = Array(recordingBuffer[audioLength!..<recordingBuffer.count])
        print("Excess samples of: (\(excessRecordingBuffer.count)) found. Appending to new buffer..")
        recordingBuffer = excessRecordingBuffer
        print("recordingBuffer length: \(displayLogCount())")
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
