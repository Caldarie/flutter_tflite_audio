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
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.nio.MappedByteBuffer;
import java.nio.ShortBuffer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

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

//External libraries
//TODO - new class
import org.apache.commons.math3.complex.Complex;
import com.jlibrosa.audio.JLibrosa;

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
    AudioFile audioFile;
    // private boolean isPreprocessing;

    // working label variables
    private List<String> labels;

    // working recognition variables
    boolean lastInference = false;
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
    private Recording recording;

    // input/output variables
    private int inputSize;
    private int outputSize;
    private boolean transposeInput = false;
    private boolean transposeOutput = false;
    private String inputType;
    private boolean outputRawScores;

    // default specrogram variables
    private float inputTime = 1.00f;
    private int nMFCC = 20;
    private int nFFT = 256;
    private int nMels = 128;
    private int hopLength = 512;

    // tflite variables
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
                Log.d(LOG_TAG, "loadModel parameters: " + arguments);
                try {
                    loadModel();
                } catch (Exception e) {
                    result.error("failed to load model", e.getMessage(), e);
                }
                break;
            case "setSpectrogramParameters":
                // this.mSampleRate = (int) arguments.get("mSampleRate");
                double time = (double) arguments.get("inputTime");
                this.inputTime = (float) time;
                this.nMFCC = (int) arguments.get("nMFCC");
                this.nFFT = (int) arguments.get("nFFT");
                this.nMels = (int) arguments.get("nMels");
                this.hopLength = (int) arguments.get("hopLength");
                Log.d(LOG_TAG, "Spectrogram parameters: " + arguments);
                break;
            case "stopAudioRecognition":
                forceStop();
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
                determineInput();
                checkPermissions(REQUEST_RECORD_AUDIO);
                break;
            case "setFileRecognitionStream":
                this.audioDirectory = (String) arguments.get("audioDirectory");
                this.sampleRate = (int) arguments.get("sampleRate");
                determineInput();
                checkPermissions(REQUEST_READ_EXTERNAL_STORAGE);
                break;
            default:
                throw new AssertionError("Error with listening to stream.");
        }

    }

    private void determineInput() {

        this.inputShape = tfLite.getInputTensor(0).shape();
        int [] outputShape = tfLite.getOutputTensor(0).shape();
        
        if (inputType.equals("rawAudio") || inputType.equals("decodedWav")) {
            this.inputSize = Arrays.stream(inputShape).max().getAsInt();
            this.transposeInput = shouldTranspose(inputShape);            
        } else {
            //TODO - add transpose for multiple inputs or more than 2d shape?
            this.inputSize = (int)(sampleRate * inputTime); // calculate how many bytes in 1 second in float array
        }

        this.transposeOutput = shouldTranspose(outputShape);
        this.outputSize = Arrays.stream(outputShape).max().getAsInt();

        Log.v(LOG_TAG, "Input Type: " + inputType);
        Log.v(LOG_TAG, "Input shape: " + Arrays.toString(inputShape));
        Log.v(LOG_TAG, "Require Transpose: " + transposeInput);
        Log.v(LOG_TAG, "Input size: " + inputSize);
        Log.v(LOG_TAG, "Output shape: " + Arrays.toString(outputShape));
        Log.v(LOG_TAG, "Require Transpose: " + transposeOutput);
        Log.v(LOG_TAG, "Input size: " + outputSize);

    }


    private boolean shouldTranspose(int [] inputShape){
        
        if (inputShape[0] > inputShape[1] && inputShape[1] == 1) return true;
        else if (inputShape[0] < inputShape[1] && inputShape[0] == 1) return false;
        else throw new AssertionError("Problem with input shape: " + inputShape);
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
                    Log.d(LOG_TAG, "Permission already granted.");
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
                    Log.d(LOG_TAG, "Permission already granted.");
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

    private void loadAudioFile() {
        Log.d(LOG_TAG, "Loading audio file to buffer");
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

            Log.d(LOG_TAG, "Audio file sucessfully loaded");
            byte[] byteData = extractRawData(fileDescriptor, startOffset, declaredLength);
            startPreprocessing(byteData);

        } catch (IOException e) {
            Log.d(LOG_TAG, "Error loading audio file: " + e);
        }
    }

    private byte[] extractRawData(AssetFileDescriptor fileDescriptor, long startOffset, long declaredLength) {
        Log.d(LOG_TAG, "Extracting byte data from audio file");

        MediaDecoder decoder = new MediaDecoder(fileDescriptor, startOffset, declaredLength);
        AudioData audioData = new AudioData();

        byte[] byteData = {};
        byte[] readData;
        while ((readData = decoder.readByteData()) != null) {
            byteData = audioData.appendByteData(readData, byteData);
            Log.d(LOG_TAG, "data chunk length: " + readData.length);
        }
        Log.d(LOG_TAG, "byte data length: " + byteData.length);
        return byteData;

    }

    public synchronized void startPreprocessing(byte[] byteData) {
        if (preprocessThread != null) {
            return;
        }
        preprocessThread = new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        preprocess(byteData);
                    }
                });
        preprocessThread.start();
    }

    //No noticable performance difference with subscribleOn and observableOn
    public void preprocess(byte[] byteData) {
        Log.d(LOG_TAG, "Preprocessing audio file..");

        audioFile = new AudioFile(byteData, inputType, inputSize);
        audioFile.getObservable()
                .doOnComplete(() -> {
                    stopStream(); 
                    clearPreprocessing();
                })
                .subscribe((audioChunk) -> {
                    startRecognition(audioChunk);
                });
        audioFile.splice();
    }

    public synchronized void startRecording() {
        if (recordingThread != null) {
            return;
        }
        recordingThread = new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        record();
                    }
                });
        recordingThread.start();
    }

    //Some performance difference with subscribeOn and observeON (android only?)
    private void record() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

        recording = new Recording(bufferSize, inputSize, sampleRate, numOfInferences);
        recording.setReentrantLock(recordingBufferLock);
        recording.getObservable()
                .subscribeOn(Schedulers.io()) //run [observable] on background thread
                .observeOn(Schedulers.computation()) //tell [observer] to receive data on computation thread
                .doOnComplete(() -> {
                    stopStream();
                    clearRecording();
                    })
                .subscribe((audioChunk) -> {
                    startRecognition(audioChunk);
                });
         
        recording.splice();
    }

    private void startRecognition(short[] inputBuffer16) {
        Log.v(LOG_TAG, "Recognition started.");

        if (events == null) {
            return;
        }

        SignalProcessing signalProcessing;
        AudioData audioData = new AudioData();

        float [] inputBuffer32; //for spectro
        float [][] inputTensor2D = {}; //for raw audio
        float [][][][] inputTensor4D = {}; // for spectro
        Object [] inputArray = {};

        Map<Integer, Object> outputMap = new HashMap<>();
        float [][] outputTensor = {};
        
        Map<String, Object> finalResults = new HashMap();

        long startTime = new Date().getTime();

        switch (inputType) {

            //TODO - add transponse for spectro

            case "mfcc":
                signalProcessing = new SignalProcessing(sampleRate, nMFCC, nFFT, nMels, hopLength);
                inputBuffer32 = audioData.normalizeBySigned16(inputBuffer16);
                float mfcc [][] = signalProcessing.getMFCC(inputBuffer32);
                // float transposedMfcc [][] = audioData.transpose2D(mfcc);
                
                outputTensor = transposeOutput
                ? new float [outputSize][1]
                : new float [1][outputSize];

                tfLite.run(mfcc, outputTensor);
                break;

            case "melSpectrogram":
                signalProcessing = new SignalProcessing(sampleRate, nMFCC, nFFT, nMels, hopLength);
                inputBuffer32 = audioData.normalizeBySigned16(inputBuffer16);
                float[][] melSpectrogram = signalProcessing.getMelSpectrogram(inputBuffer32);
                inputTensor4D = signalProcessing.reshape2dto4d(melSpectrogram);

                outputTensor = transposeOutput
                ? new float [outputSize][1]
                : new float [1][outputSize];

                tfLite.run(inputTensor4D, outputTensor);
                break;

            case "spectrogram":
                signalProcessing = new SignalProcessing(sampleRate, nMFCC, nFFT, nMels, hopLength);
                inputBuffer32 = audioData.normalizeBySigned16(inputBuffer16);
                float[][] spectrogram = signalProcessing.getSpectrogram(inputBuffer32);
                // float[][] transposedSpectrogram = audioData.transpose2D(spectrogram);
                // float[][][][] inputTensor4d = signalProcessing.reshape2dto4d(transposedSpectrogram);
                inputTensor4D = signalProcessing.reshape2dto4d(spectrogram);

                outputTensor = transposeOutput
                ? new float [outputSize][1]
                : new float [1][outputSize];

                tfLite.run(inputTensor4D, outputTensor);
                break;

            case "decodedWav":

                //TODO - allow user to add their own additional inputs
                //TODO - remove duplicate code by dynamically adding custom inputs
         
                inputTensor2D = transposeInput 
                    ? audioData.normaliseToTranspose2D(inputBuffer16) 
                    : audioData.normaliseTo2D(inputBuffer16);
                
                outputTensor = transposeOutput
                    ? new float [outputSize][1]
                    : new float [1][outputSize];

                int [] sampleRateList = new int[] { sampleRate }; 
                inputArray = new Object[] { inputTensor2D, sampleRateList};

                outputMap.put(0, outputTensor);
                tfLite.runForMultipleInputsOutputs(inputArray, outputMap);
                break;

            case "rawAudio":
     
                inputTensor2D = transposeInput 
                    ? audioData.normaliseToTranspose2D(inputBuffer16) 
                    : audioData.normaliseTo2D(inputBuffer16);

                outputTensor = transposeOutput
                    ? new float [outputSize][1]
                    : new float [1][outputSize];

                tfLite.run(inputTensor2D, outputTensor);
                break;

        }

        lastProcessingTimeMs = new Date().getTime() - startTime;
        Log.v(LOG_TAG, "Raw Scores: " + Arrays.toString(outputTensor[0]));
   
        if (outputRawScores == false) {
            labelSmoothing = new LabelSmoothing(
                    labels,
                    averageWindowDuration,
                    detectionThreshold,
                    suppressionTime,
                    minimumTimeBetweenSamples);

            long currentTime = System.currentTimeMillis();
            final LabelSmoothing.RecognitionResult recognitionResult = labelSmoothing
                    .processLatestResults(outputTensor[0], currentTime);
            finalResults.put("recognitionResult", recognitionResult.foundCommand);
        } else {
            finalResults.put("recognitionResult", Arrays.toString(outputTensor[0]));
        }

        finalResults.put("inferenceTime", lastProcessingTimeMs);
        finalResults.put("hasPermission", true);

        getResult(finalResults);
    }

    public void getResult(Map<String, Object> recognitionResult) {
        runOnUIThread(() -> {
            if (events != null) {
                Log.v(LOG_TAG, "result: " + recognitionResult.toString());
                events.success(recognitionResult);
            }
        });
    }

    public void stopStream() {

        runOnUIThread(() -> {
            if (events != null) {
                Log.d(LOG_TAG, "Recognition Stream stopped");
                events.endOfStream();
            }
        });
    }

    public void forceStop() {
           
        stopRecording();
        stopPreprocessing();
        //no need to have stop stream here, as it is called when observable is onComplete()
    }

    public void stopRecording(){
        if (recordingThread == null) {
            Log.d(LOG_TAG, "There is no ongoing recording. Breaking.");
            return;
        }

        recording.stop();
        clearRecording();
    }

    public void clearRecording() {
        if (recordingThread == null) {
            Log.d(LOG_TAG, "There is no ongoing recording. Breaking.");
            return;
        }

        recording = null;
        recordingThread = null;
        Log.d(LOG_TAG, "Recording stopped.");
    }


    public void stopPreprocessing() {
        if (preprocessThread == null) {
            Log.d(LOG_TAG, "There is no ongoing preprocessing. Breaking.");
            return;
        }

        audioFile.stop();
        clearPreprocessing();
    }

    public void clearPreprocessing() {
        if (preprocessThread == null) {
            Log.d(LOG_TAG, "There is no ongoing preprocessing. Breaking.");
            return;
        }

        audioFile = null;
        preprocessThread = null;
        Log.d(LOG_TAG, "Prepocesing stopped.");
    }

    private void runOnUIThread(Runnable runnable) {
        if (Looper.getMainLooper() == Looper.myLooper())
            runnable.run();
        else
            handler.post(runnable);
    }

}
