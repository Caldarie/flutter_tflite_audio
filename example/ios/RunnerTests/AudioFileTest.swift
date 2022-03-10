//
//  RunnerTests.swift
//  RunnerTests
//
//  Created by Michael Nguyen on 2022/03/08.
//

import XCTest
@testable import tflite_audio

class AudioFileTest: XCTestCase {
    
    
    func testSingleSplice(){
        
        let audioData: [Int16] = [1, 2, 3]
        let audioLength = 3
        let expectedData: [Int16] = [1, 2, 3]
        let result = splice(audioData: audioData, audioLength: audioLength, bufferSize: audioData.count)
 
    
        XCTAssertEqual(result, expectedData)
    }
    
    func testSingleSplice_LackData_NoPad(){
        //Assumes that threshold is set to 0.4
        //Mostly for situations where file sampleRate is below model input size
        //Todo - maybe force pad this situation???
        
        let audioData: [Int16] = [1]
        let audioLength = 3
        let expectedData: [Int16] = []
        let result = splice(audioData: audioData, audioLength: audioLength, bufferSize: audioData.count)
 
    
        XCTAssertEqual(result, expectedData)
    }
    
    func testSingleSplice_LackData_WithPadding(){
        //Assumes that threshold is set to 0.4
        
        let audioData: [Int16] = [1, 2]
        let audioLength = 3
        let expectedLength = 3
        let result = splice(audioData: audioData, audioLength: audioLength, bufferSize: audioData.count)
        let resultWithNoPad: [Int16] = Array(result[0..<audioData.count])
        
        print(result)
        XCTAssertEqual(resultWithNoPad, audioData)
        XCTAssertEqual(result.count, expectedLength)
    }
    
    
    func testMultiSplice(){
        
        let audioData: [Int16] = [1, 2, 3, 4, 5, 6, 7, 8, 9]
        let audioLength = 3
        let expectedData: [Int16] = [1, 2, 3, 4, 5, 6, 7, 8, 9]
        let result = splice(audioData: audioData, audioLength: audioLength, bufferSize: audioData.count)
 
    
        XCTAssertEqual(result, expectedData)
    }
    
    
    func testMultiSplice_WithExcess_NoPadding(){
        
        let audioData: [Int16] = [1, 2, 3, 4, 5, 6, 7]
        let audioLength = 3
        let expectedData: [Int16] = [1, 2, 3, 4, 5, 6]
        let result = splice(audioData: audioData, audioLength: audioLength, bufferSize: audioData.count)
 
    
        XCTAssertEqual(result, expectedData)
        
    }
    
    func testMultiSplice_WithExcess_WithPadding(){
        
        let audioData: [Int16] = [1, 2, 3, 4, 5, 6, 7, 8]
        let audioLength = 3
        let expectedLength = 9
        let result = splice(audioData: audioData, audioLength: audioLength, bufferSize: audioData.count)
        let resultWithNoPad: [Int16] = Array(result[0..<audioData.count])
        
        print(result)
        XCTAssertEqual(resultWithNoPad, audioData)
        XCTAssertEqual(result.count, expectedLength)
        
    }
    
    
    
    func splice(audioData: [Int16], audioLength: Int, bufferSize: Int) -> [Int16]{
        
        let audioFileData: AudioFileData = AudioFileData(audioLength: audioLength, bufferSize: bufferSize)
        var result: [Int16] = []
        var isSplicing = true
        
        for (index, data) in audioData.enumerated(){
            
            if(isSplicing == false){ break }
            let state = audioFileData.getState(i: index)
            
            switch state{
                case "recognise":
                    print("recognising")
                    audioFileData
                        .append(data: data)
                        .displayCount()
                        .emit{ (audioChunk) in result.append(contentsOf: audioChunk) }
                        .reset()
                    break
                case "append":
                    audioFileData
                        .append(data: data)
                        .displayCount() //only for testing
                    break
                case "finalise":
                    print("finalising")
                    audioFileData
                        .append(data: data)
                        .displayCount()
                        .emit{ (audioChunk) in result.append(contentsOf: audioChunk) }
                    isSplicing = false //BREAK HERE - excess data ignored
                    break
                case "padAndFinalise":
                    print("trimming and finalising")
                    audioFileData
                        .append(data: data)
                        .padSilence(i: index)
                        .displayCount()
                        .emit{ (audioChunk) in result.append(contentsOf: audioChunk)}
                    break
                default:
                    print("Error")
                
            }
        }
        
        return result
        
    }
}
