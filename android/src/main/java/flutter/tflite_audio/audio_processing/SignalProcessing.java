package flutter.tflite_audio;

import org.apache.commons.math3.complex.Complex;

import java.util.Arrays;

import com.jlibrosa.audio.JLibrosa;
import com.jlibrosa.audio.process.AudioFeatureExtraction;

import android.util.Log;


public class SignalProcessing{
    
    private static final String LOG_TAG = "Signal_Processing";
    private JLibrosa jLibrosa = new JLibrosa();
    private AudioFeatureExtraction featureExtractor = new AudioFeatureExtraction();

    private int sampleRate;
    private int nMFCC;
    private int nFFT;
    private int nMels;
    private int hopLength;
   
    public SignalProcessing(int sampleRate, int nMFCC, int nFFT, int nMels, int hopLength){
        this.sampleRate = sampleRate;
        this.nMFCC = nMFCC;
        this.nFFT = nFFT;
        this.nMels = nMels;
        this.hopLength = hopLength;

        featureExtractor.setSampleRate(sampleRate);
		featureExtractor.setN_mfcc(nMFCC);
        featureExtractor.setN_fft(nFFT);
		featureExtractor.setN_mels(nMels);
		featureExtractor.setHop_length(hopLength);
    };


    // public float [][] getMFCC(float [] inputBuffer32){
    //     //generateMFCCFeatures(float[] magValues, int sampleRate, int nMFCC, int nFFT, int nMels, int hopLength)
    //     //generateMFCCFeatures(float[] magValues, int sampleRate, int nMFCC)
    //     //nMfcc - affects first shape
    //     //hopLength - affects second shape
    //     return jLibrosa.generateMFCCFeatures(inputBuffer32, sampleRate, nMFCC, nFFT, nMels, hopLength);
    //     //if (showPreprocessLogs == true) display.matrix(mfcc);
    //     //Log.d(LOG_TAG, "1. Shape1 " + mfcc.length);
    //     // Log.d(LOG_TAG, "2. Shape2 " + mfcc[0].length);
    // }
    
    public float[][] getSpectrogram(float [] inputBuffer32){

        // double [][] stft = featureExtractor.extractSTFTFeatures(inputBuffer32);
        // double [][] spectro = featureExtractor.powerToDb(featureExtractor.melSpectrogram(inputBuffer32));
        // double [][] melspectro = 
        // float [][] spectrogram = doubleTo2dFloat(featureExtractor.melSpectrogram(inputBuffer32));

        Complex [][] stft = featureExtractor.extractSTFTFeaturesAsComplexValues(inputBuffer32, true);
        // float [][] spectrogram = getSpectroAbsVal(stft);
        float [][] spectrogram = complexTo2DFloat(stft);
        // float [][] spectrogram = doubleTo2dFloat(stftAbsVal); 
        

        // float [][] spectrogram = jLibrosa.generateMelSpectroGram(inputBuffer32, sampleRate, nFFT, nMels, hopLength);
        Log.d(LOG_TAG, "Spectrogram " + Arrays.toString(spectrogram[0]));
        Log.d(LOG_TAG, "1. Mel Bin " + spectrogram.length);
        Log.d(LOG_TAG, "2. Frames " + spectrogram[0].length);

        //generateSTFTFeatures(float[] magValues, int sampleRate, int nMFCC, int nFFT, int nMels, int hopLength)
        //nFFT - affects first shape

        // Complex [][] stft = jLibrosa.generateSTFTFeatures(inputBuffer32, sampleRate, nMFCC, nFFT, nMels, hopLength);
        // // Complex [][] stft = jLibrosa.generateSTFTFeaturesWithPadOption(inputBuffer32, 16000, 40, 256, 130, 130, false);
        // float[][] spectrogram = new float[stft.length][stft[0].length];

        // for(int i=0;i<stft.length;i++) {
        //     for(int j=0;j<stft[0].length;j++) {
        //         Complex complexVal = stft[i][j];
        //         double spectroDblVal = Math.sqrt((Math.pow(complexVal.getReal(), 2) + Math.pow(complexVal.getImaginary(), 2)));
        //         spectrogram[i][j] = (float) Math.pow(spectroDblVal,2);
        //     }
        // }

        // // Log.d(LOG_TAG, "Spectrogram: " + Arrays.toString(spectrogram[0]));



        return spectrogram;
    }

    private float [][] getSpectroAbsVal(Complex [][] spectro){

        float[][] spectroAbsVal = new float[spectro.length][spectro[0].length];
		
		for(int i=0;i<spectro.length;i++) {
			for(int j=0;j<spectro[0].length;j++) {
				Complex complexVal = spectro[i][j];
				float spectroDblVal = (float) Math.sqrt((Math.pow(complexVal.getReal(), 2) + Math.pow(complexVal.getImaginary(), 2)));
				spectroAbsVal[i][j] = (float) Math.pow(spectroDblVal,2);
			}
		}

        return spectroAbsVal;
    }


    private float[][] doubleTo2dFloat(double[][] doubleInput) {

        int height = doubleInput.length;
        int width = doubleInput[0].length;

        float[][] floatOutput = new float[height][width];
        for (int h = 0; h < height; h++) {
            for (int w = 0; w < width; w++) {
                floatOutput[h][w] = (float) doubleInput[h][w];
            }
        }
        return floatOutput;
    }

    public float[][][][] reshape2dto4d(float [][] spectrogram){

        int FRAMES = spectrogram.length;
        int MEL_BINS = spectrogram[0].length;
        float[][][][] inputTensor = new float[1][FRAMES][MEL_BINS][1];
     

        for (int frame = 0; frame < FRAMES; frame++) {
            for (int freq = 0; freq < MEL_BINS; freq++) {
                inputTensor[0][frame][freq][0] = spectrogram[frame][freq];
            }
        }

        return inputTensor;
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
            // f[index] = padNanValues(value);
            f[index] = value;
            index++;
        }
        return f;
    }

    // public float padNanValues(float value){
    //     // Random random = new Random();
    //     float zeroValue = 0.0f;
    //     // float randF = random.nextFloat() * (0.01f - 0.001f) + 0.001f; //only in absolute values
    //     return Float.isFinite(value) ? value : zeroValue;
    // }

}
//    public float[][] getFloatChunk(){

//         JLibrosa jLibrosa = new JLibrosa();
//         float [][] chunk;

//         switch(inputType){

//             case "melSpectrogram":
//                 // generateMelSpectroGram(float[] yValues, int sampleRate, int nFFT, int nMels, int hopLength)
//                 chunk = jLibrosa.generateMelSpectroGram(floatAudioChunk, 16000, 256, 129, 65);
//                 // if (showPreprocessLogs == true) display.matrix(melSpectrogram);
//                 // Log.d(LOG_TAG, "1. Mel Bin " + melSpectrogram.length);
//                 // Log.d(LOG_TAG, "2. Frames " + melSpectrogram[0].length);
//                 break;

//             case "spectrogram":
//                 generateSTFTFeatures(float[] magValues, int sampleRate, int nMFCC, int nFFT, int nMels, int hopLength)
//                 https://stackoverflow.com/questions/62584184/understanding-the-shape-of-spectrograms-and-n-mels
//                 nFFT = number of samples per frame (needs to be power to 2/ if nFFT > hop length - need to pad)
//                 hopLength = sample_rate/frame_rate = 512 = 22050 Hz/43 Hz
//                 frame_rate - is the output of spectrogram [freq, frame]
//                 Complex [][] stft = jLibrosa.generateSTFTFeatures(floatAudioChunk, 16000, 40, 256, 129, 130);
//                 chunk = audioData.complexTo2DFloat(stft);
//                 // if (showPreprocessLogs == true) display.matrix(spectrogram);
//                 Log.d(LOG_TAG, "1. Mel Bin " + chunk.length);
//                 Log.d(LOG_TAG, "2. Frames " + chunk[0].length);
//                 break;

//             case "mfcc":
//                 //generateMFCCFeatures(float[] magValues, int sampleRate, int nMFCC, int nFFT, int nMels, int hopLength)
//                 //generateMFCCFeatures(float[] magValues, int sampleRate, int nMFCC)
//                 //nMfcc - affects first shape
//                 //hopLength - affects second shape
//                 chunk = jLibrosa.generateMFCCFeatures(floatAudioChunk, 16000, 40, 256, 128, 8192);
//                 //if (showPreprocessLogs == true) display.matrix(mfcc);
//                 //Log.d(LOG_TAG, "1. Shape1 " + mfcc.length);
//                 // Log.d(LOG_TAG, "2. Shape2 " + mfcc[0].length);
//                 break;

//             default:
//                 throw new AssertionError("Input type: " + inputType + " is not a spectrogram. Skipping"); 
//                 // Log.d(LOG_TAG, "Input type: " + inputType + " is not a spectrogram. Skipping");
//                 // break;
            
//         }

//         return chunk;

    