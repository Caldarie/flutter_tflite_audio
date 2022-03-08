package flutter.tflite_audio;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import org.apache.commons.math3.complex.Complex;


/* Used for debugging. Implement this??
Log.v(LOG_TAG, "smax: " + audioData.getMaxAbsoluteValue(audioBuffer));
Log.v(LOG_TAG, "smin: " + audioData.getMinAbsoluteValue(audioBuffer));
Log.v(LOG_TAG, "audio data: " + Arrays.toString(audioBuffer));
*/

public class AudioProcessing {

    private static final String LOG_TAG = "Audio_Data";
    
    //TODO -  user controlled ranges?
    public short[] addSilence(int missingSamples, short[] audioChunk, int indexCount) {
        short[] padding = new short[missingSamples];
        Random random = new Random();
        Log.d("Preprocess:", "Padding " + missingSamples + " samples to audioChunk..");
        for (int x = 0; x < missingSamples; x++) {
            int rand = random.nextInt(10 + 10) - 10; //range from negative to positive
            // int rand = 0;
            short value = (short) rand;
            padding[x] = value;
        }
        System.arraycopy(padding, 0, audioChunk, indexCount, missingSamples);
        return audioChunk;
    }

    public float[][] transpose2D(float[][] matrix){
	    int m = matrix.length;
	    int n = matrix[0].length;

	    float[][] transposedMatrix = new float[n][m];

	    for(int x = 0; x < n; x++) {
	        for(int y = 0; y < m; y++) {
	            transposedMatrix[x][y] = matrix[y][x];
	        }
	    }
	    return transposedMatrix;
	}


    public float[][] normalise(short [] inputBuffer16){
        final float maxRes16 = (float) Math.pow(2, 15) -1; //outputs 32767.0f
        float[][] floatInputBuffer = new float [1][inputBuffer16.length];

        for (int i = 0; i < inputBuffer16.length; ++i) {
            floatInputBuffer[0][i] = inputBuffer16[i] / maxRes16;
        }

        return floatInputBuffer;
	}


    public float[][] normaliseAndTranspose(short [] inputBuffer16){
        final float maxRes16 = (float) Math.pow(2, 15) -1; //outputs 32767.0f
        float[][] floatInputBuffer = new float [inputBuffer16.length][1];

        for (int i = 0; i < inputBuffer16.length; ++i) {
            floatInputBuffer[i][0] = inputBuffer16[i] / maxRes16;
        }

        return floatInputBuffer;
	}


    public float [] normalizeBySigned16(short [] inputBuffer16){
        final float maxRes16 = (float) Math.pow(2, 15) -1; //outputs 32767.0f
        float inputBuffer32[] = new float[inputBuffer16.length];

        //normalise audio to -1 to 1 values
        for (int i = 0; i < inputBuffer16.length; ++i)
            inputBuffer32[i] = inputBuffer16[i] / maxRes16;
            // inputBuffer32[i] = inputBuffer16[i] / 32767.0f;

        return inputBuffer32;
    }

    public float [] normalizeByMaxAmplitude(short[] inputBuffer16){
              //normalise what??
        float [] result = new float [inputBuffer16.length];
        short wmax = getMaxAbsoluteValue(inputBuffer16);
        for (int i = 0; i < inputBuffer16.length; ++i)
            result[i] = (float) inputBuffer16[i] / wmax;

        return result;
    }

     
    //https://github.com/mkvenkit/simple_audio_pi/blob/main/simple_audio.py
    public float [] scaleAndCentre(float [] inputBuffer32){
        
        float [] result = new float [inputBuffer32.length];
        final float ptp = getMaxMinRange(inputBuffer32);
        final float min = getMinValue(inputBuffer32);
       
        // Log.d(LOG_TAG, "audio chunk: " + Arrays.toString(inputBuffer32));
        Log.d(LOG_TAG, "ptp: " + ptp);
        Log.d(LOG_TAG, "min: " + min);
    
        //scale to center waveform 
        for (int i = 0; i < inputBuffer32.length; ++i)
            result[i] = 2.0f*(inputBuffer32[i] - min) / (float)(ptp - 1);

        return result;
    }

    // public float[][] doubleTo2dFloat(double[][] doubleInput) {

    //     int height = doubleInput.length;
    //     int width = doubleInput[0].length;

    //     float[][] floatOutput = new float[height][width];
    //     for (int h = 0; h < height; h++) {
    //         for (int w = 0; w < width; w++) {
    //             floatOutput[h][w] = (float) doubleInput[h][w];
    //         }
    //     }
    //     return floatOutput;
    // }
    

     // preprocessing
    public byte[] appendByteData(byte[] src, byte[] dst) {
        byte[] result = new byte[src.length + dst.length];
        System.arraycopy(src, 0, result, 0, src.length);
        System.arraycopy(dst, 0, result, src.length, dst.length);
        return result;
    }

    public short[] appendShortData(short[] src, short[] dst) {
        short[] result = new short[src.length + dst.length];
        System.arraycopy(src, 0, result, 0, src.length);
        System.arraycopy(dst, 0, result, src.length, dst.length);
        return result;
    }

    //https://stackoverflow.com/questions/40361324/maximum-minus-minimum-in-an-array
    private float getMaxMinRange(float [] input){
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        for (float elem : input) {
            if (elem < min) min = elem;
            if (elem > max) max = elem;
        }
        return (float) (max - min);
    }

    //http://www.java2s.com/example/java/collection-framework/calculates-max-absolute-value-in-short-type-array.html
    // private float getMaxAbsoluteValue(float[] input) {
    //     float max = Float.MIN_VALUE;
    //     for (int i = 0; i < input.length; i++) {
    //         if (Math.abs(input[i]) > max) {
    //             max = (float) Math.abs(input[i]);
    //         }
    //     }
    //     return max;
    // }

    private short getMaxAbsoluteValue(short[] input) {
        short max = Short.MIN_VALUE;
        for (int i = 0; i < input.length; i++) {
            if (Math.abs(input[i]) > max) {
                max = (short) Math.abs(input[i]);
            }
        }
        return max;
    }

    private float getMinValue(float[] input) {
        float min = Float.MAX_VALUE;
        for (int i = 0; i < input.length; i++) {
            if (Math.abs(input[i]) < min) {
                min = (float) input[i];
            }
        }
        return min;
    }

    // private float getMinAbsoluteValue(float[] input) {
    //     float min = Float.MAX_VALUE;
    //     for (int i = 0; i < input.length; i++) {
    //         if (Math.abs(input[i]) < min) {
    //             min = (float) Math.abs(input[i]);
    //         }
    //     }
    //     return min;
    // }

    // public short getMinAbsoluteValue(short[] input) {
    //     short min = Short.MAX_VALUE;
    //     for (int i = 0; i < input.length; i++) {
    //         if (Math.abs(input[i]) < min) {
    //             min = (short) Math.abs(input[i]);
    //         }
    //     }
    //     return min;
    // }
}
