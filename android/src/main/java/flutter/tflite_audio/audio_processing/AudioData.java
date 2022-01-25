package flutter.tflite_audio;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import org.apache.commons.math3.complex.Complex;

public class AudioData {
    
    //TODO -  user controlled ranges?
    public short[] addSilence(int remain, short[] audioChunk, int indexCount) {
        short[] padding = new short[remain];
        Random random = new Random();
        Log.d("Preprocess:", "Padding " + remain + " samples to audioChunk..");
        for (int x = 0; x < remain; x++) {
            int rand = random.nextInt(10 + 10) - 10; //range from negative to positive
            short value = (short) rand;
            padding[x] = value;
        }
        System.arraycopy(padding, 0, audioChunk, indexCount, remain);
        return audioChunk;
    }

    //TODO -  user controlled ranges?
    public float[] addSilence(int remain, float[] audioChunk, int indexCount) {
        float[] padding = new float[remain];
        Random random = new Random();
        Log.d("Preprocess:", "Padding " + remain + " samples to audioChunk..");
        for (int x = 0; x < remain; x++) {
            float rand = random.nextFloat() * (0.01f - 0.001f) + 0.001f; //range from negative to positive
            float value = (float) rand;
            padding[x] = value;
        }
        System.arraycopy(padding, 0, audioChunk, indexCount, remain);
        return audioChunk;
    }


    public float[][] transpose(float[][] matrix){
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

    public float [][] normalize(float [][] floatInput){
        int m = floatInput.length;
	    int n = floatInput[0].length;

        float[][] normalizedInput = new float[n][m];

	    for(int x = 0; x < n; x++) {
	        for(int y = 0; y < m; y++) {
	            normalizedInput[x][y] = floatInput[x][y] / 32767.0f;
	        }
	    }

	    return normalizedInput;
    }

    public float[][] complexTo2DFloat(Complex[][] c) {
        final int length = c.length;
        float[][] f = new float[length][];
        for (int n = 0; n < length; n++) {
            f[n] = complexTo1DFloat(c[n]);
        }
        return f;
    }


    public float[] complexTo1DFloat(Complex[] c) {
        int index = 0;
        final float[] f = new float[c.length];
        for (Complex cc : c) {
            float value = (float) cc.abs();
            f[index] = padNanValues(value);
            index++;
        }
        return f;
    }

    public float padNanValues(float value){
        Random random = new Random();
        float randF = random.nextFloat() * (0.01f - 0.001f) + 0.001f;
        return Float.isFinite(value) ? value : randF;
    }

    //https://github.com/lucasronchetti/tg_aedes_detector/blob/4dd323620238833c66737a7fdd67fadac73d503a/aedes_detector/app/src/main/java/com/example/aedesdetector/spec/MFCC.java#L36
    public float[][] convert(double[][] doubleInput) {
        float[][] floatArray = new float[doubleInput.length][];
        for (int i = 0 ; i < doubleInput.length; i++)
        {
            floatArray[i] = new float[doubleInput[i].length];
            for (int j = 0; j < doubleInput[i].length; j++) {
                floatArray[i][j] = (float) ((doubleInput[i][j] / 80 ) + 1);
            }
        }
        return floatArray;
    }

    
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

}
