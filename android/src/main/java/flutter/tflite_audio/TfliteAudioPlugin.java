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


public class TfliteAudioPlugin implements MethodCallHandler, StreamHandler, FlutterPlugin, ActivityAware, PluginRegistry.RequestPermissionsResultListener {

    //ui elements
    private static final String LOG_TAG = "Tflite_audio";
    private static final int REQUEST_RECORD_AUDIO = 13;
    private static TfliteAudioPlugin instance;
    private Handler handler = new Handler(Looper.getMainLooper());

    //working recording variables
    AudioRecord record;
    short[] recordingBuffer;
    short[] recordingBufferCache;
    int countNumOfInferences = 1;
    int recordingOffset = 0;
    boolean shouldContinue = true;
    private Thread recordingThread;
    private final ReentrantLock recordingBufferLock = new ReentrantLock();

    // @tamnv working label variables
    private ArrayList<ArrayList<String>> labels;

    // @tamnv working recognition variables
    boolean lastInferenceRun = false;
    private long lastProcessingTimeMs;
    private Thread recognitionThread;
    private ArrayList<Interpreter> tfliteModels;
    private LabelSmoothing labelSmoothing = null;

    //flutter
    private AssetManager assetManager;
    private Activity activity;
    private Context applicationContext;
    private MethodChannel methodChannel;
    private EventChannel eventChannel;
    private EventSink events;

    //recording variables
    private int bufferSize;
    private int sampleRate;
    private int recordingLength;
    private int numOfInferences;

    //Determine input and output
    private String inputType;
    private boolean outputRawScores;

    // get objects to convert to float and long
    private double detectObj;
    private int avgWinObj;
    private int minTimeObj;
    
    //labelsmoothing variables 
    private float detectionThreshold;
    private long averageWindowDuration;
    private long minimumTimeBetweenSamples;
    private int suppressionTime;

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

        this.eventChannel = new EventChannel(messenger, "startAudioRecognition");
        this.eventChannel.setStreamHandler(this);

    }

    @Override
    public void onDetachedFromEngine(FlutterPluginBinding binding) {
        this.applicationContext = null;
        this.assetManager = null;

        this.methodChannel.setMethodCallHandler(null);
        this.methodChannel = null;

        this.eventChannel.setStreamHandler(null);
        this.eventChannel = null;
    }


    public void onAttachedToActivity(ActivityPluginBinding binding) {
        // onAttachedToActivity(binding.getActivity());
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
                Log.d(LOG_TAG, "loadModel");
                this.inputType = (String) arguments.get("inputType");
                this.outputRawScores = (boolean) arguments.get("outputRawScores");

                try {
                    loadModel(arguments);
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

        //load recording variables
        this.bufferSize = (int) arguments.get("bufferSize");
        this.sampleRate = (int) arguments.get("sampleRate");
        this.recordingLength = (int) arguments.get("recordingLength");
        this.numOfInferences = (int) arguments.get("numOfInferences");

        // get objects to convert to float and long
        this.detectObj = (double) arguments.get("detectionThreshold");
        this.avgWinObj = (int) arguments.get("averageWindowDuration");
        this.minTimeObj = (int) arguments.get("minimumTimeBetweenSamples");
        
        //load labelsmoothing variables 
        this.detectionThreshold = (float)detectObj;
        this.averageWindowDuration = (long)avgWinObj;
        this.minimumTimeBetweenSamples = (long)minTimeObj;
        this.suppressionTime = (int) arguments.get("suppressionTime");

        checkPermissions();
    }

    @Override
    public void onCancel(Object arguments) {
        this.events = null;
    }


    private void loadModel(HashMap arguments) throws IOException {
        tfliteModels = new ArrayList<Interpreter>();

        //  @tamnv Get model file names and label file names from given arguments
        // `model` should have format like "model1_path,model2_path"
        // `label` should have format like "label1_path,label2_path"
        String[] modelList = arguments.get("model").toString().split(",");
        String label = arguments.get("label").toString();
        String[] labelList = label.split(",");
        Object isAssetObj = arguments.get("isAsset");
        boolean isAsset = isAssetObj == null ? false : (boolean) isAssetObj;

        //  @tamnv Initialize tflite models
        for (int i = 0; i < modelList.length; i++) {
            String model = modelList[i];

            Log.d(LOG_TAG, "model name is: " + model);
            MappedByteBuffer buffer = null;
            String key = null;
            if (isAsset) {
                key = FlutterMain.getLookupKeyForAsset(model);
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

            //  @tamnv Actually initialize the model
            int numThreads = (int) arguments.get("numThreads");
            final Interpreter.Options tfliteOptions = new Interpreter.Options();
            tfliteOptions.setNumThreads(numThreads);
            Interpreter tflite = new Interpreter(buffer, tfliteOptions);
            tfliteModels.add(tflite);
        }
        // @tamnv load labels
        // After this step, `labels` would be like
        // [
        //    ["_silence_", "_unknown_", "class1", "class2"]
        //    ["_silence_", "_unknown_", "class7", "class5"]
        //    ...
        //    ["_silence_", "_unknown_", "class7", "class5"]
        // ]
        Log.d(LOG_TAG, "label name is: " + label);
        labels = new ArrayList<ArrayList<String>>();
        for (int i = 0; i < labelList.length; i++) {
            String key = null;
            if (labelList[i].length() > 0) {
                if (isAsset) {
                    key = FlutterMain.getLookupKeyForAsset(labelList[i]);
                    labels.add(loadLabels(assetManager, key));
                } else {
                    labels.add(loadLabels(null, labelList[i]));
                }
            }
        }

    }

    private ArrayList<String> loadLabels(AssetManager assetManager, String path) {
        BufferedReader br;
        ArrayList<String> labelTmp = new ArrayList<String>();
        try {
            if (assetManager != null) {
                br = new BufferedReader(new InputStreamReader(assetManager.open(path)));
            } else {
                br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(path))));
            }
            String line;
            while ((line = br.readLine()) != null) {
                labelTmp.add(line);
            }
            Log.d(LOG_TAG, "Labels: " + labelTmp.toString());
            br.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read label file", e);
        }
        return labelTmp;
    }


    private void checkPermissions() {
        Log.d(LOG_TAG, "Check for permissions");
        PackageManager pm = applicationContext.getPackageManager();
        int hasRecordPerm = pm.checkPermission(Manifest.permission.RECORD_AUDIO, applicationContext.getPackageName());
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
        Activity activity = TfliteAudioPlugin.getActivity();
        ActivityCompat.requestPermissions(activity,
                new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
    }

    // @Override
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

   
        short[] recordingFrame = new short[bufferSize / 2];
        recordingBuffer = new short[recordingLength]; //this buffer will be fed into model
        recordingBufferCache = new short[recordingLength]; //temporary holds recording buffer until recognitionStarts

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
            //Reads audio data and records it into redcordFrame
            int numberRead = record.read(recordingFrame, 0, recordingFrame.length);
            int recordingOffsetCount = recordingOffset + numberRead;
            // Log.v(LOG_TAG, "recordingOffsetCount: " + recordingOffsetCount);

            recordingBufferLock.lock();
            try {
                
                //Continue to append frame until it reaches recording length
                if(countNumOfInferences <= numOfInferences && recordingOffsetCount < recordingLength){
    
                    System.arraycopy(recordingFrame, 0, recordingBufferCache, recordingOffset, numberRead);
                    recordingOffset += numberRead;
                    Log.v(LOG_TAG, "recordingOffset: " + recordingOffset + "/" + recordingLength + " inferenceCount: " + countNumOfInferences);
                
                //When recording buffer populates, inference starts. Resets recording buffer after iference
                }else if(countNumOfInferences < numOfInferences  && recordingOffsetCount == recordingLength){
                 
          
                    Log.v(LOG_TAG, "Recording reached threshold");
                    System.arraycopy(recordingFrame, 0, recordingBufferCache, recordingOffset, numberRead);
                    recordingOffset += numberRead;
               
                    Log.v(LOG_TAG, "recordingOffset: " + recordingOffset + "/" + recordingLength);  
                    recordingBuffer = recordingBufferCache;
                    startRecognition();

                    Log.v(LOG_TAG, "Clearing recordingBufferCache..");
                    recordingBufferCache = new short[recordingLength];
                    recordingOffset = 0; 
                    //!TODO assert that recordingBuffer is populated
                    
                //when buffer exeeds max record length, trim and resize the buffer, append, and then start inference
                //Resets recording buffer after inference
                }else if(countNumOfInferences < numOfInferences && recordingOffsetCount > recordingLength){
                
                    Log.v(LOG_TAG, "Recording buffer exceeded maximum threshold");
                    Log.v(LOG_TAG, "Trimming recording frame to remaining recording buffer..");
                    // int remainingRecordingLength = recordingLength - recordingOffset - 1; 
                    int remainingRecordingFrame = recordingOffset + numberRead - recordingLength; //16200 -> 200 remaining 
                    int remainingRecordingLength = recordingLength - recordingOffset; //15800
                    short [] resizedRecordingFrame = Arrays.copyOf(recordingFrame, remainingRecordingLength);
                    System.arraycopy(resizedRecordingFrame, 0, recordingBufferCache, recordingOffset, remainingRecordingLength);
                    recordingOffset += remainingRecordingLength;
                    //!Todo assert that recordingOffset = 16000

                    Log.v(LOG_TAG, "Recording trimmed and appended at length: " + remainingRecordingLength);
                    Log.v(LOG_TAG, "recordingOffset: " + (recordingOffset) + "/" + recordingLength);    //should output max recording length

                    recordingBuffer = recordingBufferCache;
                    startRecognition();
                    
                    Log.v(LOG_TAG, "Clearing recording buffer..");
                    Log.v(LOG_TAG, "Appending remaining recording frame to new recording buffer..");
                    recordingBufferCache = new short[recordingLength];
                    recordingOffset = 0 + remainingRecordingFrame; //200/16000
                    System.arraycopy(recordingFrame, 0, recordingBufferCache, recordingOffset, numberRead);
                    Log.v(LOG_TAG, "recordingOffset: " + recordingOffset + "/" + recordingLength);  
                  

                //when count reaches max numOfInferences, stop all inference and recording
                //no need to count recordingOffset with numberRead as its final
                }else if(countNumOfInferences == numOfInferences && recordingOffsetCount > recordingLength){
                    
                    Log.v(LOG_TAG, "Reached indicated number of inferences.");
                    Log.v(LOG_TAG, "Recording buffer exceeded maximum threshold");
                    Log.v(LOG_TAG, "Trimming recording frame to remaining recording buffer..");
                
                    int remainingRecordingFrame = recordingOffset + numberRead - recordingLength; //16200 -> 200 remaining 
                    int remainingRecordingLength = recordingLength - recordingOffset; //15800
                    short [] resizedRecordingFrame = Arrays.copyOf(recordingFrame, remainingRecordingLength);
                    System.arraycopy(resizedRecordingFrame, 0, recordingBufferCache, recordingOffset, remainingRecordingLength);
                    Log.v(LOG_TAG, "recordingOffset: " + (recordingOffset + remainingRecordingLength) + "/" + recordingLength);    //should output max recording length
                    Log.v(LOG_TAG, "Unused excess recording length: " + remainingRecordingLength);

                    recordingBuffer = recordingBufferCache;
                    lastInferenceRun = true;

                    startRecognition();
                    stopRecording();

                    //reset after recognition and recording. Don't change position!!
                    recordingOffset = 0;
                    countNumOfInferences = 1;

                         
                //stop recording once numOfInference is reached.
                }else if(countNumOfInferences == numOfInferences && recordingOffsetCount == recordingLength){
                    Log.v(LOG_TAG, "Reached indicated number of inferences.");
                    
                    System.arraycopy(recordingFrame, 0, recordingBufferCache, recordingOffset, numberRead);
                    recordingBuffer = recordingBufferCache;
                    lastInferenceRun = true;

                    startRecognition();
                    stopRecording();

                     //reset after recognition and recording. Don't change position!!
                     recordingOffset = 0;
                     countNumOfInferences = 1;

                //For debugging - Stop recognition/recording for unusual situations
                }else{
                    
                    lastInferenceRun = true;
                    forceStopRecogniton();

                    Log.v(LOG_TAG, "something weird has happened"); 
                    Log.v(LOG_TAG, "-------------------------------------"); 
                    Log.v(LOG_TAG, "countNumOfInference: " + countNumOfInferences); 
                    Log.v(LOG_TAG, "numOfInference: " + numOfInferences); 
                    Log.v(LOG_TAG, "recordingOffset: " + recordingOffset);
                    Log.v(LOG_TAG, "recordingOffsetCount " + recordingOffsetCount);
                    Log.v(LOG_TAG, "recordingLength " + recordingLength);
                    Log.v(LOG_TAG, "-------------------------------------"); 

                    //reset after recognition and recording. Don't change position!!
                    recordingOffset = 0;
                    countNumOfInferences = 1;
                    
                }

            } finally {
                recordingBufferLock.unlock();

            }
        }
    }

    public synchronized void startRecognition() {
        if (recognitionThread != null) {
            return;
        }

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
        Log.v(LOG_TAG, "Recognition started.");
        countNumOfInferences += 1;

         //catches null exception.
         if(events == null){
            throw new AssertionError("Events is null. Cannot start recognition");
        }
        
        // @tamnv Get input shape
        int[] inputShape = tfliteModels.get(0).getInputTensor(0).shape();
        String inputShapeMsg = Arrays.toString(inputShape);
        Log.v(LOG_TAG, "Input shape: " + inputShapeMsg);

        outputRawScores = false;  // disable outputRawScores

        //  @tamnv Record output scores and predicted labels of each models
        ArrayList<LabelSmoothing.RecognitionResult> resultList = new ArrayList<LabelSmoothing.RecognitionResult>();

        //  @tamnv Make prediction one by one
        Map<String, Object> finalResults = new HashMap();
        for (int i = 0; i < tfliteModels.size(); i++) {
            //determine rawAudio or decodedWav input
            float[][] floatOutputBuffer = new float[1][labels.size()];
            short[] inputBuffer = new short[recordingLength]; 

            float[][] floatInputBuffer = {};
            int[] sampleRateList = {};
            Object[] inputArray = {};

            Map<Integer, Object> outputMap = new HashMap<>();

            switch (inputType) {
                case "decodedWav": 
                    Log.v(LOG_TAG, "InputType: " + inputType);
                    floatInputBuffer = new float[recordingLength][1];
                    sampleRateList = new int[]{sampleRate};
                    
                    inputArray = new Object[]{floatInputBuffer, sampleRateList};        
                    outputMap.put(0, floatOutputBuffer);
                break;

                case "rawAudio":
                    Log.v(LOG_TAG, "InputType: " + inputType);
                    if(inputShape[0] > inputShape[1] && inputShape[1] == 1){
                        //[recordingLength, 1]
                        floatInputBuffer = new float[recordingLength][1];
                    
                    }else if(inputShape[0] < inputShape[1] && inputShape[0] == 1){
                        //[1, recordingLength]
                        floatInputBuffer = new float[1][recordingLength];
                    }
                    // else{
                    //     throw new Exception("input shape: " + inputShapeMsg + " does not match with rawAudio");
                    // } 
                break;
            }


            recordingBufferLock.lock();
            try {
                int maxLength = recordingBuffer.length;
                System.arraycopy(recordingBuffer, 0, inputBuffer, 0, maxLength);
            } finally {
                recordingBufferLock.unlock();
            }
    

            long startTime = new Date().getTime();
            switch (inputType) {
                case "decodedWav": 
                    // We need to feed in float values between -1.0 and 1.0, so divide the
                    // signed 16-bit inputs.
                    for (int j = 0; j < recordingLength; ++j) {
                        floatInputBuffer[j][0] = inputBuffer[j] / 32767.0f;
                    }

                    tfliteModels.get(i).runForMultipleInputsOutputs(inputArray, outputMap);
                    lastProcessingTimeMs = new Date().getTime() - startTime;
                break;

                case "rawAudio":
                    // We need to feed in float values between -1.0 and 1.0, so divide the
                    // signed 16-bit inputs.
                    for (int j = 0; j < recordingLength; ++j) {
                        floatInputBuffer[0][j] = inputBuffer[j] / 32767.0f;
                    }
                    
                    // @tamnv Actually run the model
                    tfliteModels.get(i).run(floatInputBuffer, floatOutputBuffer);
                    lastProcessingTimeMs = new Date().getTime() - startTime;
                break;
            }

            // debugging purposes
            Log.v(LOG_TAG, "Raw Scores: " + Arrays.toString(floatOutputBuffer[0]));
            // Log.v(LOG_TAG, Long.toString(lastProcessingTimeMs));

            labelSmoothing =
            new LabelSmoothing(
                    labels.get(i),
                    averageWindowDuration,
                    detectionThreshold,
                    suppressionTime,
                    minimumTimeBetweenSamples);

            long currentTime = System.currentTimeMillis();
            final LabelSmoothing.RecognitionResult recognitionResult =
                    labelSmoothing.processLatestResults(floatOutputBuffer[0], currentTime);
            
            // @tamnv Save for further processing
            resultList.add(recognitionResult);

        }

        // @tamnv Aggregate the above predictions and get final result.
        // We just pick the class has the highest score as the right class.
        finalResults.put("inferenceTime", lastProcessingTimeMs);
        finalResults.put("hasPermission", true);
        float maxScore = 0.f;
        String finalLabel = "";
        for (int i = 0; i < resultList.size(); i++) {
            if (resultList.get(i).score > maxScore) {
                maxScore = resultList.get(i).score;
                finalLabel = resultList.get(i).foundCommand;
            }
        }
        finalResults.put("recognitionResult", finalLabel);
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
        
        if (recordingThread == null || shouldContinue == false ) {
            Log.d(LOG_TAG, "Recording has already stopped. Breaking stopRecording()");
            return;
        }

        shouldContinue = false;

        record.stop();
        record.release();
       
        recordingThread = null;
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