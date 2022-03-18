import Foundation
import AVFoundation
import RxCocoa
import RxSwift

/* RxSwift
https://github.com/chat-sdk/firestream-swift/blob/7cdcb2bfe18a50d982609d972e1fadc5d3d5655a/Sources/RX/MultiQueueSubject.swift
*/

/* observable schedulers/threads
https://stackoverflow.com/questions/37973445/does-the-order-of-subscribeon-and-observeon-matter
https://stackoverflow.com/questions/52931989/schedulers-for-network-requests-in-rxswift
*/

/* serial vs concurrent
let main = MainScheduler.instance
let concurrentMain = ConcurrentMainScheduler.instance

let serialBackground = SerialDispatchQueueScheduler.init(qos: .background)
let concurrentBackground = ConcurrentDispatchQueueScheduler.init(qos: .background)
https://www.avanderlee.com/swift/concurrent-serial-dispatchqueue/
*/

class Recording{

    private var bufferSize: Int
    private var audioLength: Int
    private var sampleRate: Int
    private var numOfInferences: Int

    private var recordingData: RecordingData
    private var audioEngine: AVAudioEngine
    private let subject: PublishSubject<[Int16]>
  
    init(bufferSize: Int, audioLength: Int, sampleRate: Int, numOfInferences: Int){

        recordingData = RecordingData()
        audioEngine = AVAudioEngine()
        subject = PublishSubject()

        self.bufferSize = bufferSize
        self.audioLength = audioLength
        self.sampleRate = sampleRate
        self.numOfInferences = numOfInferences

        recordingData.setAudioLength(audioLength: audioLength)
        recordingData.setNumOfInferences(numOfInferences: numOfInferences)
    }

    func getObservable() -> Observable<[Int16]>{
        return self.subject.asObservable()
    }

    func start(){

        let recordingFrame = AVAudioFrameCount(bufferSize)
        let inputNode = audioEngine.inputNode
        let inputFormat = inputNode.outputFormat(forBus: 0)
        let recordingFormat = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: Double(sampleRate), channels: 1, interleaved: true)
        guard let formatConverter =  AVAudioConverter(from:inputFormat, to: recordingFormat!) else {
            return
        }

         audioEngine.inputNode.installTap(onBus: 0, bufferSize: AVAudioFrameCount(bufferSize), format: inputFormat) { (buffer, time) in
            
            let pcmBuffer = AVAudioPCMBuffer(pcmFormat: recordingFormat!, frameCapacity: recordingFrame)
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
                self.splice(data: channelDataValueArray)
            }
        }

        audioEngine.prepare()
        do {
            try audioEngine.start()
        }
        catch {
            print(error.localizedDescription)
        }
    } 

    func stop(){
        audioEngine.stop()
        audioEngine.inputNode.removeTap(onBus: 0)
        self.subject.onCompleted()
    }

    func splice(data: [Int16]){

        let state = recordingData.getState()
            
        switch state{
            case "append":
                recordingData.append(data: data)
                break
            case "recognise":
                recordingData
                    .emit{ (result) in self.subject.onNext(result) }
                    .updateCount()
                    .clear()
                    .append(data: data)
                break
            case "trimAndRecognise":
                recordingData
                    .emit{ (result) in self.subject.onNext(result) }
                    .updateCount()
                    .trimExcessToNewBuffer()
                break
            case "finalise":
                recordingData
                    .emit{ (result) in self.subject.onNext(result) }
                    .updateCount()
                    .resetCount()
                    .clear()
                self.stop()
                break
            default:
                print("Error: \(state)")

        }
    }  
}
