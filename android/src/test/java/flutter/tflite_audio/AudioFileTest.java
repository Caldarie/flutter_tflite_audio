package flutter.tflite_audio;

import org.junit.Test;
//import org.junit.Assert;
//import static org.junit.Assert.assertFalse;
//import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

import java.util.ArrayList;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AudioFileTest {

    @Test
    public void testSingleSplice() {

        short [] audioData = {1, 2, 3};
        int audioLength = 3;
        short [] expectedData = new short [] {1, 2, 3};

        List<Short> result = splice(audioData, audioLength, audioData.length);
        assertArrayEquals(convertToArray(result), expectedData);
    }

    @Test
    public void testSingleSplice_lackData_noPad(){
        short [] audioData = {1};
        int audioLength = 3;
        short [] expectedData = new short [] {};

        List<Short> result = splice(audioData, audioLength, audioData.length);
        assertArrayEquals(convertToArray(result), expectedData);
    }

    @Test
    public void testSingleSplice_lackData_withPad(){
        short [] audioData = {1, 2};
        int audioLength = 3;
        int expectedWithPadLength = 3;

        List<Short> resultList = splice(audioData, audioLength, audioData.length);
        short [] result = convertToArray(resultList);
        short [] resultNoPad = Arrays.copyOfRange(result, 0, audioData.length);

        System.out.println("singleSplice_lackData_withPad: " + resultList.toString());
        assertArrayEquals(resultNoPad, audioData);
        assertEquals(result.length, expectedWithPadLength);
    }

    @Test
    public void testMultiSplice() {

        short [] audioData = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        int audioLength = 5;
        short [] expectedData = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        List<Short> result = splice(audioData, audioLength, audioData.length);
        assertArrayEquals(convertToArray(result), expectedData);
    }

    @Test
    public void testMultiSplice_withExcess_noPadding() {

        short [] audioData = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        int audioLength = 5;
        short [] expectedData = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        List<Short> result = splice(audioData, audioLength, audioData.length);
        assertArrayEquals(convertToArray(result), expectedData);
    }

    @Test
    public void testMultiSplice_withExcess_withPadding() {

        short [] audioData = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        int audioLength = 6;
        int expectedWithPadLength = 12;

        List<Short> resultList = splice(audioData, audioLength, audioData.length);
        short [] result = convertToArray(resultList);
        short [] resultNoPad = Arrays.copyOfRange(result, 0, audioData.length);

        System.out.println("multiSplice_lackData_withPad: " + resultList.toString());
        assertArrayEquals(resultNoPad, audioData);
        assertEquals(result.length, expectedWithPadLength);
    }


    //https://stackoverflow.com/questions/60072435/how-to-convert-short-into-listshort-in-java-with-streams
    public List <Short> convertToList(short [] shortArray){
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

    public List<Short> splice(short [] shortBuffer, int audioLength, int bufferSize){

        List <Short> result = new ArrayList<Short>();
        AudioProcessing audioProcessing = new AudioProcessing();
        AudioData audioData = new AudioData(audioLength, bufferSize);
        boolean isSplicing = true;

        for (int i = 0; i < shortBuffer.length; i++) {

            if (!isSplicing) {
                break;
            }

            short dataPoint = shortBuffer[i];

            switch (audioData.getState(i)) {
                case "append":
                    audioData
                        .append(dataPoint);
                break;
                case "recognise":
                    System.out.println("recognising");
                    audioData
                        .append(dataPoint)
                        .displayInference()
                        .emit(data -> {
                            result.addAll(convertToList(data));
                        })
                        .reset();
                    break;
                case "finalise":
                    System.out.println("finalising");
                    audioData
                        .append(dataPoint)
                        .displayInference()
                        .emit(data -> {
                            result.addAll(convertToList(data));
                        });
                    isSplicing = false;
                    break;
                case "padAndFinalise":
                    System.out.println("padding and finalising");
                    audioData
                        .append(dataPoint)
                        .padSilence(i)
                        .displayInference()
                        .emit(data -> {
                            result.addAll(convertToList(data));
                        });
                    isSplicing = false;
                    break;
         
                default:
                    throw new AssertionError("Incorrect state when preprocessing");
            }
        }
        return result;
    }

}
