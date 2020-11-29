package flutter.tflite_audio;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.AssetFileDescriptor;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.media.AudioRecord;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.Looper;
import android.os.Handler;

import androidx.core.app.ActivityCompat;
import androidx.annotation.NonNull;

import org.tensorflow.lite.Interpreter;

import java.util.concurrent.CompletableFuture; //required to get value from thread
import java.util.concurrent.CountDownLatch;
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
import java.util.Date;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry; //required for onRequestPermissionsResult
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;


public class TfliteAudioPlugin implements MethodCallHandler, StreamHandler, PluginRegistry.RequestPermissionsResultListener {

    //constants that control the behaviour of the recognition code and model settings
    //private static final int recordingLength = 16000;

    // private static final int sampleRate = 16000;
    //private static final int SAMPLE_DURATION_MS = 1000;
    // private static final int recordingLength = (int) (sampleRate * SAMPLE_DURATION_MS / 1000);

    //label smoothing variables
    private static final long AVERAGE_WINDOW_DURATION_MS = 1000;
    private static final float DETECTION_THRESHOLD = 0.50f;
    private static final int SUPPRESSION_MS = 1500;
    private static final int MINIMUM_COUNT = 3;
    private static final long MINIMUM_TIME_BETWEEN_SAMPLES_MS = 30;

    //ui elements
    private static final String LOG_TAG = "Tflite_audio";
    private static final int REQUEST_RECORD_AUDIO = 13;
    private Handler handler = new Handler(Looper.getMainLooper());

    //working recording variables
    AudioRecord record;
    short[] recordingBuffer;
    short[] recordingBufferMax;
    int recordingOffset = 0;
    boolean shouldContinue = true;
    private Thread recordingThread;
    private final ReentrantLock recordingBufferLock = new ReentrantLock();

    //working label variables
    private List<String> labels;

    //working recognition variables
    boolean lastInferenceRun = false;
    private long lastProcessingTimeMs;
    private Thread recognitionThread;
    private Interpreter tfLite;
    private LabelSmoothing labelSmoothing = null;

    //flutter
    private final Registrar registrar;
    private HashMap arguments;
    private Result result;
    private EventSink events;


    //initialises register variable with a constructor
    private TfliteAudioPlugin(Registrar registrar) {
        this.registrar = registrar;
    }

    public static void registerWith(Registrar registrar) {
        TfliteAudioPlugin tfliteAudioPlugin = new TfliteAudioPlugin(registrar);

        final MethodChannel channel = new MethodChannel(registrar.messenger(), "tflite_audio");
        channel.setMethodCallHandler(tfliteAudioPlugin);

        final EventChannel eventChannel = new EventChannel(registrar.messenger(), "startAudioRecognition");
        eventChannel.setStreamHandler(tfliteAudioPlugin);

        registrar.addRequestPermissionsResultListener(tfliteAudioPlugin);
    }


    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        this.arguments = (HashMap) call.arguments;
        this.result = result;
        switch (call.method) {
            case "loadModel":
                Log.d(LOG_TAG, "loadModel");
                try {
                    loadModel();
                } catch (Exception e) {
                    result.error("failed to load model", e.getMessage(), e);
                }
                break;
            case "stopAudioRecognition":
                forceStopRecogniton();
                break;
            default:
                result.notImplemented();
                break;
        }
    }


    @Override
    public void onListen(Object arguments, EventSink events) {
        this.events = events;
        this.arguments = (HashMap) arguments;
        checkPermissions();
    }

    @Override
    public void onCancel(Object arguments) {
        this.events = null;
    }


    private void loadModel() throws IOException {
        String model = arguments.get("model").toString();
        Log.d(LOG_TAG, "model name is: " + model);
        Object isAssetObj = arguments.get("isAsset");
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

        int numThreads = (int) arguments.get("numThreads");
        final Interpreter.Options tfliteOptions = new Interpreter.Options();
        tfliteOptions.setNumThreads(numThreads);
        tfLite = new Interpreter(buffer, tfliteOptions);

        //load labels
        String labels = arguments.get("label").toString();
        Log.d(LOG_TAG, "label name is: " + labels);

        if (labels.length() > 0) {
            if (isAsset) {
                key = registrar.lookupKeyForAsset(labels);
                loadLabels(assetManager, key);
            } else {
                loadLabels(null, labels);
            }
        }
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


    private void checkPermissions() {
        //int hasStoragePerm = pm.checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, context.getPackageName());
        //        boolean hasPermissions = hasStoragePerm == PackageManager.PERMISSION_GRANTED
//                && hasRecordPerm == PackageManager.PERMISSION_GRANTED;
        Log.d(LOG_TAG, "Check for permissions");
        Context context = registrar.context();
        PackageManager pm = context.getPackageManager();
        int hasRecordPerm = pm.checkPermission(Manifest.permission.RECORD_AUDIO, context.getPackageName());
        boolean hasPermissions = hasRecordPerm == PackageManager.PERMISSION_GRANTED;
        if (hasPermissions) {
            startRecording();
            Log.d(LOG_TAG, "Permission already granted. start recording");
        } else {
            requestMicrophonePermission();
        }
    }

    private void requestMicrophonePermission() {
        Log.d(LOG_TAG, "Permission requested.");
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
            startRecording();
            Log.d(LOG_TAG, "Permission granted. Start recording...");
        } else {
            showRationaleDialog(
                    "Microphone Permissions",
                    "Permission has been declined. Please accept permissions in your settings"
            );
            //return false for hasPermission
            Map<String, Object> finalResults = new HashMap();
            // finalResults.put("recognitionResult", null);
            // finalResults.put("inferenceTime", 0);
            finalResults.put("hasPermission", false);
            if (events != null) {
                events.success(finalResults);
                events.endOfStream();
            }
        }
        return true;
    }


    public void showRationaleDialog(String title, String message) {

        runOnUIThread(() -> {
            Activity activity = registrar.activity();
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(title);
            builder.setMessage(message);
            builder.setPositiveButton(
                    "Settings",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + activity.getPackageName()));
                            intent.addCategory(Intent.CATEGORY_DEFAULT);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            activity.startActivity(intent);
                        }
                    });
            builder.setNegativeButton(
                    "Cancel",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
        });

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

        final int bufferSize = (int) arguments.get("bufferSize");
        final int sampleRate = (int) arguments.get("sampleRate");
        final int recordingLength = (int) arguments.get("recordingLength");
        final int numOfInferences = (int) arguments.get("numOfInferences");

        int maxRecordingLength = recordingLength * numOfInferences;
        short[] recordingFrameBuffer = new short[bufferSize / 2];

        //Used to keep count 
        recordingBuffer = new short[recordingLength]; //16000
        recordingBufferMax = new short[maxRecordingLength]; //32000
        int preRecordingCount = 0;
        int recordingCount = recordingLength;

        // Estimate the buffer size we'll need for this device.
        // int bufferSize =
        //         AudioRecord.getMinBufferSize(
        //                 sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        // if (bufferSize == AudioRecord.ERROR || bufferSize == Audi oRecord.ERROR_BAD_VALUE) {
        //     bufferSize = sampleRate * 2;
        // }
        // Log.v(LOG_TAG, "Buffer size: " + bufferSize);

        record =
                new AudioRecord(
                        MediaRecorder.AudioSource.DEFAULT,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(LOG_TAG, "Audio Record can't initialize!");
            return;
        }

        record.startRecording();

        Log.v(LOG_TAG, "Recording started");


        while (shouldContinue) {
            //numberRead = represents the length of RecordingFrameBuffer (1280)
            int numberRead = record.read(recordingFrameBuffer, 0, recordingFrameBuffer.length);
            recordingBufferLock.lock();
            try {
                //Appends recordingFrameBuffer (length: 1280) to recordingBuffer (length: 16000)
                System.arraycopy(recordingFrameBuffer, 0, recordingBufferMax, recordingOffset, numberRead);
                //Used tp keep count
                recordingOffset += numberRead;
                Log.v(LOG_TAG, "recordingOffset: " + recordingOffset + "/" + maxRecordingLength);
                //If 
                if (recordingOffset + numberRead >= recordingCount) {
                    Log.v(LOG_TAG, "Exceeded threshold");
                    //inputs the first array
                    System.arraycopy(recordingBufferMax, preRecordingCount, recordingBuffer, 0, recordingLength);
                    startRecognition();
                    if (recordingOffset + numberRead >= maxRecordingLength) {
                        Log.v(LOG_TAG, "Stop recording..");
                        stopRecording();
                        //set variable for stream to close, after recognition is finished.
                        lastInferenceRun = true;
                    } else {
                        Log.v(LOG_TAG, "looping...");
                        recordingCount += recordingLength;
                        preRecordingCount += recordingLength;
                    }
                }
            } finally {
                recordingBufferLock.unlock();

            }
        }
        // stopRecording();
        // startRecognition();
    }

    public synchronized void startRecognition() {
        if (recognitionThread != null) {
            return;
        }
        // shouldContinueRecognition = true;

        recognitionThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                String inputType = (String) arguments.get("inputType");
                                Log.v(LOG_TAG, "inputType: " + inputType);
                                switch (inputType) {
                                    case "decodedWav": 
                                        decodedWaveRecognize();
                                        break;
                                    case "single":
                                        singleInputRecognize();
                                        break;
                                }
                           
                            }
                        });
        recognitionThread.start();
    }


    private void singleInputRecognize() {
        Log.v(LOG_TAG, "Recognition started.");

        int sampleRate = (int) arguments.get("sampleRate");
        int recordingLength = (int) arguments.get("recordingLength");

        short[] inputBuffer = new short[recordingLength];
        float[][] floatInputBuffer = new float[1][recordingLength];
        float[][] floatOutputBuffer = new float[1][labels.size()];
        int[] sampleRateList = new int[]{sampleRate};


        recordingBufferLock.lock();
        try {
            int maxLength = recordingBuffer.length;
            System.arraycopy(recordingBuffer, 0, inputBuffer, 0, maxLength);
        } finally {
            recordingBufferLock.unlock();
        }

        // We need to feed in float values between -1.0 and 1.0, so divide the
        // signed 16-bit inputs.
        for (int i = 0; i < recordingLength; ++i) {
            floatInputBuffer[0][i] = inputBuffer[i] / 32767.0f;
        }

        // Calculate inference time
        long startTime = new Date().getTime();
        tfLite.run(floatInputBuffer, floatOutputBuffer);
        lastProcessingTimeMs = new Date().getTime() - startTime;

        // debugging purposes
        // Log.v(LOG_TAG, "OUTPUT======> " + Arrays.toString(floatOutputBuffer[0]));
        // Log.v(LOG_TAG, Long.toString(lastProcessingTimeMs));

        labelSmoothing =
                new LabelSmoothing(
                        labels,
                        AVERAGE_WINDOW_DURATION_MS,
                        DETECTION_THRESHOLD,
                        SUPPRESSION_MS,
                        MINIMUM_COUNT,
                        MINIMUM_TIME_BETWEEN_SAMPLES_MS);

        long currentTime = System.currentTimeMillis();
        final LabelSmoothing.RecognitionResult recognitionResult =
                labelSmoothing.processLatestResults(floatOutputBuffer[0], currentTime);

        //Map score and inference time
        Map<String, Object> finalResults = new HashMap();
        finalResults.put("recognitionResult", recognitionResult.foundCommand);
        finalResults.put("inferenceTime", lastProcessingTimeMs);
        finalResults.put("hasPermission", true);

        getResult(finalResults);
        stopRecognition();
    }


 
    private void decodedWaveRecognize() {
        Log.v(LOG_TAG, "Recognition started.");

        int sampleRate = (int) arguments.get("sampleRate");
        int recordingLength = (int) arguments.get("recordingLength");

        short[] inputBuffer = new short[recordingLength];
        float[][] floatInputBuffer = new float[recordingLength][1];
        float[][] outputScores = new float[1][labels.size()];
        int[] sampleRateList = new int[]{sampleRate};


        recordingBufferLock.lock();
        try {
            int maxLength = recordingBuffer.length;
            System.arraycopy(recordingBuffer, 0, inputBuffer, 0, maxLength);
        } finally {
            recordingBufferLock.unlock();
        }

        // We need to feed in float values between -1.0 and 1.0, so divide the
        // signed 16-bit inputs.
        for (int i = 0; i < recordingLength; ++i) {
            floatInputBuffer[i][0] = inputBuffer[i] / 32767.0f;
        }

        //Create the input and output tensors to feed the model
        Object[] inputArray = {floatInputBuffer, sampleRateList};
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, outputScores);

        // Calculate inference time
        long startTime = new Date().getTime();
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);
        lastProcessingTimeMs = new Date().getTime() - startTime;

        //debugging purposes
        // Log.v(LOG_TAG, "OUTPUT======> " + Arrays.toString(outputScores[0]));
        // Log.v(LOG_TAG, Long.toString(lastProcessingTimeMs));

        labelSmoothing =
                new LabelSmoothing(
                        labels,
                        AVERAGE_WINDOW_DURATION_MS,
                        DETECTION_THRESHOLD,
                        SUPPRESSION_MS,
                        MINIMUM_COUNT,
                        MINIMUM_TIME_BETWEEN_SAMPLES_MS);

        long currentTime = System.currentTimeMillis();
        final LabelSmoothing.RecognitionResult recognitionResult =
                labelSmoothing.processLatestResults(outputScores[0], currentTime);

        //Map score and inference time
        Map<String, Object> finalResults = new HashMap();
        finalResults.put("recognitionResult", recognitionResult.foundCommand);
        finalResults.put("inferenceTime", lastProcessingTimeMs);
        finalResults.put("hasPermission", true);

        getResult(finalResults);
        stopRecognition();
    }

    //passes map to from platform to flutter.
    public void getResult(Map<String, Object> recognitionResult) {

        //passing data from platform to flutter requires ui thread
        runOnUIThread(() -> {
            if (events != null) {
                Log.v(LOG_TAG, "result: " + recognitionResult.toString());
                events.success(recognitionResult);
            }
        });
    }



    public void stopRecognition() {
        // if recognitThread hasn't been called. The function will break
        if (recognitionThread == null) {
            return;
        }

        Log.d(LOG_TAG, "Recognition stopped.");
        recognitionThread = null;

        //If last inference run is true, will close stream
        if (lastInferenceRun == true) {
            //passing data from platform to flutter requires ui thread
            runOnUIThread(() -> {
                if (events != null) {
                    Log.d(LOG_TAG, "Recognition Stream stopped");
                    events.endOfStream();
                }
            });
            lastInferenceRun = false;
        }
    }

    public void stopRecording() {
        shouldContinue = false;

        if (recordingThread == null) {
            return;
        }

        record.stop();
        record.release();

        recordingOffset = 0; //reset recordingOffset
        recordingThread = null;//closes recording
        Log.d(LOG_TAG, "Recording stopped.");
    }

    public void forceStopRecogniton() {

        stopRecording();
        stopRecognition();

        //passing data from platform to flutter requires ui thread
        runOnUIThread(() -> {
            if (events != null) {
                Log.d(LOG_TAG, "Recognition Stream stopped");
                events.endOfStream();
            }
        });
    }


    private void runOnUIThread(Runnable runnable) {
        if (Looper.getMainLooper() == Looper.myLooper())
            runnable.run();
        else
            handler.post(runnable);
    }

}


