package audioprocessing.audio_processing;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.AssetFileDescriptor;

//import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.util.Log;
import android.media.AudioRecord;
import android.media.AudioFormat;
import android.media.MediaRecorder;

import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;

import org.tensorflow.lite.Interpreter;

import audioprocessing.audio_processing.mfcc.MFCC;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry; //required for onRequestPermissionsResult
import io.flutter.plugin.common.PluginRegistry.Registrar;


/**
 * AudioProcessingPlugin
 */
public class AudioProcessingPlugin implements MethodCallHandler, PluginRegistry.RequestPermissionsResultListener {

    //constants that control the behaviour of the recognition code and model settings
    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_DURATION_MS = 1000;
    private static final int RECORDING_LENGTH = (int) (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000);

    //label smoothing variables
    private static final long AVERAGE_WINDOW_DURATION_MS = 1000;
    private static final float DETECTION_THRESHOLD = 0.50f;
    private static final int SUPPRESSION_MS = 1500;
    private static final int MINIMUM_COUNT = 3;
    private static final long MINIMUM_TIME_BETWEEN_SAMPLES_MS = 30;

    //ui elements
    private static final String LOG_TAG = "AudioProcessing";
    private static final int REQUEST_RECORD_AUDIO = 13;

    //working recording variables
    short[] recordingBuffer = new short[RECORDING_LENGTH];
    int recordingOffset = 0;
    boolean shouldContinue = true;
    private Thread recordingThread;
    private final Registrar registrar;
    private final ReentrantLock recordingBufferLock = new ReentrantLock();

    //working label variables
    private List<String> labels;

    //working recognition variables
    boolean shouldContinueRecognition = true;
    private Thread recognitionThread;
    private Interpreter tfLite;
    private LabelSmoothing labelSmoothing = null;
    //private TensorFlowInferenceInterface inferenceInterface;


    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "audio_processing");
        AudioProcessingPlugin audioProcessingPlugin = new AudioProcessingPlugin(registrar);
        channel.setMethodCallHandler(audioProcessingPlugin);
        registrar.addRequestPermissionsResultListener(audioProcessingPlugin);
    }

    //initialises register variable with a constructor
    private AudioProcessingPlugin(Registrar registrar) {
        this.registrar = registrar;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case "loadModel":
                Log.d(LOG_TAG, "loadModel");
                try {
                    String res = loadModel((HashMap) call.arguments);
                    result.success(res);
                    //result.success(null);
                } catch (Exception e) {
                    result.error("failed to load model", e.getMessage(), e);
                }
                break;
            case "hasPermissions":
                Log.d(LOG_TAG, "Check for permissions");
                result.success(hasPermissions());
                break;
            case "startRecording":
                Log.d(LOG_TAG, "startRecording");
                startRecording();
                result.success(null);
                break;
            case "requestPermissions":
                Log.d(LOG_TAG, "requestPermission");
                requestMicrophonePermission();
                result.success(null);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private String loadModel(HashMap args) throws IOException {
        String model = args.get("model").toString();
        Log.d(LOG_TAG, "model name is: " + model);
        Object isAssetObj = args.get("isAsset");
        boolean isAsset = isAssetObj == null ? false : (boolean) isAssetObj;
        MappedByteBuffer buffer = null;
        String key = null;
        AssetManager assetManager = null;
        if (isAsset) {
            assetManager = registrar.context().getAssets();
            key = registrar.lookupKeyForAsset(model);
            AssetFileDescriptor fileDescriptor = assetManager.openFd(key);
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } else {
            FileInputStream inputStream = new FileInputStream(new File(model));
            FileChannel fileChannel = inputStream.getChannel();
            long declaredLength = fileChannel.size();
            buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, declaredLength);
        }

        int numThreads = (int) args.get("numThreads");
        final Interpreter.Options tfliteOptions = new Interpreter.Options();
        tfliteOptions.setNumThreads(numThreads);
        tfLite = new Interpreter(buffer, tfliteOptions);

        //load labels
        String labels = args.get("label").toString();
        Log.d(LOG_TAG, "label name is: " + labels);

        if (labels.length() > 0) {
            if (isAsset) {
                key = registrar.lookupKeyForAsset(labels);
                loadLabels(assetManager, key);
            } else {
                loadLabels(null, labels);
            }
        }

        return "success";
    }

   private void loadLabels(AssetManager assetManager, String path) {
    BufferedReader br;
    try {
      if (assetManager != null) {
        br = new BufferedReader(new InputStreamReader(assetManager.open(path)));
      } else {
        br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(path))));
      }
      String line;
      labels = new ArrayList<>(); //resets label input
      while ((line = br.readLine()) != null) {
        labels.add(line);
      }
      br.close();
    } catch (IOException e) {
      throw new RuntimeException("Failed to read label file", e);
    }
  }


    private void requestMicrophonePermission() {
        Activity activity = registrar.activity();
        ActivityCompat.requestPermissions(activity,
                new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
    }

    @Override
    public boolean onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        //if request is cancelled, result arrays will be empty
        if (requestCode == REQUEST_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(LOG_TAG, "Permission granted.");
//            tfLite.resizeInput(0, new int[] {RECORDING_LENGTH, 1});
//            tfLite.resizeInput(1, new int[] {1});
            startRecording();
        }

        return true;
    }

    private boolean hasPermissions() {
        Context context = registrar.context();
        PackageManager pm = context.getPackageManager();
        //int hasStoragePerm = pm.checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, context.getPackageName());
        int hasRecordPerm = pm.checkPermission(Manifest.permission.RECORD_AUDIO, context.getPackageName());
//        boolean hasPermissions = hasStoragePerm == PackageManager.PERMISSION_GRANTED
//                && hasRecordPerm == PackageManager.PERMISSION_GRANTED;
        boolean hasPermissions = hasRecordPerm == PackageManager.PERMISSION_GRANTED;
        return hasPermissions;
    }

    public synchronized void startRecording() {
        if (recordingThread != null) {
            return;
        }
        shouldContinue = true;
        recordingThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                record();
                            }
                        });
        recordingThread.start();
    }

    private void record() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

        // Estimate the buffer size we'll need for this device.
        int bufferSize =
                AudioRecord.getMinBufferSize(
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2;
        }
        short[] audioBuffer = new short[bufferSize / 2];

        AudioRecord record =
                new AudioRecord(
                        MediaRecorder.AudioSource.DEFAULT,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(LOG_TAG, "Audio Record can't initialize!");
            return;
        }

        record.startRecording();

        Log.v(LOG_TAG, "Recording started....");


        while (shouldContinue) {
            int numberRead = record.read(audioBuffer, 0, audioBuffer.length);
            int maxLength = recordingBuffer.length;
            Log.v(LOG_TAG, "read: " + numberRead);
            recordingBufferLock.lock();
            try {
                if (recordingOffset + numberRead < maxLength) {
                    System.arraycopy(audioBuffer, 0, recordingBuffer, recordingOffset, numberRead);
                } else {
                    shouldContinue = false;
                }
                recordingOffset += numberRead;
                Log.v(LOG_TAG, "recordingOffset: " + recordingOffset + "/" + maxLength);
            } finally {
                recordingBufferLock.unlock();

            }
        }
        record.stop();
        record.release();
        stopRecording();
        startRecognition();
    }

    public synchronized void startRecognition() {
        if (recognitionThread != null) {
            return;
        }
        shouldContinueRecognition = true;
        recognitionThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                recognize();
                            }
                        });
        recognitionThread.start();
    }

    private void recognize() {
        Log.v(LOG_TAG, "Start recognition");

        short[] inputBuffer = new short[RECORDING_LENGTH];
        float[][] floatInputBuffer = new float[RECORDING_LENGTH][1];
        float[][] outputScores = new float[1][labels.size()];
        int[] sampleRateList = new int[]{SAMPLE_RATE};

        recordingBufferLock.lock();
        try {
            int maxLength = recordingBuffer.length;
            System.arraycopy(recordingBuffer, 0, inputBuffer, 0, maxLength);
        } finally {
            recordingBufferLock.unlock();
        }

        // We need to feed in float values between -1.0 and 1.0, so divide the
        // signed 16-bit inputs.
        for (int i = 0; i < RECORDING_LENGTH; ++i) {
            floatInputBuffer[i][0] = inputBuffer[i] / 32767.0f;
        }

        //MFCC java library.
//        MFCC mfccConvert = new MFCC();
//        float[] mfccInput = mfccConvert.process(doubleInputBuffer);
//        Log.v(LOG_TAG, "MFCC Input======> " + Arrays.toString(mfccInput));


        //Create the input and output tensors to feed the model
        Object[] inputArray = {floatInputBuffer, sampleRateList};
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, outputScores);

        // Run the model.
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);
        Log.v(LOG_TAG, "OUTPUT======> " + Arrays.toString(outputScores[0]));
        Log.v(LOG_TAG, "Output scores length " + outputScores.toString());

        long currentTime = System.currentTimeMillis();

        labelSmoothing =
                new LabelSmoothing(
                        labels,
                        AVERAGE_WINDOW_DURATION_MS,
                        DETECTION_THRESHOLD,
                        SUPPRESSION_MS,
                        MINIMUM_COUNT,
                        MINIMUM_TIME_BETWEEN_SAMPLES_MS);
        
        final LabelSmoothing.RecognitionResult result =
                labelSmoothing.processLatestResults(outputScores[0], currentTime);

        Log.d(LOG_TAG, "final result:" + result.foundCommand);
        
        stopRecognition();
    }

    public void stopRecognition() {
        if (recognitionThread == null) {
            return;
        }
        recognitionThread = null;
        Log.d(LOG_TAG, "Recognition stopped.");
    }

    public void stopRecording() {
        if (recordingThread == null) {
            return;
        }
        recordingOffset = 0; //reset recordingOffset
        recordingThread = null;//closes recording
        Log.d(LOG_TAG, "Recording stopped.");
    }

//    public void resetInput(){
//        tfLite.resizeInput(0, new int[] {RECORDING_LENGTH, 1});
//        tfLite.resizeInput(1, new int[] {1});
//    }
}


