package flutter.tflite_audio;

import org.apache.commons.math3.complex.Complex;

import java.util.Arrays;

import com.jlibrosa.audio.JLibrosa;
import com.jlibrosa.audio.process.AudioFeatureExtraction;

import android.util.Log;

/* Spectogram - shape guidelines
    First dimension: [Frequency Bins] = 1+nFFT/2   (note: frequency bins = mel bins)
    Second dimension: [Frame Rate] = sampleRate/hopLength  (note: by default hopLength = winLength)

    For example for input shape [129, 124]
    nFFT = 256
    hopLength = 129 

    First dimenstion: 129 = 1+nFTT/2
    Second dimension: 124 = 16000/hopLength

    https://kinwaicheuk.github.io/nnAudio/v0.1.5/_autosummary/nnAudio.Spectrogram.STFT.html
    https://stackoverflow.com/questions/62584184/understanding-the-shape-of-spectrograms-and-n-mels
    nFFT = number of samples per frame (needs to be power to 2/ if nFFT > hop length - need to pad)
    hopLength = sample_rate/frame_rate = 512 = 22050 Hz/43 Hz
*/

/* MFCC - shape guidelines
    https://stackoverflow.com/questions/56911774/mfcc-feature-extraction-librosa
    first dimension: n_mfcc
    second dimension: (sr * time) / hop length

    example, given a samplerate of 16000/sec, inputshape is [1,40]
    first dimension = 40
    second dimension is (16000 * 1 / 16384) = [1]

    16,384 is hoplength
*/

/* Mel spectrogram
    number of mel bins
    time
    
*/


public class SignalProcessing{
    
    private static final String LOG_TAG = "Signal_Processing";
    private JLibrosa jLibrosa = new JLibrosa();
    private AudioFeatureExtraction featureExtractor = new AudioFeatureExtraction();
    private boolean showPreprocessLogs = true;

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
    };


    public float [][] getMFCC(float [] inputBuffer32){
        float [][] MFCC = jLibrosa.generateMFCCFeatures(inputBuffer32, sampleRate, nMFCC, nFFT, nMels, hopLength);
        if (showPreprocessLogs) displayShape(MFCC);
        return MFCC;
    }

    public float [][] getMelSpectrogram(float [] inputBuffer32){
        float [][] melSpectrogram = jLibrosa.generateMelSpectroGram(inputBuffer32, sampleRate, nFFT, nMels, hopLength);
        if (showPreprocessLogs) displayShape(melSpectrogram);
        return melSpectrogram;
    }
    
    public float[][] getSpectrogram(float [] inputBuffer32){
        featureExtractor.setSampleRate(sampleRate);
		featureExtractor.setN_mfcc(nMFCC);
        featureExtractor.setN_fft(nFFT);
		featureExtractor.setN_mels(nMels);
		featureExtractor.setHop_length(hopLength);

        Complex [][] stft = featureExtractor.extractSTFTFeaturesAsComplexValues(inputBuffer32, true);
        //float [][] spectrogram = getSpectroAbsVal(stft);
        float [][] spectrogram = getFloatABSValue(stft);
        if (showPreprocessLogs) displayShape(spectrogram);
        return spectrogram;
    }

    private float [][] getFloatABSValue(Complex [][] spectro){

        float[][] spectroAbsVal = new float[spectro.length][spectro[0].length];
		
		for(int i=0;i<spectro.length;i++) {
			for(int j=0;j<spectro[0].length;j++) {
				Complex complexVal = spectro[i][j];
				spectroAbsVal[i][j] = (float) complexVal.abs();
			}
		}

        return spectroAbsVal;
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

    private void displayShape(float [][] spectro){
        Log.d(LOG_TAG, "Spectro " + Arrays.toString(spectro[0]));
        Log.d(LOG_TAG, "1. Mel Bin " + spectro.length);
        Log.d(LOG_TAG, "2. Frames " + spectro[0].length);
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

    public float[][] transpose2D(float[][] matrix){
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

    public float[][][][] reshapeTo4D(float [][] spectrogram){

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
    

    public float[][][][] reshapeTo4DAndTranspose(float [][] spectrogram){

        int FRAMES = spectrogram.length;
        int MEL_BINS = spectrogram[0].length;
        float[][][][] inputTensor = new float[1][FRAMES][MEL_BINS][1];
     

        for (int frame = 0; frame < FRAMES; frame++) {
            for (int freq = 0; freq < MEL_BINS; freq++) {
                inputTensor[0][freq][frame][0] = spectrogram[frame][freq];
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


    