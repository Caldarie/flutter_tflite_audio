import Foundation
import AVFoundation
import RxCocoa
import RxSwift

/* why nil is assigned to pcmBuffer abd subject
 https://stackoverflow.com/questions/34474545/self-used-before-all-stored-properties-are-initialized
 */

class AudioFile{

    private var pcmBuffer: AVAudioPCMBuffer? = nil
    private var subject: PublishSubject<[Int16]>? = nil
    private var audioFileData: AudioFileData? = nil

    private var isSplicing = false
 
    init(fileURL: URL, audioLength: Int){
        self.pcmBuffer = getBuffer(fileURL: fileURL)
        self.subject = PublishSubject()
        self.audioFileData = AudioFileData(audioLength: audioLength, bufferSize: Int(pcmBuffer!.frameCapacity))
        print(Int(pcmBuffer!.frameCapacity))
    }

    func getObservable() -> Observable<[Int16]>{
        return self.subject!.asObservable()
    }
    
    func getBuffer(fileURL : URL) -> AVAudioPCMBuffer{

        let pcmBuffer: AVAudioPCMBuffer

        do {
            let file = try! AVAudioFile(forReading: fileURL, commonFormat: .pcmFormatInt16, interleaved: false)
            let format = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: file.fileFormat.sampleRate, channels: 1, interleaved: false)    
            pcmBuffer = AVAudioPCMBuffer(pcmFormat: format!, frameCapacity: AVAudioFrameCount(file.length))!
            try file.read(into: pcmBuffer)
        } catch let error as NSError {
            print("Buffer loading error: ", error.localizedDescription)
        }
        
        return pcmBuffer
        
    }

    func stop(){
        isSplicing = false
        self.subject!.onCompleted()
        audioFileData = nil
    }

    func splice(){
        
        isSplicing = true
        let buffer = UnsafeBufferPointer(start: pcmBuffer?.int16ChannelData![0], count:Int(pcmBuffer!.frameCapacity))
        
        for (index, data) in buffer.enumerated(){

            if(isSplicing == false){ break }

            let state = audioFileData!.getState(i: index)
            
            switch state{
                case "recognise":
                    audioFileData!
                        .append(data: data)
                        .displayCount()
                        .emit{ (audioChunk) in self.subject!.onNext(audioChunk) }
                        .reset()
                    break
                case "append":
                    audioFileData!
                        .append(data: data)
                    break
                case "finalise":
                    audioFileData!
                        .append(data: data)
                        .displayCount()
                        .emit{ (audioChunk) in self.subject!.onNext(audioChunk) }
                    self.stop() //Force break loop - excess data will be ignored
                    break
                case "padAndFinalise":
                    audioFileData!
                        .append(data: data)
                        .padSilence(i: index)
                        .displayCount()
                        .emit{ (audioChunk) in self.subject!.onNext(audioChunk) }
                    self.stop()
                    break
                default:
                    print("Error")
            }
        }
    }
}
