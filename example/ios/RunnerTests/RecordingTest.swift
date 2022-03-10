//
//  RecordingTest.swift
//  RecordingTest
//
//  Created by Michael Nguyen on 2022/03/10.
//

import XCTest
@testable import tflite_audio

class RecordingTest: XCTestCase {

    func testRecordingSplice(){
        
        let mockRecordingData: [Int16] = [1, 2, 3, 4]
        var result: [Int16] = []
        var mockIsRecording = true
        
        let recordingData: RecordingData = RecordingData()
        recordingData.setAudioLength(audioLength: 5)
        recordingData.setNumOfInferences(numOfInferences: 3)
        
        while(mockIsRecording){
            let state = recordingData.getState()
                 
             switch state{
                 case "append":
                     recordingData.append(data: mockRecordingData)
                     break
                 case "recognise":
                     recordingData
                    .emit{ (audioChunk) in  result.append(contentsOf: audioChunk)}
                         .updateCount()
                         .clear()
                         .append(data: mockRecordingData)
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
                         
                     mockIsRecording = false
                     break
                 default:
                     print("Error: \(state)")

             }
        }
        
        XCTAssertEqual(result, [1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3])
    }
}
