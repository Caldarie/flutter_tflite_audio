package flutter.tflite_audio;

import android.util.Log;

// import io.reactivex.Observable;
// import io.reactivex.Observer;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.PublishSubject;
// import org.apache.commons.math3.complex.Complex;
// import com.jlibrosa.audio.JLibrosa;

import java.nio.ShortBuffer;
import java.util.Arrays;
import java.nio.FloatBuffer;
import java.nio.ByteBuffer; //required for preprocessing
import java.nio.ByteOrder; //required for preprocessing

public class AudioSplicing{

    private static final String LOG_TAG = "Audio_Slicing";
    private AudioData audioData = new AudioData();
    private PublishSubject<short []> subject = PublishSubject.create();

    private ShortBuffer shortBuffer;
    private short[] shortAudioChunk;

    private int inputSize;
    private String inputType;
    private int fileSize;

    private int indexCount = 0;
    private int inferenceCount = 1;

    private boolean requirePadding;
    private int numOfInferences;

    private boolean isPreprocessing = false;


    public AudioSplicing(byte[] byteData, String inputType, int inputSize){

            shortBuffer = ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            this.inputSize = inputSize;
            this.inputType = inputType;

            calculateParameters();
      
    }

    public void startPreprocessing(){
        isPreprocessing = true;
    }

    public void stopPreprocessing(){
        isPreprocessing = false;
    }


    public void spliceAudio(){

        for (int i = 0; i < fileSize; i++) {

            if (isPreprocessing == false){
                subject.onComplete();
                break;
            } 

            switch (getState(i)) {
                case "recognising":
                    Log.d(LOG_TAG, "Recognising");
                    displayInferenceCount();
                    subject.onNext(shortAudioChunk);
                    reset(i);
                    break;

                case "finalising":
                    Log.d(LOG_TAG, "Finalising");
                    displayInferenceCount();
                    padSilenceToChunk(i);
                    stopPreprocessing();
                    subject.onNext(shortAudioChunk);
                    subject.onComplete();
                    break;

                case "appending":
                    appendDataToChunk(i);
                    break;

                default:
                    throw new AssertionError("Incorrect state when preprocessing");
            }

        }
    }

    public Observable<short []> getObservable() {
        return (Observable<short []>) this.subject;
     } 
     
     //!To debug requirePadding, simply change original [<] to > before (inputSize/2)
    //TODO - add unit test | [>] 2/2 or 5/5 | [<] returns 1/1 or 6/6
    private void calculateParameters(){
        fileSize = shortBuffer.limit(); //calculate how many bytes in 1 second in short array
        shortAudioChunk = new short[inputSize];

        int remainingSamples = fileSize % inputSize;
        int missingSamples = inputSize - remainingSamples;
        int totalWithPad = fileSize + missingSamples;
        int totalWithoutPad = fileSize - remainingSamples;
        boolean hasMissingSamples = missingSamples != 0 || remainingSamples != inputSize;
        boolean underThreshold = missingSamples < (int) inputSize * 0.75f;
        
        if(hasMissingSamples && underThreshold){ requirePadding = true; }
        else if(hasMissingSamples && !underThreshold) { requirePadding = false; }
        else if(!hasMissingSamples && underThreshold) { requirePadding = false; }
        else { requirePadding = false; }
        
        numOfInferences = requirePadding ? (int) totalWithPad/inputSize : (int) totalWithoutPad/inputSize;
    }
    

    private void displayInferenceCount(){
        Log.d(LOG_TAG, "Inference count: " + (inferenceCount) + "/" + numOfInferences);
    }

    private String getState (int i){
        boolean reachInputSize = (i + 1) % inputSize == 0;
        boolean reachFileSize = i == fileSize - 1;
        boolean reachInferenceLimit = inferenceCount == numOfInferences;

        if (reachInputSize && !reachInferenceLimit) { return "recognising"; } // inferences > 1 && < not final
        else if (!reachInputSize && reachInferenceLimit && !reachFileSize) { return "appending"; } //Inferences = 1
        else if(!reachInputSize && !reachInferenceLimit) { return "appending"; } //Inferences > 1
        else if (!reachInputSize && reachInferenceLimit && reachFileSize) { return "finalising"; } //for padding last infernce 
        else if(reachInputSize && reachInferenceLimit) { return "finalising"; } // inference is final
        else { return "Error"; }
    }


    private void reset(int i){
        indexCount = 0;
        inferenceCount += 1;
        shortAudioChunk = new short[inputSize];
        shortAudioChunk[indexCount] = shortBuffer.get(i);
    }

    private void appendDataToChunk(int i){
        shortAudioChunk[indexCount] = shortBuffer.get(i);
        indexCount += 1;
    }


    private void padSilenceToChunk(int i){  

        if(requirePadding){
            int missingSamples = inputSize - indexCount;
            Log.d(LOG_TAG, "Missing samples found in short audio chunk..");
            shortAudioChunk = audioData.addSilence(missingSamples, shortAudioChunk, indexCount);
        }else{
            int missingSamples = inputSize - indexCount;
            Log.d(LOG_TAG, "Missing samples of " + missingSamples + " are less than half of input. Padding not required");
        }

    }
}

