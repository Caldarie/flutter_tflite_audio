package flutter.tflite_audio;

import android.util.Log;

// import io.reactivex.rxjava3.core.Observable;
// import io.reactivex.rxjava3.core.*;
import org.apache.commons.math3.complex.Complex;
import com.jlibrosa.audio.JLibrosa;

import java.nio.ShortBuffer;
import java.util.Arrays;
import java.nio.FloatBuffer;
import java.nio.ByteBuffer; //required for preprocessing
import java.nio.ByteOrder; //required for preprocessing

public class AudioSplicing{

    private static final String LOG_TAG = "Audio_Slicing";
    private AudioData audioData = new AudioData();

    private enum DataType { SHORT, FLOAT }
    private DataType dataType;

    private ShortBuffer shortBuffer;
    private short[] shortAudioChunk;

    private FloatBuffer floatBuffer;
    private float [] floatAudioChunk;

    private int inputSize;
    private String inputType;
    private int fileSize;

    private int indexCount = 0;
    private int inferenceCount = 1;

    private boolean requirePadding;
    private int numOfInferences;


    public AudioSplicing(byte[] byteData, String inputType, int inputSize){

        if(inputType.equals("rawAudio") || inputType.equals("decodedWav")){
            dataType = DataType.SHORT;
            shortBuffer = ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            fileSize = shortBuffer.limit(); //calculate how many bytes in 1 second in short array
            this.inputSize = inputSize;
            this.inputType = inputType;
            shortAudioChunk = new short[inputSize];

        }else{
            dataType = DataType.FLOAT;
            floatBuffer = ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
            fileSize = floatBuffer.limit();    
            this.inputSize = inputSize; 
            this.inputType = inputType;
            floatAudioChunk = new float[inputSize]; 
        }

        calculateRemain();
    }
 
    private void calculateRemain(){
        int remainingSamples = fileSize % inputSize;
        int missingSamples = inputSize - remainingSamples;
        int totalWithPad = fileSize + missingSamples;
        int totalWithoutPad = fileSize - remainingSamples;
        //!To debug requirePadding, simply change original [>] to < before (inputSize/2)
        requirePadding = remainingSamples > (int) inputSize/2 ? true : false; //TODO - Make this user controlled?
        numOfInferences = requirePadding ? (int) totalWithPad/inputSize : (int) totalWithoutPad/inputSize;
    }

    public int getFileSize (){
        return fileSize;
    }

    public boolean isSpectrogram(){
        return dataType == DataType.FLOAT ? true : false;
    }

    public short[] getShortChunk(){
        return shortAudioChunk;
    }


    public float[][] getFloatChunk(){

        JLibrosa jLibrosa = new JLibrosa();
        float [][] chunk;

        switch(inputType){

            case "melSpectrogram":
                // generateMelSpectroGram(float[] yValues, int mSampleRate, int n_fft, int n_mels, int hop_length)
                chunk = jLibrosa.generateMelSpectroGram(floatAudioChunk, 16000, 256, 129, 65);
                // if (showPreprocessLogs == true) display.matrix(melSpectrogram);
                // Log.d(LOG_TAG, "1. Mel Bin " + melSpectrogram.length);
                // Log.d(LOG_TAG, "2. Frames " + melSpectrogram[0].length);
                break;

            case "spectrogram":
                //generateSTFTFeatures(float[] magValues, int mSampleRate, int nMFCC, int n_fft, int n_mels, int hop_length)
                //https://stackoverflow.com/questions/62584184/understanding-the-shape-of-spectrograms-and-n-mels
                //n_fft = number of samples per frame (needs to be power to 2/ if n_fft > hop length - need to pad)
                //hop_length = sample_rate/frame_rate = 512 = 22050 Hz/43 Hz
                Complex [][] stft = jLibrosa.generateSTFTFeatures(floatAudioChunk, 16000, 40, 256, 1028, 65);
                chunk = audioData.complexTo2DFloat(stft);
                // if (showPreprocessLogs == true) display.matrix(spectrogram);
                // Log.d(LOG_TAG, "1. Mel Bin " + spectrogram.length);
                // Log.d(LOG_TAG, "2. Frames " + spectrogram[0].length);
                break;

            case "mfcc":
                //generateMFCCFeatures(float[] magValues, int mSampleRate, int nMFCC, int n_fft, int n_mels, int hop_length)
                //generateMFCCFeatures(float[] magValues, int mSampleRate, int nMFCC)
                //nMfcc - affects first shape
                //hop_length - affects second shape
                chunk = jLibrosa.generateMFCCFeatures(floatAudioChunk, 16000, 40, 256, 128, 8192);
                //if (showPreprocessLogs == true) display.matrix(mfcc);
                //Log.d(LOG_TAG, "1. Shape1 " + mfcc.length);
                // Log.d(LOG_TAG, "2. Shape2 " + mfcc[0].length);
                break;

            default:
                throw new AssertionError("Input type: " + inputType + " is not a spectrogram. Skipping"); 
                // Log.d(LOG_TAG, "Input type: " + inputType + " is not a spectrogram. Skipping");
                // break;
            
        }

        return chunk;

    }


    public String getState (int i){
        if ((i + 1) % inputSize == 0 && inferenceCount != numOfInferences) { return "processing"; }
        else if (i == fileSize - 1 && inferenceCount == numOfInferences) { return "finalising"; }
        else { return "appending"; }
    }


    public void displayInferenceCount(){
        Log.d(LOG_TAG, "Inference count: " + (inferenceCount) + "/" + numOfInferences);
    }

    public void reset(int i){
        indexCount = 0;
        inferenceCount += 1;
        if(dataType == DataType.SHORT){
            shortAudioChunk = new short[inputSize];
            shortAudioChunk[indexCount] = shortBuffer.get(i);
        }else{
            floatAudioChunk = new float[inputSize];
            floatAudioChunk[indexCount] = floatBuffer.get(i);
        }
    }

    public void appendDataToChunk(int i){
        if(dataType == DataType.SHORT){
            shortAudioChunk[indexCount] = shortBuffer.get(i);
        }else{
            floatAudioChunk[indexCount] = floatBuffer.get(i);
        }
        indexCount += 1;
    }



    //TODO requirePadding - USER CONTROLLED?
    public void padSilenceToChunk(int i){  

        int remainingSamples = inputSize - indexCount;

        if(dataType == DataType.SHORT && requirePadding ){
            Log.d(LOG_TAG, "Missing samples found in short audio chunk..");
            shortAudioChunk = audioData.addSilence(remainingSamples, shortAudioChunk, indexCount);
        }

        else if(dataType == DataType.FLOAT && requirePadding){   
            Log.d(LOG_TAG, "Missing samples found in float audio chunk..");    
            floatAudioChunk = audioData.addSilence(remainingSamples, floatAudioChunk, indexCount);
        }

        else {
            Log.d(LOG_TAG, "Missing samples are less than half of input. Padding not required");
        }


    }
}

