package flutter.tflite_audio;

import static org.junit.Assert.assertArrayEquals;

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
        short [] expectedData = {1, 2, 3, 4};

        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences);
        assertArrayEquals(convertToArray(result), expectedData);
    }

    @Test
    public void testSingleSplice_lackDataEven() {

        short [] recordingData = {1, 2};
        int audioLength = 4;
        int numOfInferences = 1;
        short [] expectedData = {1, 2, 1, 2};

        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences);
        assertArrayEquals(convertToArray(result), expectedData);
    }

    @Test
    public void testSingleSplice_lackDataOdd() {

        short [] recordingData = {1, 2, 3};
        int audioLength = 4;
        int numOfInferences = 1;
        short [] expectedData = {1, 2, 3, 1};

        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences);
        assertArrayEquals(convertToArray(result), expectedData);
    }

    @Test
    public void testSingleSplice_withExcess() {

        short [] recordingData = {1, 2, 3, 4, 5};
        int audioLength = 4;
        int numOfInferences = 1;
        short [] expectedData = {1, 2, 3, 4};

        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences);
        assertArrayEquals(convertToArray(result), expectedData);
    }

    @Test
    public void testMultiSplice() {

        short [] recordingData = {1, 2, 3, 4};
        int audioLength = 4;
        int numOfInferences = 3;
        short [] expectedData = {1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4};

        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences);
        assertArrayEquals(convertToArray(result), expectedData);
    }

    @Test
    public void testMultiSplice_lackDataEven() {

        short [] recordingData = {1, 2};
        int audioLength = 4;
        int numOfInferences = 3;
        short [] expectedData = {1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2};

        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences);
        assertArrayEquals(convertToArray(result), expectedData);
    }

    @Test
    public void testMultiSplice_lackDataOdd() {

        short [] recordingData = {1, 2, 3};
        int audioLength = 4;
        int numOfInferences = 3;
        short [] expectedData = {1, 2, 3, 1, 2, 3, 1 ,2, 3, 1, 2, 3};

        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences);
        assertArrayEquals(convertToArray(result), expectedData);
    }

    @Test
    public void testMultiSplice_withExcess() {

        short [] recordingData = {1, 2, 3, 4, 5, 6};
        int audioLength = 4;
        int numOfInferences = 3;
        short [] expectedData = {1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6};

        List<Short> result = mockRecording(recordingData, audioLength, numOfInferences);
        System.out.println(result.toString());
        assertArrayEquals(convertToArray(result), expectedData);
    }

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

    public List<Short> mockRecording(short [] shortBuffer, int audioLength, int numOfInferences){

        boolean isRecording = true;
        List <Short> result = new ArrayList<>();

        RecordingData recordingData = new RecordingData(audioLength, shortBuffer.length ,numOfInferences);

        while (isRecording) {

            switch (recordingData.getState()) {
                case "append":
                    System.out.println("appending");
                    recordingData
                            .append(shortBuffer);
                    break;

                case "recognise":
                    System.out.println("recognising");
                    recordingData
                            .append(shortBuffer)
                            .emit(data -> {
                                System.out.println(Arrays.toString(data));
                                result.addAll(convertToList(data));
                            })
                            .updateInferenceCount()
                            .clear();
                    break;

                case "finalise":
                    // Log.v(LOG_TAG, "finalising");
                    System.out.println("finalising");
                    recordingData
                            .append(shortBuffer)
                            .emit(data -> {
                                System.out.println(Arrays.toString(data));
                                result.addAll(convertToList(data));
                            });
                    isRecording = false;
                    break;

                case "trimAndRecognise":
                    System.out.println("Trimming and recognising");
                    recordingData
                            .updateRemain()
                            .trimToRemain(shortBuffer)
                            .emit(data -> {
                                System.out.println(Arrays.toString(data));
                                result.addAll(convertToList(data));
                            })
                            .updateInferenceCount()
                            .clear()
                            .updateExcess()
                            .addExcessToNew(shortBuffer);
                    break;

                case "trimAndFinalise":
                    System.out.println("Trimming and finalising");
                    recordingData
                            .updateRemain()
                            .trimToRemain(shortBuffer)
                            .emit(data -> {
                                System.out.println(Arrays.toString(data));
                                result.addAll(convertToList(data));
                            });
                    isRecording = false;
                    break;

                default:
                    recordingData.displayErrorLog();
                    throw new AssertionError("Incorrect state when preprocessing");
            }
        }
        return result;
    }

}
