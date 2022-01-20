package flutter.tflite_audio;

import android.util.Log;
import java.util.Arrays;

class DisplayLogs {

    public void matrix(float [][] matrix) {
        for (int i = 0; i < matrix.length; i++) {
            Log.d("matrix", Arrays.toString(matrix[i]));
        }
    }


    // Preprocessing - silence padding on final inference
    public void logs(String LOG_TAG, short[] audioChunk, int indexCount, int inputSize) {
        Log.d(LOG_TAG, "audioChunk first element: " + audioChunk[0]);
        Log.d(LOG_TAG, "audioChunk second last element: " + audioChunk[indexCount - 2]);
        Log.d(LOG_TAG, "audioChunk last element: " + audioChunk[indexCount - 1]);
        Log.d(LOG_TAG, "audioChunk first missing element: " + audioChunk[indexCount]);
        Log.d(LOG_TAG, "audioChunk second missing element: " + audioChunk[indexCount + 1]);
        Log.d(LOG_TAG, "audioChunk second last missing element: " + audioChunk[inputSize - 2]);
        Log.d(LOG_TAG, "audioChunk last missing element: " + audioChunk[inputSize - 1]);
    }

    // Preprocessing - keep track of index & inference count
    public void logs(String LOG_TAG, int i, int indexCount, int inferenceCount, short[] audioChunk) {
        Log.d(LOG_TAG, "Index: " + i);
        Log.d(LOG_TAG, "IndexCount: " + indexCount);
        Log.d(LOG_TAG, "InferenceCount " + inferenceCount);
        Log.d(LOG_TAG, "audioChunk: " + Arrays.toString(audioChunk));
    }

    // Preprocesing - show basic info about raw audio data
    public void logs(String LOG_TAG, int byteDataLength, int shortDataLength, int numOfInferences) {
        Log.d(LOG_TAG, "byte length: " + byteDataLength);
        Log.d(LOG_TAG, "short length: " + shortDataLength);
        Log.d(LOG_TAG, "numOfInference " + numOfInferences);
    }

    // recording - before and after trim
    public void logs(String LOG_TAG, short[] recordingBuffer, int recordingOffset) {
        Log.v("RECORDING_BUFFER", "2nd last index before added date: " + recordingBuffer[recordingOffset - 2]);
        Log.v("RECORDING_BUFFER", "last buffer index before added data: " + recordingBuffer[recordingOffset - 1]);
        Log.v("RECORDING_BUFFER", "1st index after added data: " + recordingBuffer[recordingOffset]);
        Log.v("RECORDING_BUFFER", "2nd index after added data: " + recordingBuffer[recordingOffset + 1]);
    }

    // recording - final inference with padding
    public void logs(String LOG_TAG, short[] remainingRecordingFrame, int remainingRecordingLength,
            short[] excessRecordingFrame, int excessRecordingLength) {
        Log.v(LOG_TAG, "First remain index: " + remainingRecordingFrame[0]);
        Log.v(LOG_TAG, "last remain index: " +
                remainingRecordingFrame[remainingRecordingLength - 1]);
        Log.v(LOG_TAG, "First excess index: " + excessRecordingFrame[0]);
        Log.v(LOG_TAG, "last excess index: " +
                excessRecordingFrame[excessRecordingLength - 1]);
    }

    //recording - determine which inference is causing the strange behaviour
    public void logs(String LOG_TAG, int inferenceCount, int numOfInferences,
        int recordingOffset, int recordingOffsetCount, int inputSize) {
            Log.v(LOG_TAG, "countNumOfInference: " + inferenceCount);
            Log.v(LOG_TAG, "numOfInference: " + numOfInferences);
            Log.v(LOG_TAG, "recordingOffset: " + recordingOffset);
            Log.v(LOG_TAG, "recordingOffsetCount " + recordingOffsetCount);
            Log.v(LOG_TAG, "inputSize " + inputSize);
    }
  

}
