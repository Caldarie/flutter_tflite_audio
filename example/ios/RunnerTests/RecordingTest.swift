//
//  RecordingTest.swift
//  RecordingTest
//
//  Created by Michael Nguyen on 2022/03/10.
//

import XCTest
@testable import tflite_audio

class RecordingTest: XCTestCase {
    
    func test_singleSplice(){
        let recordingData: [Int16] = [1, 2, 3, 4]
        let audioLength = 4
        let numOfInferences = 1
        let expectedResult: [Int16] = [1, 2, 3, 4]
        let result = mockRecording(data: recordingData, length: audioLength, num: numOfInferences)
        XCTAssertEqual(result, expectedResult)
    }
    
    func test_singleSplice_lackData(){
        let recordingData: [Int16] = [1, 2, 3]
        let audioLength = 4
        let numOfInferences = 1
        let expectedResult: [Int16] = [1, 2, 3, 1]
        let result = mockRecording(data: recordingData, length: audioLength, num: numOfInferences)
        XCTAssertEqual(result, expectedResult)
    }
    
    
    func test_singleSplice_withExcess(){
        let recordingData: [Int16] = [1, 2, 3, 4, 5]
        let audioLength = 4
        let numOfInferences = 1
        let expectedResult: [Int16] = [1, 2, 3, 4]
        let result = mockRecording(data: recordingData, length: audioLength, num: numOfInferences)
        XCTAssertEqual(result, expectedResult)
    }
    
    
    
    func test_multiSplice(){
        let recordingData: [Int16] = [1, 2, 3, 4]
        let audioLength = 4
        let numOfInferences = 3
        let expectedResult: [Int16] = [1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4]
        let result = mockRecording(data: recordingData, length: audioLength, num: numOfInferences)
        XCTAssertEqual(result, expectedResult)
    }
    
    func test_multiSplice_lackData(){
        let recordingData: [Int16] = [1, 2, 3]
        let audioLength = 4
        let numOfInferences = 3
        let expectedResult: [Int16] = [1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3]
        let result = mockRecording(data: recordingData, length: audioLength, num: numOfInferences)
        XCTAssertEqual(result, expectedResult)
    }

    
    func test_multiSplice_withExcess(){
        
        let recordingData: [Int16] = [1, 2, 3, 4, 5]
        let audioLength = 4
        let numOfInferences = 3
        let expectedResult: [Int16] = [1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2]
        let result = mockRecording(data: recordingData, length: audioLength, num: numOfInferences)
        XCTAssertEqual(result, expectedResult)
    }

    func mockRecording(data: [Int16], length: Int, num: Int) -> [Int16]{
        
       
        var result: [Int16] = []
        var isRecording = true
        
        let recordingData: RecordingData = RecordingData()
        recordingData.setAudioLength(audioLength: length)
        recordingData.setNumOfInferences(numOfInferences: num)
        
        while(isRecording){
            let state = recordingData.getState()
                 
             switch state{
                 case "append":
                     recordingData.append(data: data)
                     break
                 case "recognise":
                     recordingData
                         .emit{ (audioChunk) in  result.append(contentsOf: audioChunk)}
                         .updateCount()
                         .clear()
                         .append(data: data)
                     break
                 case "trimAndRecognise":
                     recordingData
                         .emit{ (audioChunk) in  result.append(contentsOf: audioChunk)}
                         .updateCount()
                         .trimExcessToNewBuffer()
                     break
                 case "finalise":
                     recordingData
                         .emit{ (audioChunk) in  result.append(contentsOf: audioChunk)}
                         .updateCount()
                         .resetCount()
                         .clear()
                     isRecording = false
                     break
                 default:
                     print("Error: \(state)")

             }
        }
        
        return result
    }
    
}
