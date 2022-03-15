package flutter.tflite_audio;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//import java.util.stream.Stream;
//
import android.util.Log;

import androidx.core.content.res.TypedArrayUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AudioFileTest {

    private static final String LOG_TAG = "AudioFileTest";

    @Test
    public void test_singleSplice() {

        short [] audioData = {1, 2, 3};
        int audioLength = 3;
        short [] expectedData = new short [] {1, 2, 3};

        List<Short> result = splice(audioData, audioLength, audioData.length);
        assertEquals(convertToArray(result), expectedData);
    }

    //https://stackoverflow.com/questions/60072435/how-to-convert-short-into-listshort-in-java-with-streams
    public List <Short> convertToList(short [] shortArray){
        List <Short> result = IntStream.range(0, shortArray.length)
                .mapToObj(s -> shortArray[s])
                .collect(Collectors.toList());
        return result;
    }

    //https://stackoverflow.com/questions/718554/how-to-convert-an-arraylist-containing-integers-to-primitive-int-array
    public short [] convertToArray(List <Short> shortList){
        short [] result = new short[shortList.size()];

        for (int i=0; i < result.length; i++) {
            result[i] = shortList.get(i).shortValue();
        }

        return result;
    }

    public List<Short> splice(short [] shortBuffer, int audioLength, int bufferSize){

//        short[] result = {};
        List <Short> result = new ArrayList<Short>();
        AudioProcessing audioProcessing = new AudioProcessing();
        AudioData audioData = new AudioData(audioLength, bufferSize);
        boolean isSplicing = true;

        for (int i = 0; i < shortBuffer.length; i++) {

            if (isSplicing == false) {
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
