package flutter.tflite_audio;

import android.util.Log;

import java.util.Arrays;

public class AudioData {

    private static final String LOG_TAG = "AudioData";

    private final int audioLength;
    private final int fileSize;

    private final boolean requirePadding;
    private final int numOfInferences;

    private int indexCount = 0;
    private int inferenceCount = 1;

    private short[] audioChunk;

    public AudioData(int audioLength, int fileSize){
        this.audioLength = audioLength;
        this.fileSize = fileSize;
        this.audioChunk = new short [audioLength];

        int excessSample = fileSize % audioLength;
        int missingSamples = audioLength - excessSample;
        requirePadding = getPaddingRequirement(excessSample, missingSamples);

        int totalWithPad = fileSize + missingSamples;
        int totalWithoutPad = fileSize - excessSample;
        numOfInferences = getNumOfInferences(totalWithoutPad, totalWithPad);
    }


    private boolean getPaddingRequirement(int excessSample, int missingSamples) {

        boolean hasMissingSamples = missingSamples != 0 || excessSample != audioLength;
        double missingSampleThreshold = 0.40;
        double missingSamplesRatio = (double) missingSamples / (double) audioLength;
        boolean shouldPad =  missingSamplesRatio < missingSampleThreshold;

        if (hasMissingSamples && shouldPad) return true;
        else if (hasMissingSamples && !shouldPad) return false;
        else if (!hasMissingSamples && shouldPad) return false;
        else return false;

    }

    private int getNumOfInferences(int totalWithoutPad, int totalWithPad) {
        return requirePadding ? (int) totalWithPad / audioLength : (int) totalWithoutPad / audioLength;
    }

    public String getState(int i) {
        boolean reachInputSize = (i + 1) % audioLength == 0;
        boolean reachFileSize = (i + 1) == fileSize;
        boolean reachInferenceLimit = inferenceCount == numOfInferences;

        if (reachInputSize && !reachInferenceLimit) {
            return "recognise";
        } // inferences > 1 && < not final
        else if (!reachInputSize && reachInferenceLimit && !reachFileSize) {
            return "append";
        } // Inferences = 1
        else if (!reachInputSize && !reachInferenceLimit) {
            return "append";
        } // Inferences > 1
        else if (!reachInputSize && reachInferenceLimit && reachFileSize) {
            return "padAndFinalise";
        } // for padding last infernce
        else if (reachInputSize && reachInferenceLimit) {
            return "finalise";
        } // inference is final
        else {
            return "Error";
        }
    }

    public AudioData append(short dataPoint) {
        audioChunk[indexCount] = dataPoint;
        indexCount += 1;
        return this;
    }

    public AudioData displayInference() {
        Log.d(LOG_TAG, "Inference count: " + (inferenceCount) + "/" + numOfInferences);
        return this;
    }

    /*
    https://www.javatpoint.com/java-closure
    https://www.geeksforgeeks.org/method-within-method-in-java/
    https://stackoverflow.com/questions/54566753/escaping-closure-in-swift-and-how-to-perform-it-in-java */
    public AudioData emit(AudioChunk audioChunk) {
        audioChunk.get(this.audioChunk);
        return this;
    }

    public AudioData reset() {
        indexCount = 0;
        inferenceCount += 1;
        audioChunk = new short[audioLength];
        return this;
    }

    public AudioData padSilence(int i) {
        AudioProcessing audioData = new AudioProcessing();
        int missingSamples = audioLength - indexCount;

        if (requirePadding) {
            Log.d(LOG_TAG, "Missing samples found in short audio chunk..");
            audioChunk = audioData.addSilence(missingSamples, audioChunk, indexCount);
        } else {
            Log.d(LOG_TAG, "Under threshold. Padding not required");
        }

        return this;
    }


}


