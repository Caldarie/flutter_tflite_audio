package flutter.tflite_audio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class RecordingTest {

    @Test
    public void testSingleSplice() {

        short [] recordingData = {1, 2, 3, 4};
        int audioLength = 4;
        int numOfInferences = 1;
        double overlap = 0.0;
        short [] expectedData = {1, 2, 3, 4};

        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences, overlap);
        assertArrayEquals(convertToArray(result), expectedData);
    }

    @Test
    public void testSingleSplice_lackDataEven() {

        short [] recordingData = {1, 2};
        int audioLength = 4;
        int numOfInferences = 1;
        double overlap = 0.0;
        short [] expectedData = {1, 2, 1, 2};

        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences, overlap);
        assertArrayEquals(convertToArray(result), expectedData);
    }

    @Test
    public void testSingleSplice_lackDataOdd() {

        short [] recordingData = {1, 2, 3};
        int audioLength = 4;
        int numOfInferences = 1;
        double overlap = 0.0;
        short [] expectedData = {1, 2, 3, 1};

        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences, overlap);
        assertArrayEquals(convertToArray(result), expectedData);
    }

    // remainingLength = audioLength - preReadCount
    // preReadCount + remainingLength + (bufferSize - remainingLength) + overlapLength ;

    @Test
    public void testSingleSplice_withExcess() {

        short [] recordingData = {1, 2, 3, 4, 5};
        int audioLength = 4;
        int numOfInferences = 1;
        double overlap = 0.0;
        short [] expectedData = {1, 2, 3, 4};

        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences, overlap);
        assertArrayEquals(convertToArray(result), expectedData);
    }

    @Test
    public void testMultiSplice() {

        short [] recordingData = {1, 2, 3, 4};
        int audioLength = 4;
        int numOfInferences = 3;
        double overlap = 0.0;
        short [] expectedData = {1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4};

        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences, overlap);
        assertArrayEquals(convertToArray(result), expectedData);
    }

    @Test
    public void testMultiSplice_lackDataEven() {

        short [] recordingData = {1, 2};
        int audioLength = 4;
        int numOfInferences = 3;
        double overlap = 0.0;
        short [] expectedData = {1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2};

        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences, overlap);
        assertArrayEquals(convertToArray(result), expectedData);
    }

    @Test
    public void testMultiSplice_lackDataOdd() {

        short [] recordingData = {1, 2, 3};
        int audioLength = 4;
        int numOfInferences = 3;
        double overlap = 0.0;
        short [] expectedData = {1, 2, 3, 1, 2, 3, 1 ,2, 3, 1, 2, 3};

        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences, overlap);
        assertArrayEquals(convertToArray(result), expectedData);
    }

    @Test
    public void testMultiSplice_withExcess() {
        //for overlapping situations

        short [] recordingData = {1, 2, 3, 4, 5, 6};
        int audioLength = 4;
        int numOfInferences = 3;
        double overlap = 0.0;
        short [] expectedData = {1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6};

        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences, overlap);
        assertArrayEquals(convertToArray(result), expectedData);
    }


    @Test
    public void testOverlap_equalData_evenBuffer() {

        short [] recordingData = {1, 2, 3, 4};
        int audioLength = 4;
        int numOfInferences = 5;
        double overlap = 0.5;
        short [] expectedData = {1, 2, 3, 4,
                                       3, 4, 1, 2,
                                             1, 2, 3, 4,
                                                   3, 4, 1, 2,
                                                         1, 2, 3, 4};

        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences, overlap);
        assertArrayEquals(convertToArray(result), expectedData);
    }



    @Test
    public void testOverlap_equalData_oddBuffer() {

        short [] recordingData = {1, 2, 3};
        int audioLength = 3;
        int numOfInferences = 5;
        double overlap = 0.25;
        short [] expectedData = {1, 2, 3,
                                       3, 1, 2,
                                             2, 3, 1,
                                                   1, 2, 3,
                                                         3, 1, 2 };

        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences, overlap);
        assertArrayEquals(convertToArray(result), expectedData);
    }


    @Test
    public void testOverlap_lackData_oddBuffer1() {

        short [] recordingData = {1, 2, 3};
        int audioLength = 4;
        int numOfInferences = 4;
        double overlap = 0.25;
        short [] expectedData = {1, 2, 3, 1,
                                          1, 2, 3, 1,
                                                    1, 2, 3, 1,
                                                             1, 2, 3, 1};

        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences, overlap);
        assertArrayEquals(convertToArray(result), expectedData);
    }

    @Test
    public void testOverlap_lackData_oddBuffer2() {

        short [] recordingData = {1, 2, 3};
        int audioLength = 4;
        int numOfInferences = 5;
        double overlap = 0.5;
        short [] expectedData = {1, 2, 3, 1,
                                       3, 1, 2, 3,
                                             2, 3, 1, 2,
                                                   1, 2, 3, 1,
                                                         3, 1, 2, 3};


        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences, overlap);
        assertArrayEquals(convertToArray(result), expectedData);
    }

    @Test
    public void testOverlap_lackData_evenBuffer() {

        short [] recordingData = {1, 2, 3, 4};
        int audioLength = 6;
        int numOfInferences = 3;
        double overlap = 0.3;
        short [] expectedData = {1, 2, 3, 4, 1, 2,
                                             1, 2, 3, 4, 1, 2,
                                                         1, 2, 3, 4, 1, 2};

        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences, overlap);
        assertArrayEquals(convertToArray(result), expectedData);
    }

    @Test
    public void testOverlap_excessData_evenBuffer() {

        short [] recordingData = {1, 2, 3, 4, 5, 6};
        int audioLength = 4;
        int numOfInferences = 3;
        double overlap = 0.45;
        short [] expectedData = {1, 2, 3, 4, 1, 2,
                1, 2, 3, 4, 1, 2,
                1, 2, 3, 4, 1, 2};

        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences, overlap);
        assertArrayEquals(convertToArray(result), expectedData);
    }



    @Test
    public void testOverlap3_overThreshold() {

        short [] recordingData = {1, 2, 3};
        int audioLength = 4;
        int numOfInferences = 4;
        double overlap = 0.70;
        short [] expectedData = {};
                // lack data + overlap 3
                //        1, 2, 3, 1, <- excess 2
                //           2, 3, 1, 2, <- overexcess: 1
                //              3, 1, 2, 3, <- overexcess: 0
                //                 1, 2, 3, 1, <- excess 2

        try {
            List<Short> result = mockRecording(recordingData, audioLength, numOfInferences, overlap);
        }
        catch (AssertionError error) {
            Assert.assertEquals("Overlap range must be from 0.0 to 0.5", error.getMessage());
        }

    }


////
////        //lack data + overlap 2
////        1, 2, 3, 1, <- excess 2
////                3, 1, 2, 3, <- excess 0
////                       2, 3, 1, 2, <- excess 1
////                             1, 2, 3, 1,  <- excess 2
////
////        //lack data + overlap 3
////        1, 2, 3, 1, <- excess 2
////           2, 3, 1, 2, <- overexcess: 1
////              3, 1, 2, 3, <- overexcess: 0
////                 1, 2, 3, 1, <- excess 2
////
////        //audioLength 5 / buffer 3
////        //lack data + overlap 1
////        1, 2, 3, 1, 2, <- excess 1
////                    2, 3, 1, 2, 3, <- excess 0
////                                3, 1, 2, 3, 1, <- excess 2
////                                            1, 2, 3, 1, 2, <-excess 1
////
////        //lack data + overlap 2
////        1, 2, 3, 1, 2, <- excess 1
////                 1, 2, 3, 1, 2, <- excess 1
////                          1, 2, 3, 1, 2 <- excess 1
////
////        //lack data + overlap 3
////        1, 2, 3, 1, 2,    <- excess 1
////              3, 1, 2, 3, 1,  <- excess 2
////                    2, 3, 1, 2, 3, <- excess 0
////                          1, 2, 3, 1, 2  <- excess 1
////
////        //lack data + overlap 4
////        1, 2, 3, 1, 2,  <- excess 1
////           2, 3, 1, 2, 3,  <- excess 0
////              3, 1, 2, 3, 1,  <- excess 2
////                  1, 2, 3, 1, 2,  <- excess 1
////
////        //audioLength 5/ buffer 4
////
////        //lack data + overlap 3
////        1, 2, 3, 4, 1, <- excess 3
////              3, 4, 1, 2, 3, <- excess 0
////                    1, 2, 3, 4, 1  <- excess 3
////
////        //lack data + overlap 4
////        1, 2, 3, 4, 1, <- excess 3
////           2, 3, 4, 1, 2, <- overexcess 2
////              3, 4, 1, 2, 3, <- overexcess 1
////                 4, 1, 2, 3, 4, <- excess 0
////                    1, 2, 3, 4, 1, <- excess 3
////
////
////        //audio length 4 / buffer 4
////        1, 2, 3, 4, <- excess 0
////                 4, 1, 2, 3, <- excess 1
////                          3, 4, 1, 2, <- excess 2
////                                   2, 3, 4, 1,  <- excess 3
////                                            1, 2, 3, 4  <- excess 0
////
////        1, 2, 3, 4, <- excess 0
////              3, 4, 1, 2, <-excess 2
////                    1, 2, 3, 4, <- excess 0
////
////        //audio length 5  //buffer 5
////        1, 2, 3, 4, 5,
////                 4, 5, 1, 2, 3,
////                           2, 3, 4, 5, 1,
////                                     5, 1, 2, 3, 4,
////                                              3, 4, 5, 1, 2,
////                                                        1, 2, 3, 4, 5,
//
//
//

//
//    @Test
//    public void testOverlap_lackData_odd1() {
//
//        short [] recordingData = {1, 2, 3};
//        int audioLength = 4;
//        int numOfInferences = 5;
//        double overlap = 0.0;
//        short [] expectedData = {1, 2, 3, 1,
//                                       3, 1, 2, 3, 1, 2, 3, 1,
//                                                         3, 1, 2, 3, 1, 2, 3, 1};
//
//        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences, overlap);
//        assertArrayEquals(convertToArray(result), expectedData);
//    }
//
//    @Test
//    public void testOverlap_lackData_odd2() {
//
//        short [] recordingData = {1, 2, 3};
//        int audioLength = 5;
//        int numOfInferences = 7;
//        double overlap = 0.0;
//        short [] expectedData = {1, 2, 3, 1, 2,
//                                       3, 1, 2, 3, 1,
//                                             2, 3, 1, 2, 3, 1, 2, 3, 1, 2,
//                                                                  3, 1, 2, 3, 1,
//                                                                        2, 3, 1, 2, 3, 1, 2, 3, 1, 2};
//
//        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences, overlap);
//        assertArrayEquals(convertToArray(result), expectedData);
//    }


//    @Test
//    public void testMultiSplice_excessData_withOverlap() {
//
//        short [] recordingData = {1, 2, 3, 4, 5};
//        int audioLength = 4;
//        int numOfInferences = 4;
//        double overlap = 0.0;
//        short [] expectedData = {1, 2, 3, 4,
//                3, 4, 5, 1, 2, 3, 4, 5,
//                4, 5, 1, 2};
//
//        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences, overlap);
//        assertArrayEquals(convertToArray(result), expectedData);
//    }

    //https://stackoverflow.com/questions/60072435/how-to-convert-short-into-listshort-in-java-with-streams
    public List<Short> convertToList(short [] shortArray){
        return IntStream.range(0, shortArray.length)
                .mapToObj(s -> shortArray[s])
                .collect(Collectors.toList());
    }

    //https://stackoverflow.com/questions/718554/how-to-convert-an-arraylist-containing-integers-to-primitive-int-array
    public short [] convertToArray(List <Short> shortList){
        short [] result = new short[shortList.size()];

        for (int i=0; i < result.length; i++) {
            result[i] = shortList.get(i);
        }

        return result;
    }

    public short [][] dataGenerator(){
        int bufferSize = 3;
        int inferenceCount = 4;
        short [][] data = new short[inferenceCount][bufferSize];
        short mockData = 0;

        for (int row = 0; row < data.length; row++) {
            for (int col = 0; col < data[row].length; col++) {
                mockData += 1;
                data[row][col] = mockData;
            }
        }
        
        System.out.println(Arrays.deepToString(data));
        return data;
    }

    public List<Short> mockRecording(short [] shortBuffer, int audioLength, int numOfInference, double overlap){

        boolean isRecording = true;
        List <Short> result = new ArrayList<>();

        RecordingData recordingData = new RecordingData(audioLength, shortBuffer.length ,numOfInference, overlap);

        while (isRecording) {

            List<String> states = recordingData.getStates();

            for(String state : states){
                
                switch (state) {
                        case "append":
                            System.out.println("appending");
                            recordingData
                                    .append(shortBuffer);
                            break;
        
                        case "recognise":
                            System.out.println("recognising");
                            recordingData
                                    .emit(data -> {
                                        System.out.println(Arrays.toString(data));
                                        result.addAll(convertToList(data));
                                    })
                                    .updateInference();
                            break;

                        case "clearExcess":
                            recordingData.clearExcessBuffer();
                            break;

                        // case "overflow":
                        //     recordingData
                        //         .extractOverlap()
                        //         .clearBuffer()
                        //         .appendOverlap();
                        //     break;
        
                        case "trimExcess":
                            recordingData
                                .extractRemain(shortBuffer)
                                .appendRemain();
                            break;
        
                        case "appendExcess":
                            recordingData
                                .extractOverlap() //last elements aded into buffer. Not fixed last two elemenets.
                                .extractExcess(shortBuffer)
                                .clearBuffer()
                                .appendOverlap()
                                .appendExcess();
                            break;
        
                         case "finalise":
                            System.out.println("finalising");
                            isRecording = false;
                            break;
        
                        default:
                            recordingData.displayErrorLog();
                            throw new AssertionError("Incorrect state when preprocessing");
                    }

            }
        }
        return result;
    }

}
