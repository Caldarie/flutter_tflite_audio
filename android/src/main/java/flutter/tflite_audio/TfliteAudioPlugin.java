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
import android.media.AudioFormat; //Toggle this off unless debugging?
import android.media.MediaCodec; //required for extracting raw audio
import android.media.MediaRecorder;
import android.os.Looper;
import android.os.Handler;

import androidx.core.app.ActivityCompat;
import androidx.annotation.NonNull;

import org.tensorflow.lite.Interpreter;

import java.util.concurrent.CompletableFuture; //required to get value from thread
import java.util.concurrent.CountDownLatch;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer; //required for preprocessing
import java.nio.ByteOrder; //required for preprocessing
import java.nio.channels.FileChannel;
import java.nio.MappedByteBuffer;
import java.nio.ShortBuffer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.embedding.engine.plugins.FlutterPlugin;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.view.FlutterMain;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry; //required for onRequestPermissionsResult
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;

public class TfliteAudioPlugin implements MethodCallHandler, StreamHandler, FlutterPlugin, ActivityAware,
        PluginRegistry.RequestPermissionsResultListener {

    // ui elements
    private static final String LOG_TAG = "Tflite_audio";
    private static final int REQUEST_RECORD_AUDIO = 13;
    private static final int REQUEST_READ_EXTERNAL_STORAGE = 1;
    private static TfliteAudioPlugin instance;
    private Handler handler = new Handler(Looper.getMainLooper());

    // debugging
    private DisplayLogs display = new DisplayLogs();
    private boolean showPreprocessLogs = true;
    private boolean showRecordLogs = false;

    // working recording variables
    AudioRecord record;
    boolean shouldContinue = true;
    private Thread recordingThread;
    private final ReentrantLock recordingBufferLock = new ReentrantLock();

    // preprocessing variables
    private Thread preprocessThread;
    private String audioDirectory;
    private boolean isPreprocessing;

    // working label variables
    private List<String> labels;

    // working recognition variables
    boolean lastInferenceRun = false;
    private long lastProcessingTimeMs;
    private Thread recognitionThread;
    private Interpreter tfLite;
    private LabelSmoothing labelSmoothing = null;

    // flutter
    private AssetManager assetManager;
    private Activity activity;
    private Context applicationContext;
    private MethodChannel methodChannel;
    private EventChannel audioRecognitionChannel;
    private EventChannel fileRecognitionChannel;
    private EventSink events;

    // recording variables
    private int bufferSize;
    private int sampleRate;
    private int numOfInferences;

    // model variables
    private int inputSize;
    private int[] inputShape;
    private String inputType;
    private boolean outputRawScores;
    private String modelPath;
    private String labelPath;
    private Object isAssetObj;
    private int numThreads;

    // labelsmoothing variables
    private float detectionThreshold;
    private long averageWindowDuration;
    private long minimumTimeBetweenSamples;
    private int suppressionTime;

    // Used to extract raw audio data
    private MediaCodec mediaCodec;
    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

    private AudioData audioData;

    static Activity getActivity() {
        return instance.activity;
    }

    public TfliteAudioPlugin() {
        instance = this;
    }

    @Override
    public void onAttachedToEngine(FlutterPluginBinding binding) {
        onAttachedToEngine(binding.getApplicationContext(), binding.getBinaryMessenger());
    }

    private void onAttachedToEngine(Context applicationContext, BinaryMessenger messenger) {
        this.applicationContext = applicationContext;
        this.assetManager = applicationContext.getAssets();

        this.methodChannel = new MethodChannel(messenger, "tflite_audio");
        this.methodChannel.setMethodCallHandler(this);

        this.audioRecognitionChannel = new EventChannel(messenger, "AudioRecognitionStream");
        this.audioRecognitionChannel.setStreamHandler(this);

        this.fileRecognitionChannel = new EventChannel(messenger, "FileRecognitionStream");
        this.fileRecognitionChannel.setStreamHandler(this);
    }

    @Override
    public void onDetachedFromEngine(FlutterPluginBinding binding) {
        this.applicationContext = null;
        this.assetManager = null;

        this.methodChannel.setMethodCallHandler(null);
        this.methodChannel = null;

        this.audioRecognitionChannel.setStreamHandler(null);
        this.audioRecognitionChannel = null;

        this.fileRecognitionChannel.setStreamHandler(null);
        this.fileRecognitionChannel = null;
    }

    public void onAttachedToActivity(ActivityPluginBinding binding) {
        this.activity = binding.getActivity();
        binding.addRequestPermissionsResultListener(this);
    }

    // @Override
    public void onDetachedFromActivityForConfigChanges() {
        this.activity = null;
    }

    // @Override
    public void onReattachedToActivityForConfigChanges(ActivityPluginBinding activityPluginBinding) {
        this.activity = activityPluginBinding.getActivity();
        activityPluginBinding.addRequestPermissionsResultListener(this);
    }

    // @Override
    public void onDetachedFromActivity() {
        this.activity = null;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result _result) {
        HashMap arguments = (HashMap) call.arguments;
        Result result = _result;

        switch (call.method) {
            case "loadModel":
                this.numThreads = (int) arguments.get("numThreads");
                this.inputType = (String) arguments.get("inputType");
                this.outputRawScores = (boolean) arguments.get("outputRawScores");
                this.modelPath = (String) arguments.get("model");
                this.labelPath = (String) arguments.get("label");
                this.isAssetObj = arguments.get("isAsset");
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
    public void onListen(Object _arguments, EventSink events) {
        HashMap arguments = (HashMap) _arguments;
        this.events = events;

        Log.d(LOG_TAG, "Parameters: " + arguments);

        // label smoothing variables
        double detectObj = (double) arguments.get("detectionThreshold");
        this.detectionThreshold = (float) detectObj;
        int avgWinObj = (int) arguments.get("averageWindowDuration");
        this.averageWindowDuration = (long) avgWinObj;
        int minTimeObj = (int) arguments.get("minimumTimeBetweenSamples");
        this.minimumTimeBetweenSamples = (long) minTimeObj;
        this.suppressionTime = (int) arguments.get("suppressionTime");

        switch ((String) arguments.get("method")) {
            case "setAudioRecognitionStream":
                this.bufferSize = (int) arguments.get("bufferSize");
                this.sampleRate = (int) arguments.get("sampleRate");
                this.numOfInferences = (int) arguments.get("numOfInferences");
                checkPermissions(REQUEST_RECORD_AUDIO);
                break;
            case "setFileRecognitionStream":
                // Log.d(LOG_TAG, "setting file recognition listener");
                this.audioDirectory = (String) arguments.get("audioDirectory");
                checkPermissions(REQUEST_READ_EXTERNAL_STORAGE);
                break;
            default:
                throw new AssertionError("Error with listening to stream.");
        }

    }

    @Override
    public void onCancel(Object _arguments) {
        this.events = null;
    }

    private void loadModel() throws IOException {
        Log.d(LOG_TAG, "model name is: " + modelPath);
        boolean isAsset = this.isAssetObj == null ? false : (boolean) isAssetObj;
        MappedByteBuffer buffer = null;
        String key = null;
        if (isAsset) {
            key = FlutterMain.getLookupKeyForAsset(modelPath);
            AssetFileDescriptor fileDescriptor = assetManager.openFd(key);
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } else {
            FileInputStream inputStream = new FileInputStream(new File(modelPath));
            FileChannel fileChannel = inputStream.getChannel();
            long declaredLength = fileChannel.size();
            buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, declaredLength);
        }

        final Interpreter.Options tfliteOptions = new Interpreter.Options();
        tfliteOptions.setNumThreads(numThreads);
        this.tfLite = new Interpreter(buffer, tfliteOptions);
        this.inputShape = tfLite.getInputTensor(0).shape(); //TODO - maybe not accurage for multi inputs
        this.inputSize = Arrays.stream(inputShape).max().getAsInt(); // TODO - Not appropriate for MFCC or spectogram
                                                                     // inputs

        Log.v(LOG_TAG, "Input Type: " + inputType);
        Log.v(LOG_TAG, "Input shape: " + Arrays.toString(inputShape));
        Log.v(LOG_TAG, "Input size: " + inputSize);

        // load labels
        Log.d(LOG_TAG, "label name is: " + labelPath);

        if (labelPath.length() > 0) {
            if (isAsset) {
                key = FlutterMain.getLookupKeyForAsset(labelPath);
                loadLabels(assetManager, key);
            } else {
                loadLabels(null, labelPath);
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
            labels = new ArrayList<>(); // resets label input
            while ((line = br.readLine()) != null) {
                labels.add(line);
            }
            Log.d(LOG_TAG, "Labels: " + labels.toString());
            br.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read label file", e);
        }

    }

    private void checkPermissions(int permissionType) {
        Log.d(LOG_TAG, "Check for permission. Request code: " + permissionType);

        PackageManager pm = applicationContext.getPackageManager();

        switch (permissionType) {
            case REQUEST_RECORD_AUDIO:
                int recordPerm = pm.checkPermission(Manifest.permission.RECORD_AUDIO,
                        applicationContext.getPackageName());
                boolean hasRecordPerm = recordPerm == PackageManager.PERMISSION_GRANTED;

                if (hasRecordPerm) {
                    startRecording();
                    Log.d(LOG_TAG, "Permission already granted. start recording");
                } else {
                    requestPermission(REQUEST_RECORD_AUDIO);
                }
                break;

            case REQUEST_READ_EXTERNAL_STORAGE:
                int readPerm = pm.checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE,
                        applicationContext.getPackageName());
                boolean hasReadPerm = readPerm == PackageManager.PERMISSION_GRANTED;
                if (hasReadPerm) {
                    loadAudioFile();
                    Log.d(LOG_TAG, "Permission already granted. Loading audio file..");
                } else {
                    requestPermission(REQUEST_READ_EXTERNAL_STORAGE);
                }
                break;
            default:
                Log.d(LOG_TAG, "Unknown permission type");

        }

    }

    private void requestPermission(int permissionType) {
        Log.d(LOG_TAG, "Permission requested.");
        Activity activity = TfliteAudioPlugin.getActivity();

        switch (permissionType) {
            case REQUEST_RECORD_AUDIO:
                ActivityCompat.requestPermissions(activity,
                        new String[] { android.Manifest.permission.RECORD_AUDIO }, REQUEST_RECORD_AUDIO);
                break;
            case REQUEST_READ_EXTERNAL_STORAGE:
                ActivityCompat.requestPermissions(activity,
                        new String[] { android.Manifest.permission.READ_EXTERNAL_STORAGE },
                        REQUEST_READ_EXTERNAL_STORAGE);
                break;
            default:
                Log.d(LOG_TAG, "Unknown permission type");
        }
    }

    // @Override
    public boolean onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_RECORD_AUDIO:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startRecording();
                    Log.d(LOG_TAG, "Permission granted. Start recording...");
                } else {
                    showRationaleDialog(
                            "Microphone Permissions",
                            "Permission has been declined. Please accept permissions in your settings");
                    if (events != null) {
                        events.endOfStream();
                    }
                }
                break;
            case REQUEST_READ_EXTERNAL_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    loadAudioFile();
                    Log.d(LOG_TAG, "Permission granted. Loading audio file...");
                } else {
                    showRationaleDialog(
                            "Read External Storage Permissions",
                            "Permission has been declined. Please accept permissions in your settings");
                    if (events != null) {
                        events.endOfStream();
                    }
                }
                break;
            default:
                Log.d(LOG_TAG, "Error with request permission results.");
                break;
        }
        // placehold value
        return true;
    }

    public void showRationaleDialog(String title, String message) {

        runOnUIThread(() -> {
            Activity activity = TfliteAudioPlugin.getActivity();
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

    public synchronized void loadAudioFile() {
        if (preprocessThread != null) {
            return;
        }
        shouldContinue = true;
        preprocessThread = new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        preprocessAudioFile();
                    }
                });
        preprocessThread.start();
    }

    private void preprocessAudioFile() {
        Log.d(LOG_TAG, "Preprocessing audio file..");

        this.isPreprocessing = true;
        boolean isAsset = this.isAssetObj == null ? false : (boolean) isAssetObj;
        AssetFileDescriptor fileDescriptor = null;
        long startOffset = 0;
        long declaredLength = 0;

        try {
            if (isAsset) {
                // Get exact location of the file in the asssets folder.
                String key = FlutterMain.getLookupKeyForAsset(audioDirectory);
                fileDescriptor = assetManager.openFd(key);
                FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
                FileChannel fileChannel = inputStream.getChannel();
                startOffset = fileDescriptor.getStartOffset();
                declaredLength = fileDescriptor.getDeclaredLength();
            } else {
                FileInputStream inputStream = new FileInputStream(new File(audioDirectory));
                FileChannel fileChannel = inputStream.getChannel();
                declaredLength = fileChannel.size();
            }

            // Extract raw audio data in byte form then wrap as a
            MediaDecoder decoder = new MediaDecoder(fileDescriptor, startOffset, declaredLength);
            this.sampleRate = decoder.getSampleRate();
            byte[] byteData = {};
            byte[] readData;
            while ((readData = decoder.readByteData()) != null) {
                byteData = appendByteData(readData, byteData);
                Log.d(LOG_TAG, "data chunk length: " + readData.length);
            }

            // Convert byte to short form and prepare the data before feeding the model
            ShortBuffer shortBuffer = ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            int shortDataLength = shortBuffer.limit();
            int numOfInferences = numOfInferences = (int) Math.ceil((float) shortDataLength / inputSize);

            if (showPreprocessLogs == true)
                display.logs("Raw data info", byteData.length, shortDataLength, numOfInferences);

            // Keep track of preprocessing loop
            short[] audioChunk = new short[inputSize];
            int indexCount = 0;
            int inferenceCount = 1;

            // Splice short buffer into several audio chunks for inferencing
            for (int i = 0; i < shortDataLength; i++) {

                if(isPreprocessing == false) break;

                // Inferences that is not final
                if ((i + 1) % inputSize == 0 && inferenceCount != numOfInferences) {
                    Log.d(LOG_TAG, "Inference count: " + (inferenceCount) + "/" + numOfInferences);
                    if (showPreprocessLogs == true)
                        display.logs("Preprocessing - regular inference", i, indexCount, inferenceCount, audioChunk);
                        
                    startRecognition(audioChunk);
                    if(recognitionThread != null) awaitRecognition(); 

                    // need to reset index or out of array error
                    // Do not change the positions below!!!!
                    indexCount = 0;
                    inferenceCount += 1;
                    audioChunk = new short[inputSize];
                    audioChunk[indexCount] = shortBuffer.get(i);

                    // Final inference
                } else if (i == shortDataLength - 1 && inferenceCount == numOfInferences) {
                    Log.d(LOG_TAG, "Inference count: " + (inferenceCount) + "/" + numOfInferences);

                    if (showPreprocessLogs == true)
                        display.logs("Final inference", i, indexCount, inferenceCount, audioChunk);

                    if ((i + 1) % inputSize != 0) {
                        Log.d(LOG_TAG, "Missing samples found in audioChunk..");

                        if (showPreprocessLogs == true)
                            display.logs("preprocessing - before padding", audioChunk, indexCount, inputSize);

                        int remain = audioChunk.length - indexCount;
                        audioChunk = audioData.addSilence(remain, audioChunk, indexCount);

                        // short[] padding = new short[remain];
                        // Random random = new Random();
                        // Log.d(LOG_TAG, "Padding " + remain + " samples to audioChunk..");
                        // for (int x = 0; x < remain; x++) {
                        //     int rand = random.nextInt(10 + 10) - 10;
                        //     short value = (short) rand;
                        //     padding[x] = value;
                        // }
                        // System.arraycopy(padding, 0, audioChunk, indexCount, remain);
                        if (showPreprocessLogs == true)
                            display.logs("preprocessing - after padding", audioChunk, indexCount, inputSize);
                    }
                    
                    lastInferenceRun = true;
                    startRecognition(audioChunk);
                    if(recognitionThread != null) awaitRecognition(); 
           
                    // clears out memmory and threads after preprocessing is done
                    stopPreprocessing();
                    byteData = null;
                    audioChunk = null;
                    indexCount = 0;
                    inferenceCount = 1;

                    // apend elements to buffer until it reaches the limit
                } else {
                    audioChunk[indexCount] = shortBuffer.get(i);
                    indexCount += 1;
                }
            }

        } catch (IOException e) {
            Log.d(LOG_TAG, "Error loading audio file: " + e);
        }

    }

    public synchronized void startRecording() {
        if (recordingThread != null) {
            return;
        }
        shouldContinue = true;
        recordingThread = new Thread(
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

        int inferenceCount = 1;
        int recordingOffset = 0;
        short[] recordingFrame = new short[bufferSize / 2];
        short[] recordingBuffer = new short[inputSize];

        record = new AudioRecord(
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
            // Reads audio data and records it into redcordFrame
            int numberRead = record.read(recordingFrame, 0, recordingFrame.length);
            int recordingOffsetCount = recordingOffset + numberRead;

            recordingBufferLock.lock();
            try {
                // Continue to append frame until it reaches recording length
                // Do not change inferenceCount <= numOfInferences! - counts last inference
                if (inferenceCount <= numOfInferences && recordingOffsetCount < inputSize) {

                    System.arraycopy(recordingFrame, 0, recordingBuffer, recordingOffset, numberRead);
                    recordingOffset += numberRead;
                    Log.v(LOG_TAG, "recordingOffset: " + recordingOffset + "/" + inputSize + " | inferenceCount: "
                            + inferenceCount + "/" + numOfInferences);
                    // Log.v(LOG_TAG, Arrays.toString(recordingBuffer));

                    // Starts recognition when recording bufffer is full. Resest recording buffer
                    // for next inference
                } else if (inferenceCount < numOfInferences && recordingOffsetCount == inputSize) {

                    Log.v(LOG_TAG, "recordingOffset: " + recordingOffsetCount + "/" + inputSize + " | inferenceCount: "
                            + inferenceCount + "/" + numOfInferences);
                    Log.v(LOG_TAG, "Recording reached threshold");
                    System.arraycopy(recordingFrame, 0, recordingBuffer, recordingOffset, numberRead);
                    recordingOffset += numberRead;

                    Log.v(LOG_TAG, "recordingOffset: " + recordingOffset + "/" + inputSize);
                    // recordingBuffer = recordingBuffer;
                    startRecognition(recordingBuffer);
                    inferenceCount += 1;

                    Log.v(LOG_TAG, "Clearing recordingBuffer..");
                    recordingBuffer = new short[inputSize];
                    recordingOffset = 0;

                    // when buffer exeeds max record length, trim and resize the buffer, append, and
                    // then start inference
                    // Resets recording buffer after inference
                } else if (inferenceCount < numOfInferences && recordingOffsetCount > inputSize) {

                    Log.v(LOG_TAG, "recordingOffset: " + recordingOffsetCount + "/" + inputSize + " | inferenceCount: "
                            + inferenceCount + "/" + numOfInferences);
                    Log.v(LOG_TAG, "Recording buffer exceeded maximum threshold");

                    if (showRecordLogs == true)
                        display.logs("Excess - Before trim:", recordingBuffer, recordingOffset);

                    /*
                     * Calculate remaining unread recordingBuffer.
                     * Resize readingBuffer to the remaining then add to recording buffer
                     * For example: 11025,22050,33075 -> 44032-33075 -> 10957 remaining
                     */
                    int remainingRecordingLength = inputSize - recordingOffset;
                    short[] remainingRecordingFrame = new short[remainingRecordingLength];
                    System.arraycopy(recordingFrame, 0, remainingRecordingFrame, 0, remainingRecordingLength);
                    System.arraycopy(remainingRecordingFrame, 0, recordingBuffer, recordingOffset,
                            remainingRecordingLength);
                    recordingOffset += remainingRecordingLength;
                    Log.v(LOG_TAG, "Excess recording has been trimmed. RecordingOffset now at: " + recordingOffset + "/"
                            + inputSize);

                    if (showRecordLogs== true)
                        display.logs("Recording Excess - After trim:", recordingBuffer, recordingOffset);

                    startRecognition(recordingBuffer);
                    inferenceCount += 1;

                    /*
                     * Calculate excess of recordingBufferCount
                     * Trim excess than add to new recordingBuffer
                     * For example: //44100/44032 -> 68 excess @ bufferRate of 22050
                     */
                    Log.v(LOG_TAG, "Clearing recording buffer..");
                    recordingBuffer = new short[inputSize];
                    int excessRecordingLength = recordingOffsetCount - inputSize;
                    short[] excessRecordingFrame = new short[excessRecordingLength];
                    System.arraycopy(recordingFrame, remainingRecordingLength, excessRecordingFrame, 0,
                            excessRecordingLength);
                    System.arraycopy(excessRecordingFrame, 0, recordingBuffer, 0, excessRecordingLength);
                    recordingOffset = 0 + excessRecordingLength;
                    Log.v(LOG_TAG, "Added excess length to new recording buffer. RecordingOffset now at: "
                            + recordingOffset + "/" + inputSize);

                    if (showRecordLogs== true)
                        display.logs("Recording excess - New recording buffer:", remainingRecordingFrame,
                                remainingRecordingLength,
                                excessRecordingFrame, excessRecordingLength);

                    // when count reaches max numOfInferences, stop all inference and recording
                    // no need to count recordingOffset with numberRead as its final
                } else if (inferenceCount == numOfInferences && recordingOffsetCount > inputSize) {

                    Log.v(LOG_TAG, "recordingOffset: " + recordingOffsetCount + "/" + inputSize + " | inferenceCount: "
                            + inferenceCount + "/" + numOfInferences);
                    Log.v(LOG_TAG, "Recording buffer exceeded maximum threshold");
                    int remainingRecordingLength = inputSize - recordingOffset; // 11025,22050,33075 -> 44032-33075 ->
                                                                                // 10957 remaining
                    short[] remainingRecordingFrame = new short[remainingRecordingLength];
                    System.arraycopy(recordingFrame, 0, remainingRecordingFrame, 0, remainingRecordingLength);
                    System.arraycopy(remainingRecordingFrame, 0, recordingBuffer, recordingOffset,
                            remainingRecordingLength);
                    recordingOffset += remainingRecordingLength;
                    Log.v(LOG_TAG, "Excess recording has been trimmed. RecordingOffset now at: " + recordingOffset + "/"
                            + inputSize);
                    Log.v(LOG_TAG, "Unused excess samples: " + (recordingOffsetCount - inputSize));

                    lastInferenceRun = true;
                    startRecognition(recordingBuffer);
                    stopRecording();

                    // reset after recognition and recording. Don't change position!!
                    recordingOffset = 0;
                    inferenceCount = 1;

                    // Final inference. Stops recognitions and recording.
                } else if (inferenceCount == numOfInferences && recordingOffsetCount == inputSize) {
                    Log.v(LOG_TAG, "Reached indicated number of inferences.");

                    System.arraycopy(recordingFrame, 0, recordingBuffer, recordingOffset, numberRead);
                    // recordingBuffer = recordingBuffer;
                    lastInferenceRun = true;

                    startRecognition(recordingBuffer);
                    stopRecording();

                    // reset after recognition and recording. Don't change position!!
                    recordingOffset = 0;
                    inferenceCount = 1;

                } else {
                    Log.v(LOG_TAG, "something weird has happened with recording");
                    lastInferenceRun = true;
                    forceStopRecogniton();

                    if (showRecordLogs == true)
                        display.logs("Recording - strange behaviour:", inferenceCount, numOfInferences,
                                recordingOffset, recordingOffsetCount, inputSize);

                    // reset after recognition and recording. Don't change position!!
                    recordingOffset = 0;
                    inferenceCount = 1;

                }

            } finally {
                recordingBufferLock.unlock();

            }
        }
    }

    public synchronized void startRecognition(short[] audioBuffer) {
        if (recognitionThread != null) {
            return;
        }

        recognitionThread = new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        recognize(audioBuffer);
                    }
                });
        recognitionThread.start();
    }

    private void recognize(short[] audioBuffer) {
        Log.v(LOG_TAG, "Recognition started.");

        if (events == null) {
            return;
            // throw new AssertionError("Events is null. Cannot start recognition");
        }

        // determine rawAudio or decodedWav input
        float[][] floatInputBuffer = {};
        int[] sampleRateList = {};
        float[][] floatOutputBuffer = new float[1][labels.size()];
        short[] inputBuffer = new short[inputSize];

        // Used for multiple input and outputs (decodedWav)
        Object[] inputArray = {};
        Map<Integer, Object> outputMap = new HashMap<>();
        Map<String, Object> finalResults = new HashMap();

        switch (inputType) {
            case "decodedWav":
                // !Debugging - Log.v(LOG_TAG, "InputType: " + inputType);
                floatInputBuffer = new float[inputSize][1];
                sampleRateList = new int[] { sampleRate };
                break;

            case "rawAudio":
                // !Debugging - Log.v(LOG_TAG, "InputType: " + inputType);
                if (inputShape[0] > inputShape[1] && inputShape[1] == 1) {
                    // [inputSize, 1]
                    floatInputBuffer = new float[inputSize][1];

                } else if (inputShape[0] < inputShape[1] && inputShape[0] == 1) {
                    // [1, inputSize]
                    floatInputBuffer = new float[1][inputSize];
                } else {
                    throw new AssertionError(inputType + " is an incorrect input type");
                }
                break;
        }

        recordingBufferLock.lock();
        try {
            int maxLength = audioBuffer.length;
            System.arraycopy(audioBuffer, 0, inputBuffer, 0, maxLength);
            // System.arraycopy(audioBuffer, 0, inputBuffer, 0, inputSize);
        } finally {
            recordingBufferLock.unlock();
        }

        long startTime = new Date().getTime();
        switch (inputType) {
            case "decodedWav":
                // We need to feed in float values between -1.0 and 1.0, so divide the
                // signed 16-bit inputs.
                for (int i = 0; i < inputSize; ++i) {
                    floatInputBuffer[i][0] = inputBuffer[i] / 32767.0f;
                }

                inputArray = new Object[] { floatInputBuffer, sampleRateList };
                outputMap.put(0, floatOutputBuffer);

                tfLite.runForMultipleInputsOutputs(inputArray, outputMap);
                lastProcessingTimeMs = new Date().getTime() - startTime;
                break;

            case "rawAudio":
                // We need to feed in float values between -1.0 and 1.0, so divide the
                // signed 16-bit inputs.
                for (int i = 0; i < inputSize; ++i) {
                    floatInputBuffer[0][i] = inputBuffer[i] / 32767.0f;
                }

                tfLite.run(floatInputBuffer, floatOutputBuffer);
                lastProcessingTimeMs = new Date().getTime() - startTime;
                break;
        }

        // debugging purposes
        Log.v(LOG_TAG, "Raw Scores: " + Arrays.toString(floatOutputBuffer[0]));
        // Log.v(LOG_TAG, Long.toString(lastProcessingTimeMs));

        if (outputRawScores == false) {
            labelSmoothing = new LabelSmoothing(
                    labels,
                    averageWindowDuration,
                    detectionThreshold,
                    suppressionTime,
                    minimumTimeBetweenSamples);

            long currentTime = System.currentTimeMillis();
            final LabelSmoothing.RecognitionResult recognitionResult = labelSmoothing
                    .processLatestResults(floatOutputBuffer[0], currentTime);
            finalResults.put("recognitionResult", recognitionResult.foundCommand);
        } else {
            finalResults.put("recognitionResult", Arrays.toString(floatOutputBuffer[0]));
        }

        finalResults.put("inferenceTime", lastProcessingTimeMs);
        finalResults.put("hasPermission", true);

        getResult(finalResults);
        stopRecognition();
    }

    public void getResult(Map<String, Object> recognitionResult) {
        runOnUIThread(() -> {
            if (events != null) {
                Log.v(LOG_TAG, "result: " + recognitionResult.toString());
                events.success(recognitionResult);
            }
        });
    }

    //Used in preprocesing only
    //prevents async errors - as iteration is too quick
    public void awaitRecognition(){
        try {
            recognitionThread.join();
        } catch (InterruptedException ex) {
            Log.d(LOG_TAG, "Error with recognition thread: " + ex);
        }
    }

    public void stopRecognition() {
        if (recognitionThread == null) {
            Log.d(LOG_TAG, "There is no ongoing recognition. Breaking.");
            return;
        }

        recognitionThread = null;
        Log.d(LOG_TAG, "Recognition stopped.");

        if (lastInferenceRun == true) {
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

        if (recordingThread == null || shouldContinue == false) {
            // Log.d(LOG_TAG, "There is no ongoing recording. Breaking.");
            return;
        }

        shouldContinue = false;

        record.stop();
        record.release();

        recordingThread = null;
        Log.d(LOG_TAG, "Recording stopped.");
    }

    public void stopPreprocessing() {
        if (preprocessThread == null) {
            // Log.d(LOG_TAG, "There is no ongoing preprocessing. Breaking.");
            return;
        }

        isPreprocessing = false;
        preprocessThread = null;
        Log.d(LOG_TAG, "Prepocesing stopped.");
    }

    public void forceStopRecogniton() {
        //!DO NOT CHANGE BELOW - awaits recognition thread or causes async error
        try {
            if (recognitionThread != null) {
                recognitionThread.join();
            }
        } catch (InterruptedException e) {
            throw new AssertionError("Error with force stop: " + e);
        }
        
        stopRecording();
        stopRecognition();
        stopPreprocessing();

        //!DO NOT CHANGE BELOW. stopRecognition() wont pass due to recognitionThread null check
        runOnUIThread(() -> {
            if (events != null) {
                Log.d(LOG_TAG, "Recognition Stream stopped");
                events.endOfStream();
            }
        });
    }

    // TODO - Put the functions below in a seperate class?

    private void runOnUIThread(Runnable runnable) {
        if (Looper.getMainLooper() == Looper.myLooper())
            runnable.run();
        else
            handler.post(runnable);
    }

    // TODO - MERGE THESE TWO FUNCTIONS TOGETHER
    public byte[] appendByteData(byte[] src, byte[] dst) {
        byte[] result = new byte[src.length + dst.length];
        System.arraycopy(src, 0, result, 0, src.length);
        System.arraycopy(dst, 0, result, src.length, dst.length);
        return result;
    }

    public short[] appendShortData(short[] src, short[] dst) {
        short[] result = new short[src.length + dst.length];
        System.arraycopy(src, 0, result, 0, src.length);
        System.arraycopy(dst, 0, result, src.length, dst.length);
        return result;
    }

}
