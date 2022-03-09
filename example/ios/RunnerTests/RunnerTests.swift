//
//  RunnerTests.swift
//  RunnerTests
//
//  Created by Michael Nguyen on 2022/03/08.
//

import XCTest
@testable import tflite_audio

class RunnerTests: XCTestCase {
    
    override func setUpWithError() throws {
        // Put setup code here. This method is called before the invocation of each test method in the class.
    }

    override func tearDownWithError() throws {
        // Put teardown code here. This method is called after the invocation of each test method in the class.
    }
    
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

    func testAudioSplice(){
        let array: [Int16] = [1, 2, 3, 4, 5, 6, 7]
        let audioFileData: AudioFileData = AudioFileData(audioLength: 2, bufferSize: array.count)
        var result: [Int16] = []
        
        for (index, data) in array.enumerated(){
            
            let state = audioFileData.getState(i: index)
            
            switch state{
                    case "recognising":
                        audioFileData
                            .append(data: data)
                            .displayCount()
                            .emit{ (audioChunk) in result.append(contentsOf: audioChunk) }
                            .reset()
                        break
                    case "appending":
                        audioFileData
                            .append(data: data)
                        break
                    case "finalising":
                        audioFileData
                            .append(data: data)
                            .displayCount()
                            .padSilence(i: index)
                            .emit{ (audioChunk) in result.append(contentsOf: audioChunk) }
                        break
                    default:
                        print("Error")
                }
            
            
        }
              
        XCTAssertEqual(result, array)
    }

//    func testExample() throws {
//        // This is an example of a functional test case.
//        // Use XCTAssert and related functions to verify your tests produce the correct results.
//        // Any test you write for XCTest can be annotated as throws and async.
//        // Mark your test throws to produce an unexpected failure when your test encounters an uncaught error.
//        // Mark your test async to allow awaiting for asynchronous code to complete. Check the results with assertions afterwards.
//    }
//
//    func testPerformanceExample() throws {
//        // This is an example of a performance test case.
//        measure {
//            // Put the code you want to measure the time of here.
//        }
//    }

}
