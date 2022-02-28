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
 
    init(fileURL: URL, inputSize: Int){
        self.pcmBuffer = getBuffer(fileURL: fileURL)
        self.subject = PublishSubject()
        self.audioFileData = AudioFileData(inputSize: inputSize, bufferSize: Int(pcmBuffer!.frameCapacity))
        print(Int(pcmBuffer!.frameCapacity))
    }
    
    func getBuffer(fileURL : URL) -> AVAudioPCMBuffer{

        let file = try! AVAudioFile(forReading: fileURL)
        let format = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: file.fileFormat.sampleRate, channels: 1, interleaved: false)
        let fileSize = file.length
        let frameSize = AVAudioFrameCount(fileSize)
    
        let pcmBuffer = AVAudioPCMBuffer(pcmFormat: format!, frameCapacity: frameSize)
        try! file.read(into: pcmBuffer!)
        
        return pcmBuffer!
        
    }

    func stop(){
        isSplicing = false
    }

    func splice(){
        
        isSplicing = true
        let buffer = UnsafeBufferPointer(start: pcmBuffer?.int16ChannelData![0], count:Int(pcmBuffer!.frameCapacity))
        
        for (index, data) in buffer.enumerated(){

            if(isSplicing == false){ break }

            let state = audioFileData!.getState(i: index)
            
            switch state{
                case "recognising":
                    audioFileData!
                        .append(data: data)
                        .displayCount()
                        .emit{ (result) in print("emit") }
                        .reset()
                    break
                case "appending":
                    print(index)
                    audioFileData!
                        .append(data: data)
                    break
                case "finalising":
                    audioFileData!
                        .append(data: data)
                        .displayCount()
                        .padSilence(i: index)
                        .emit{ (result) in print("emit") }
                    self.stop()
                    break
                default:
                    print("Error")
            }
        }
    }
}
