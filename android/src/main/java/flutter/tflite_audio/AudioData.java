package flutter.tflite_audio;

import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class AudioData {

    // preprocessing
    public short[] addSilence(int remain, short[] audioChunk, int indexCount) {
        short[] padding = new short[remain];
        Random random = new Random();
        Log.d("Preprocess:", "Padding " + remain + " samples to audioChunk..");
        for (int x = 0; x < remain; x++) {
            int rand = random.nextInt(10 + 10) - 10;
            short value = (short) rand;
            padding[x] = value;
        }
        System.arraycopy(padding, 0, audioChunk, indexCount, remain);
        return audioChunk;
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
