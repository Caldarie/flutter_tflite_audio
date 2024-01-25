package flutter.tflite_audio;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RecordingData {
    

    private static final String LOG_TAG = "RecordingData";

    private int audioLength;
    private int numOfInferences;
    private int bufferSize;
    private double overlap;

    //Count
    private int inferenceCount = 1;
    private int preReadCount = 0;
    private int readCount; //keep track of state

    //Excess count
    private int remainingLength = 0;
    private int excessLength = 0;
    private int overlapLength = 0;
    private int overlapPoint = 0;

    private short[] remainingFrame;
    private short[] excessFrame;
    private short[] overlapFrame = {};

    //result
    private short[] recordingBuffer;

    public RecordingData(int audioLength, int bufferSize, int numOfInferences, double overlap){
        this.audioLength = audioLength;
        this.bufferSize = bufferSize;
        this.numOfInferences = numOfInferences;
        this.overlap = overlap;

        this.overlapLength = (int) Math.ceil(audioLength * overlap);
        this.overlapPoint = audioLength - overlapLength;

        this.recordingBuffer = new short[audioLength];
        this.readCount = bufferSize; //readcount should always be preReadCount + bufferSize;
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
        //REMOVE ME!!!!
        return "reddit";
    }

    public List<String> getStates(){

        List<String> states = new ArrayList<String>();
        boolean isFinal = inferenceCount == numOfInferences;
        boolean isOverflow = excessLength + overlapLength == audioLength;

        System.out.println("state: excessLength: " + excessLength);
        System.out.println("state: excessOverlap: " + overlapLength);
        System.out.println("state: preReadCount: " + preReadCount);
        System.out.println("state: readCount: " + readCount);


        // if(isOverflow){
        //     System.out.println("state: OVERFLOW DETECTED!!");
        //     states.add("recognise");
        //     states.add("clearExcess");
        //     states.add("appendExcess");
        // } 

        if (readCount < audioLength) {

            states.add("append");
            return states;
            
        }else if(readCount == audioLength){

            states.add("append");
            states.add("recognise");
            states.add("appendExcess");
            if(isFinal) states.add("finalise"); 

        } else {
            states.add("trimExcess");
            states.add("recognise");
            states.add("appendExcess");
            if(isFinal) states.add("finalise");
        }
        
        // if (readCount < audioLength) {

        //     states.add("append");
            
        // }else if(readCount == audioLength){

        //     states.add("append");
        //     states.add("recognise");
        //     states.add("appendExcess");
        //     if(isFinal) states.add("finalise"); 

        // } else {
        //     states.add("trimExcess");
        //     states.add("recognise");
        //     states.add("appendExcess");
        //     if(isFinal) states.add("finalise");
        // }

        System.out.println(Arrays.toString(states.toArray()));

        return states;
    }

    public RecordingData emit(AudioChunk audioChunk){
        audioChunk.get(recordingBuffer);
        return this;
    }

    public RecordingData updateInference(){
        if(inferenceCount < numOfInferences) inferenceCount += 1;
        return this;
    }


    public RecordingData clearBuffer(){

        recordingBuffer = new short[audioLength];
        preReadCount = 0;
        readCount = bufferSize; //readcount should always be preReadCount + bufferSize;
        return this;
    }

    public RecordingData clearExcessBuffer(){
        excessLength = 0;
        excessFrame = new short [excessLength];
        remainingLength = 0;
        remainingFrame = new short [remainingLength];
        overlapLength = 0;
        overlapFrame = new short [overlapLength];

        return this;
    }

    public RecordingData append(short[] shortData){
        System.arraycopy(shortData, 0, recordingBuffer, preReadCount, shortData.length);

        preReadCount += shortData.length;
        readCount =  preReadCount + shortData.length;

        Log.v(LOG_TAG, "preReadCount: " + preReadCount + "/" + audioLength + " | inferenceCount: "
        + inferenceCount + "/" + numOfInferences);
        return this;
    }

    public RecordingData extractRemain(short[] shortData){
        remainingLength = audioLength - preReadCount;
        remainingFrame = new short[remainingLength];

        System.arraycopy(shortData, 0, remainingFrame, 0, remainingLength);
        return this;
    }

    //!Dont need [readCount] as you are trimming the data. (Already readcount in append)
    public RecordingData appendRemain(){
       
        System.arraycopy(remainingFrame, 0, recordingBuffer, preReadCount, remainingLength);
        // System.arraycopy(remainingFrame, 0, tempBuffer, preReadCount, remainingLength);
        preReadCount += remainingLength;

        Log.v(LOG_TAG, "preReadCount: " + preReadCount + "/" + audioLength + " | inferenceCount: "
        + inferenceCount + "/" + numOfInferences + " (" + remainingLength +  " samples trimmed to remaining buffer)");
        return this;
    }

    public RecordingData extractExcess(short[] shortData){

        boolean hasExcess = remainingLength > 0;

        if(hasExcess){
            excessLength = bufferSize - remainingLength;
            excessFrame = new short[excessLength];
            System.arraycopy(shortData, remainingLength, excessFrame, 0, excessLength);
            System.out.println("Excess frame: " + Arrays.toString(excessFrame));

        }else{
            excessLength = 0;
            excessFrame = new short [0];
        }

        return this;
    }

    public RecordingData appendExcess(){

        // System.out.println("append: excessLength: " + excessLength);
        // System.out.println("append: excessOverlap: " + overlapLength);
        // System.out.println("append: preReadCount: " + preReadCount);
        // System.out.println("append: readCount: " + readCount);
      
        System.arraycopy(excessFrame, 0, recordingBuffer, preReadCount, excessLength);
        preReadCount += excessLength;
        readCount = preReadCount + bufferSize;

        Log.v(LOG_TAG, "preReadCount: " + preReadCount + "/" + audioLength + " | inferenceCount: "
        + inferenceCount + "/" + numOfInferences + " (" + excessLength +  " excess samples added to new buffer)");
        return this;
    }

    public RecordingData extractOverlap(){

        boolean isOverlappable = overlap > 0.0 && overlap <= 0.5;

        if(overlap > 0.5){
            throw new AssertionError("Overlap range must be from 0.0 to 0.5");
        }

        if(isOverlappable){
            overlapFrame = new short[overlapLength];
            overlapFrame = Arrays.copyOfRange(recordingBuffer, overlapPoint, audioLength);
            // System.out.println("overlap point: " + overlapPoint);
            System.out.println("overlap frame: " + Arrays.toString(overlapFrame));
        }

        
        return this;
    }
    

    //!need [readCount] as your add overlap data in appeneded to new data
    public RecordingData appendOverlap(){

        System.arraycopy(overlapFrame, 0, recordingBuffer, preReadCount, overlapFrame.length);
        // System.arraycopy(overlapFrame, 0, tempBuffer, preReadCount, overlapFrame.length);
        preReadCount = overlapFrame.length;
        readCount = bufferSize + preReadCount;
        // System.out.println("After overlap: " + Arrays.toString(recordingBuffer));
        // System.out.println("preReadCount after overlap: " + preReadCount);
        // System.out.println("readCount after overlap: " + readCount);
        return this;
    }

}

