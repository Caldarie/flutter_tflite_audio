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
import android.os.Looper;
import android.os.Handler;

import androidx.core.app.ActivityCompat;
import androidx.annotation.NonNull;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.nio.channels.FileChannel;
import java.nio.MappedByteBuffer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.schedulers.Schedulers;

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
    private static final String LOG_TAG = "TfliteAudio";
    private static final int REQUEST_RECORD_AUDIO = 13;
    private static final int REQUEST_READ_EXTERNAL_STORAGE = 1;
    private static TfliteAudioPlugin instance;
    private final Handler handler = new Handler(Looper.getMainLooper()); //required for runOnUI threads

    // working recording variables
    private Thread recordingThread;
    private final ReentrantLock recordingBufferLock = new ReentrantLock();

    // preprocessing variables
    private Thread preprocessThread;
    private String audioDirectory;
    AudioFile audioFile;

    // working label variables
    private List<String> labels;

    private Interpreter tfLite;

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
    private int [] inputShape;
    // private int [] outputShape;
    private int audioLength;
    private boolean transposeAudio;
    private boolean transposeSpectro;
    private String inputType;
    private boolean outputRawScores;

    // default specrogram variables
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
    // private MediaCodec mediaCodec;
    // MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

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
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        HashMap arguments = (HashMap) call.arguments;

        switch (call.method) {
            case "loadModel":
                this.numThreads = (int) arguments.get("numThreads");
                this.inputType = (String) arguments.get("inputType");
                this.outputRawScores = (boolean) arguments.get("outputRawScores");
                this.modelPath = (String) arguments.get("model");
                this.labelPath = (String) arguments.get("label");
                this.isAssetObj = arguments.get("isAsset");
                loadModel();
                Log.d(LOG_TAG, "loadModel parameters: " + arguments);
                break;
            case "setSpectrogramParameters":
                // this.mSampleRate = (int) arguments.get("mSampleRate");
                this.transposeSpectro = (boolean) arguments.get("shouldTranspose");
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
                this.audioLength = determineInput(arguments); 
                this.transposeAudio = determineAudio();
                checkPermissions(REQUEST_RECORD_AUDIO);
                break;
            case "setFileRecognitionStream":
                this.audioDirectory = (String) arguments.get("audioDirectory");
                this.sampleRate = (int) arguments.get("sampleRate");
                this.audioLength = determineInput(arguments);
                this.transposeAudio = determineAudio();
                checkPermissions(REQUEST_READ_EXTERNAL_STORAGE);
                break;
            default:
                throw new AssertionError("Error with listening to stream.");
        }

    }

    private int determineInput(HashMap arguments) {

        int audioLength = (int) arguments.get("audioLength");
        boolean hasValue = audioLength > 0;
        boolean isAudioInput = inputType.equals("rawAudio") || inputType.equals("decodedWav");
        
        if(hasValue){
            Log.d(LOG_TAG, "AudioLength does not need to be adjusted. Length: " + audioLength);
            return audioLength;
        }

        else if(isAudioInput){
            int newAudioLength = Arrays.stream(inputShape).reduce(1, (subtotal, element) -> subtotal * element);
            Log.d(LOG_TAG, "AudioLength has been readjusted. Length: " + newAudioLength);
            return newAudioLength;
        }

        else {
            Log.d(LOG_TAG, "Warning: Unspecified audio length may cause unintended problems with spectro models");
            Log.d(LOG_TAG, "AudioLength: " + sampleRate);
            return sampleRate;
        }
    }


    private boolean determineAudio(){

        boolean isAudioInput = inputType.equals("rawAudio") || inputType.equals("decodedWav");
    
        if(isAudioInput){ 
            //TODO - Assert length is 2, and is not stereo
            //need to have try and catch
            // int shape = Arrays.stream(inputShape).reduce(0, (count, element) -> count + 1); --- DOES NOT WORK
            // if (shape != 1) { throw new AssertionError("Input shape " + inputShape + "is not mono or raw audio."); } 
            //Log.d(LOG_TAG, "count: " + shape);

            boolean result = inputShape[0] > inputShape[1];
            Log.d(LOG_TAG, "Transpose Audio: " + result);

            return result;
        } else {
            Log.d(LOG_TAG, "Input is not audio. Audio does not require to be transposed.");
            return false;
        }
    }


    @Override
    public void onCancel(Object _arguments) {
        this.events = null;
    }

    private void loadModel(){
        Log.d(LOG_TAG, "model name is: " + modelPath);
        boolean isAsset = this.isAssetObj != null && (boolean) isAssetObj;
        MappedByteBuffer buffer;
        String key;

        try {
            if (isAsset) {
                key = FlutterMain.getLookupKeyForAsset(modelPath);
                AssetFileDescriptor fileDescriptor = assetManager.openFd(key);
                FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
                FileChannel fileChannel = inputStream.getChannel();
                long startOffset = fileDescriptor.getStartOffset();
                long declaredLength = fileDescriptor.getDeclaredLength();
                buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
            } else {
                FileInputStream inputStream = new FileInputStream(modelPath);
                FileChannel fileChannel = inputStream.getChannel();
                long declaredLength = fileChannel.size();
                buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, declaredLength);
            }

            final Interpreter.Options tfliteOptions = new Interpreter.Options();
            tfliteOptions.setNumThreads(numThreads);
            this.tfLite = new Interpreter(buffer, tfliteOptions);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load model: ", e);
        }

        this.inputShape = tfLite.getInputTensor(0).shape();
        Log.d(LOG_TAG, "inputShape: " + Arrays.toString(inputShape));
        
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
                br = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
            }
            String line;
            labels = new ArrayList<>(); // resets label input
            while ((line = br.readLine()) != null) {
                labels.add(line);
            }
            Log.d(LOG_TAG, "Labels: " + labels.toString());
            br.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read label file: ", e);
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
                    (dialog, id) -> {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + activity.getPackageName()));
                        intent.addCategory(Intent.CATEGORY_DEFAULT);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.startActivity(intent);
                    });
            builder.setNegativeButton(
                    "Cancel",
                    (dialog, id) -> dialog.cancel());
            AlertDialog alert = builder.create();
            alert.show();
        });

    }

    private void loadAudioFile() {
        Log.d(LOG_TAG, "Loading audio file to buffer");
        boolean isAsset = this.isAssetObj != null && (boolean) isAssetObj;
        AssetFileDescriptor fileDescriptor = null;
        long startOffset = 0;
        long declaredLength;

        try {
            if (isAsset) {
                // Get exact location of the file in the asssets folder.
                String key = FlutterMain.getLookupKeyForAsset(audioDirectory);
                fileDescriptor = assetManager.openFd(key);
                startOffset = fileDescriptor.getStartOffset();
                declaredLength = fileDescriptor.getDeclaredLength();
            } else {
                FileInputStream inputStream = new FileInputStream(audioDirectory);
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
        AudioProcessing audioData = new AudioProcessing();

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
                () -> preprocess(byteData));
        preprocessThread.start();
    }

    //No noticable performance difference with subscribleOn and observableOn
    public void preprocess(byte[] byteData) {
        Log.d(LOG_TAG, "Preprocessing audio file..");

        audioFile = new AudioFile(byteData, audioLength);
        audioFile.getObservable()
                .doOnComplete(() -> {
                    stopStream(); 
                    clearPreprocessing();
                })
                .subscribe(this::startRecognition);
        audioFile.splice();
    }

    public synchronized void startRecording() {
        if (recordingThread != null) {
            return;
        }
        recordingThread = new Thread(
                this::record);
        recordingThread.start();
    }

    //Some performance difference with subscribeOn and observeON (android only?)
    private void record() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

        recording = new Recording(bufferSize, audioLength, sampleRate, numOfInferences);
        recording.setReentrantLock(recordingBufferLock);
        recording.getObservable()
                .subscribeOn(Schedulers.io()) //run [observable] on background thread
                .observeOn(Schedulers.computation()) //tell [observer] to receive data on computation thread
                .doOnComplete(() -> {
                    stopStream();
                    clearRecording();
                    })
                .subscribe(this::startRecognition);
         
        recording.start();
    }

    private void startRecognition(short[] inputBuffer16) {
        Log.v(LOG_TAG, "Recognition started.");

        if (events == null) {
            return;
        }

        SignalProcessing signalProcessing = new SignalProcessing(sampleRate, nMFCC, nFFT, nMels, hopLength);
        AudioProcessing audioData = new AudioProcessing();

        float [] inputBuffer32; //for spectro
        float [][] inputData2D; //for raw audio
        float [][][][] inputData4D; // for spectro
        Object [] inputArray;

        int [] outputShape = tfLite.getOutputTensor(0).shape();
        int outputSize = Arrays.stream(outputShape).max().getAsInt();
        float [][] outputTensor = new float [1][outputSize];
        Map<Integer, Object> outputMap = new HashMap<>();
     
        
        Map<String, Object> finalResults = new HashMap();

        long startTime = new Date().getTime();

        switch (inputType) {

            case "mfcc":
      
                inputBuffer32 = audioData.normalizeBySigned16(inputBuffer16);
                float[][] mfcc = signalProcessing.getMFCC(inputBuffer32);

                inputData2D = transposeSpectro
                    ? signalProcessing.transpose2D(mfcc)
                    : mfcc;

                tfLite.run(inputData2D, outputTensor);
                break;

            case "melSpectrogram":
              
                inputBuffer32 = audioData.normalizeBySigned16(inputBuffer16);
                float[][] melSpectrogram = signalProcessing.getMelSpectrogram(inputBuffer32);
                
                inputData4D = transposeSpectro
                    ? signalProcessing.reshapeTo4DAndTranspose(melSpectrogram)
                    : signalProcessing.reshapeTo4D(melSpectrogram);
                    
                tfLite.run(inputData4D, outputTensor);
                break;

            case "spectrogram":
           
                inputBuffer32 = audioData.normalizeBySigned16(inputBuffer16);
                float[][] spectrogram = signalProcessing.getSpectrogram(inputBuffer32);
            
                inputData4D = transposeSpectro
                    ? signalProcessing.reshapeTo4DAndTranspose(spectrogram)
                    : signalProcessing.reshapeTo4D(spectrogram);

                tfLite.run(inputData4D, outputTensor);
                break;

            case "decodedWav":
                
                inputData2D = transposeAudio 
                    ? audioData.normaliseAndTranspose(inputBuffer16) 
                    : audioData.normalise(inputBuffer16);
                     
                int [] sampleRateList = new int[] { sampleRate }; 
                inputArray = new Object[] { inputData2D, sampleRateList};

                outputMap.put(0, outputTensor);
                tfLite.runForMultipleInputsOutputs(inputArray, outputMap);
                break;

            case "rawAudio":

                inputData2D = transposeAudio 
                    ? audioData.normaliseAndTranspose(inputBuffer16) 
                    : audioData.normalise(inputBuffer16);

                tfLite.run(inputData2D, outputTensor);
                break;

        }

        // working recognition variables
        long lastProcessingTimeMs = new Date().getTime() - startTime;
        Log.v(LOG_TAG, "Raw Scores: " + Arrays.toString(outputTensor[0]));
   
        if (!outputRawScores) {
            LabelSmoothing labelSmoothing = new LabelSmoothing(
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
