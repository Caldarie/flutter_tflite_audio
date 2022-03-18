package flutter.tflite_audio;

import android.util.Log;

import java.util.Arrays;

public class RecordingData {
    

    private static final String LOG_TAG = "RecordingData";

    private int audioLength;
    private int numOfInferences;
    private int bufferSize;

    //Count
    private int inferenceCount = 1;
    private int recordingOffset = 0;
    private int readCount; //keep track of state

    //Excess count
    private int remainingLength = 0;
    private int excessLength = 0;
    private short[] remainingFrame;
    private short[] excessFrame;

    //result
    private short[] recordingBuffer;

    public RecordingData(int audioLength, int bufferSize, int numOfInferences){
        this.audioLength = audioLength;
        this.bufferSize = bufferSize;
        this.numOfInferences = numOfInferences;

        this.recordingBuffer = new short[audioLength];
        this.readCount = bufferSize; //readcount should always be recordingOffset + bufferSize;
    }

    public void displayErrorLog(){
        System.out.println("inferenceCount " + inferenceCount);
        System.out.println("numOfInference " + numOfInferences);
        System.out.println("readCount " + readCount);
        System.out.println("audioLength " + audioLength);

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


    public RecordingData append(short[] shortData){

        //TODO - break when excess or remainframe is full

        System.arraycopy(shortData, 0, recordingBuffer, recordingOffset, shortData.length);
        recordingOffset += shortData.length;
        readCount =  recordingOffset + shortData.length;

        Log.v(LOG_TAG, "recordingOffset: " + recordingOffset + "/" + audioLength + " | inferenceCount: "
        + inferenceCount + "/" + numOfInferences);
        return this;
    }

    public RecordingData emit(AudioChunk audioChunk){
        audioChunk.get(recordingBuffer);
        return this;
    }

    public RecordingData updateInferenceCount(){
        inferenceCount += 1;
        return this;
    }

    public RecordingData updateRemain(){
        remainingLength = audioLength - recordingOffset;
        remainingFrame = new short[remainingLength];
        return this;
    }


    public RecordingData trimToRemain(short[] shortData){
        System.arraycopy(shortData, 0, remainingFrame, 0, remainingLength);
        System.arraycopy(remainingFrame, 0, recordingBuffer, recordingOffset, remainingLength);

        //!Dont need [readCount] as you are trimming the data. (Already readcount in append)
        recordingOffset += remainingLength;
        Log.v(LOG_TAG, "recordingOffset: " + recordingOffset + "/" + audioLength + " | inferenceCount: "
        + inferenceCount + "/" + numOfInferences + " (" + remainingLength +  " samples trimmed to remaining buffer)");
        return this;
    }

    public RecordingData updateExcess(){
        excessLength = bufferSize - remainingLength;
        excessFrame = new short[excessLength];
        return this;
    }

    public RecordingData addExcessToNew(short[] shortData){
        System.arraycopy(shortData, remainingLength, excessFrame, 0, excessLength);
        System.arraycopy(excessFrame, 0, recordingBuffer, 0, excessLength);
        
        //!need [readCount] as your add excess data in appeneded to new data
        recordingOffset += excessLength;
        readCount = recordingOffset + shortData.length;
        Log.v(LOG_TAG, "recordingOffset: " + recordingOffset + "/" + audioLength + " | inferenceCount: "
        + inferenceCount + "/" + numOfInferences + " (" + excessLength +  " excess samples added to new buffer)");
        return this;
    }

    public RecordingData clear(){
        recordingBuffer = new short[audioLength];
        recordingOffset = 0;
        readCount = bufferSize; //readcount should always be recordingOffset + bufferSize;
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

