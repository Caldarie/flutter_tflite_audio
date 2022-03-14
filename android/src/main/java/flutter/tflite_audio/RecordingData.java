package flutter.tflite_audio;

import android.util.Log;

public class RecordingData {
    

    private static final String LOG_TAG = "RecordingData";

    private int audioLength;
    private int numOfInferences;

    //Count
    private int inferenceCount = 1;
    private int recordingOffset = 0;
    private int readCount = 0; //keep track of state

    //Excess count
    private int remainingLength = 0;
    private int excessLength = 0;
    private short[] remainingFrame;
    private short[] excessFrame;

    //result
    private short[] recordingBuffer;

    public void setRecordingBufferSize(int recordingBufferSize){
        this.recordingBuffer = new short[recordingBufferSize];
    }

    public void setNumOfInferences(int numOfInferences){
        this.numOfInferences = numOfInferences;
    }

    public void setAudioLength(int audioLength){
        this.audioLength = audioLength;
    }


    public void displayErrorLog(){
        Log.v(LOG_TAG, "State error log:");
        Log.v(LOG_TAG, "inferenceCount " + inferenceCount);
        Log.v(LOG_TAG, "numOfInference " + numOfInferences);
        Log.v(LOG_TAG, "readCount " + readCount);
        Log.v(LOG_TAG, "audioLength " + audioLength);
    }

    public String getState(){
        if (inferenceCount <= numOfInferences && readCount < audioLength) { return "append"; }
        else if(inferenceCount < numOfInferences && readCount == audioLength){return "recognise"; }
        else if(inferenceCount == numOfInferences && readCount == audioLength){return "finalise";}
        else if(inferenceCount < numOfInferences && readCount > audioLength){ return "trimAndRecognise";}
        else if(inferenceCount == numOfInferences && readCount > audioLength){return "trimAndFinalise";}
        else{ return "error"; }
    }


    public RecordingData append(short[] recordingFrame){
        System.arraycopy(recordingFrame, 0, recordingBuffer, recordingOffset, recordingFrame.length);
        recordingOffset += recordingFrame.length;
        readCount =  recordingOffset + recordingFrame.length;

        Log.v(LOG_TAG, "recordingOffset: " + recordingOffset + "/" + audioLength + " | inferenceCount: "
        + inferenceCount + "/" + numOfInferences);
        return this;
    }

    public RecordingData emit(AudioChunk audioChunk){
        audioChunk.get(recordingBuffer);
        return this;
    }

    public RecordingData updateCount(){
        inferenceCount += 1;
        return this;
    }

    public RecordingData calculateExcess(){
        remainingLength = audioLength - recordingOffset;
        excessLength = readCount - audioLength;

        // Log.v(LOG_TAG, "remainingLength: " + remainingLength);
        // Log.v(LOG_TAG, "excesslength: " + excessLength);
        remainingFrame = new short[remainingLength];
        excessFrame = new short[excessLength];
        return this;
    }

    public RecordingData trimExcessToRemain(short[] recordingFrame){
        System.arraycopy(recordingFrame, 0, remainingFrame, 0, remainingLength);
        System.arraycopy(remainingFrame, 0, recordingBuffer, recordingOffset, remainingLength);
        
        recordingOffset += remainingLength;
        Log.v(LOG_TAG, "recordingOffset: " + recordingOffset + "/" + audioLength + " | inferenceCount: "
        + inferenceCount + "/" + numOfInferences + " (" + remainingLength +  " samples has been trimmed)");
        return this;
    }

    public RecordingData addExcessToNew(short[] recordingFrame){
        System.arraycopy(recordingFrame, remainingLength, excessFrame, 0, excessLength);
        System.arraycopy(excessFrame, 0, recordingBuffer, 0, excessLength);
        
        recordingOffset += excessLength;
        Log.v(LOG_TAG, "recordingOffset: " + recordingOffset + "/" + audioLength + " | inferenceCount: "
        + inferenceCount + "/" + numOfInferences + " (" + excessLength +  " samples added to new recording buffer)");
        return this;
    }

    public RecordingData clear(){
        recordingBuffer = new short[audioLength];
        recordingOffset = 0;
        readCount = 0;
        return this;
    }

    public RecordingData resetCount(){
        recordingOffset = 0;
        inferenceCount = 1;
        return this;
    }

    public RecordingData resetExcessCount(){
        remainingLength = 0;
        excessLength = 0;
        return this;
    }
}

