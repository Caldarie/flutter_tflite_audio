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

//!TESTING PURPOSE FOR PREPROCESSING AUDIO
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer; 
import android.media.MediaExtractor; 
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaFormat;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.core.app.ActivityCompat;
import androidx.annotation.NonNull;

import org.tensorflow.lite.Interpreter;

import java.util.concurrent.CompletableFuture; //required to get value from thread
import java.util.concurrent.CountDownLatch;
import java.io.BufferedReader;
import java.io.BufferedInputStream; //required for preprocessing
import java.io.ByteArrayOutputStream; //required for preprocessing
import java.io.DataOutputStream; //required for preprocessing
import java.io.InputStream; //required for preprocessing
import java.io.ObjectOutputStream; //required for preprocessing 
import java.io.InputStreamReader; 
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteOrder; //required for preprocessing
import java.nio.MappedByteBuffer;
import java.nio.ShortBuffer;
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
    private static final int REQUEST_READ_EXTERNAL_STORAGE = 1;
    private static TfliteAudioPlugin instance;
    private Handler handler = new Handler(Looper.getMainLooper());

    //working recording variables
    AudioRecord record;
    // short[] recordingBuffer;
    // short[] recordingBufferCache;
    int countNumOfInferences = 1;
    int recordingOffset = 0;
    boolean shouldContinue = true;
    private Thread recordingThread;
    private final ReentrantLock recordingBufferLock = new ReentrantLock();

    //preprocessing variables
    private Thread preprocessThread;
    private String audioDirectory;

    //working label variables
    private List<String> labels;

    //working recognition variables
    boolean lastInferenceRun = false;
    private long lastProcessingTimeMs;
    private Thread recognitionThread;
    private Interpreter tfLite;
    private LabelSmoothing labelSmoothing = null;

    //flutter
    private AssetManager assetManager;
    private Activity activity;
    private Context applicationContext;
    private MethodChannel methodChannel;
    private EventChannel audioRecognitionChannel;
    private EventChannel fileRecognitionChannel;
    private EventSink events;

    //recording variables
    private int bufferSize;
    private int sampleRate;
    private int recordingLength;
    private int numOfInferences;

    //Determine input and output
    private String inputType;
    private boolean outputRawScores;

    // // get objects to convert to float and long
    // private double detectObj;
    // private int avgWinObj;
    // private int minTimeObj;
    
    //labelsmoothing variables 
    private float detectionThreshold;
    private long averageWindowDuration;
    private long minimumTimeBetweenSamples;
    private int suppressionTime;

    //!Debugging - delete this
    private MediaCodec mediaCodec;
    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    private AtomicBoolean quit = new AtomicBoolean(false);
    private AudioTrack audioTrack;

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
                Log.d(LOG_TAG, "loadModel");
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
        
        Log.d(LOG_TAG, "Parameters: " + arguments);

        // label smoothing variables
        double detectObj = (double) arguments.get("detectionThreshold");
        this.detectionThreshold = (float)detectObj;
        int avgWinObj = (int) arguments.get("averageWindowDuration");
        this.averageWindowDuration = (long)avgWinObj;
        int minTimeObj = (int) arguments.get("minimumTimeBetweenSamples");
        this.minimumTimeBetweenSamples = (long)minTimeObj;
        this.suppressionTime = (int) arguments.get("suppressionTime");
                

        switch ((String) arguments.get("method")) {
            case "setAudioRecognitionStream":
                this.bufferSize = (int) arguments.get("bufferSize");
                this.sampleRate = (int) arguments.get("sampleRate");
                this.recordingLength = (int) arguments.get("recordingLength");
                this.numOfInferences = (int) arguments.get("numOfInferences");
                checkPermissions(REQUEST_RECORD_AUDIO);
                break;
            case "setFileRecognitionStream":
                // Log.d(LOG_TAG, "setting file recognition listener");
                this.audioDirectory = (String) arguments.get("audioDirectory");
                this.recordingLength = (int) arguments.get("recordingLength");
                checkPermissions(REQUEST_READ_EXTERNAL_STORAGE);
                break;
            default:
                throw new AssertionError("Error with listening to stream.");
        }

    
    }

    @Override
    public void onCancel(Object _arguments) {
        this.events = null;

        // HashMap arguments = (HashMap) _arguments;
        // this.detectionThreshold = null;
        // this.averageWindowDuration = null;
        // this.minimumTimeBetweenSamples = null;
        // this.suppressionTime = null;
        
        // switch ((String) arguments.get("method")) {
        //     case "setAudioRecognitionStream":
        //         this.bufferSize = null;
        //         this.sampleRate = null;
        //         this.recordingLength = null;
        //         this.numOfInferences = null;
        //         break;
        //     case "setFileRecognitionStream'":
        //         this.audioDirectory = null;
        //         this.recordingLength = null;
        //         break;
        //     default:
        //         throw new AssertionError("Error with stream cancellation.");
        // }

     
    }


    private void loadModel(HashMap arguments) throws IOException {
        this.inputType = (String) arguments.get("inputType");
        this.outputRawScores = (boolean) arguments.get("outputRawScores");
        String model = arguments.get("model").toString();
        Log.d(LOG_TAG, "model name is: " + model);
        Object isAssetObj = arguments.get("isAsset");
        boolean isAsset = isAssetObj == null ? false : (boolean) isAssetObj;
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

        int numThreads = (int) arguments.get("numThreads");
        final Interpreter.Options tfliteOptions = new Interpreter.Options();
        tfliteOptions.setNumThreads(numThreads);
        tfLite = new Interpreter(buffer, tfliteOptions);

        //load labels
        String labels = arguments.get("label").toString();
        Log.d(LOG_TAG, "label name is: " + labels);

        if (labels.length() > 0) {
            if (isAsset) {
                key = FlutterMain.getLookupKeyForAsset(labels);
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
            Log.d(LOG_TAG, "Labels: " + labels.toString());
            br.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read label file", e);
        }

    }


    private void checkPermissions(int permissionType) {
        Log.d(LOG_TAG, "Check for permission. Request code: " + permissionType);

        PackageManager pm = applicationContext.getPackageManager();

        switch(permissionType){
            case REQUEST_RECORD_AUDIO:
                int recordPerm = pm.checkPermission(Manifest.permission.RECORD_AUDIO, applicationContext.getPackageName());
                boolean hasRecordPerm = recordPerm == PackageManager.PERMISSION_GRANTED;

                if (hasRecordPerm) {
                    startRecording();
                    Log.d(LOG_TAG, "Permission already granted. start recording");
                } else {
                    requestPermission(REQUEST_RECORD_AUDIO);
                }
                break;

            case REQUEST_READ_EXTERNAL_STORAGE:
                int readPerm = pm.checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, applicationContext.getPackageName());
                boolean hasReadPerm = readPerm == PackageManager.PERMISSION_GRANTED;
                if (hasReadPerm) {
                    loadAudioFile();
                    Log.d(LOG_TAG, "Permission already granted. Loading audio file..");
                } else {
                    requestPermission(REQUEST_READ_EXTERNAL_STORAGE); 
                }
                break;
            default:
                Log.d(LOG_TAG, "Something weird has happened");
                
        }

        //Add run time error here for other permissions?
        
  
    }

    private void requestPermission(int permissionType) {
        Log.d(LOG_TAG, "Permission requested.");
        Activity activity = TfliteAudioPlugin.getActivity();

        switch(permissionType){
            case REQUEST_RECORD_AUDIO:
                ActivityCompat.requestPermissions(activity,
                        new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
                break;
            case REQUEST_READ_EXTERNAL_STORAGE:
                ActivityCompat.requestPermissions(activity,
                        new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_EXTERNAL_STORAGE);
                break;
            default:
                Log.d(LOG_TAG, "Something weird has happened");
            }
    }

    // @Override
    public boolean onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_RECORD_AUDIO:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    startRecording();
                    Log.d(LOG_TAG, "Permission granted. Start recording...");
                }else{
                    showRationaleDialog(
                        "Microphone Permissions",
                        "Permission has been declined. Please accept permissions in your settings"
                    );
                    if (events != null) {
                        events.endOfStream();
                    }
                }
                break;
            case REQUEST_READ_EXTERNAL_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    loadAudioFile();
                    Log.d(LOG_TAG, "Permission granted. Loading audio file...");
                }else{
                    showRationaleDialog(
                        "Read External Storage Permissions",
                        "Permission has been declined. Please accept permissions in your settings"
                    );
                    if (events != null) {
                        events.endOfStream();
                    }
                }
                break;
            default:
            Log.d(LOG_TAG, "onRequestPermissionsResult: default error...");
                break;
        }
        //placehold value 
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
        preprocessThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                preprocessAudioFile();
                            }
                        });
        preprocessThread.start();
    }

    // public static short[] shortMe(byte[] bytes) {
    //     short[] out = new short[bytes.length / 2]; // will drop last byte if odd number
    //     ByteBuffer bb = ByteBuffer.wrap(bytes);
    //     for (int i = 0; i < out.length; i++) {
    //         out[i] = bb.getShort();
    //     }
    //     return out;
    // }

    public byte [] appendByteData(byte[] src, byte[] dst){
        byte[] result = new byte[src.length + dst.length];
        System.arraycopy(src, 0, result, 0, src.length);
        System.arraycopy(dst, 0, result, src.length, dst.length);
        return result;
    }

    private void preprocessAudioFile(){
        Log.d(LOG_TAG, "Preprocessing audio file..");
        try {
            //Get path from assets
            String key = FlutterMain.getLookupKeyForAsset(audioDirectory);
            AssetFileDescriptor fileDescriptor = assetManager.openFd(key);
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());

            //get exact location from storage
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            // MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);

            //Extract raw audio data in byte form then wrap as a 
            MediaDecoder decoder = new MediaDecoder(fileDescriptor, startOffset, declaredLength);
            byte[] byteData = {};
            byte [] readData;
            while ((readData = decoder.readByteData()) != null) {
                byteData = appendByteData(readData, byteData);
                Log.d(LOG_TAG, "data chunk length: " + readData.length);
             }
            //! debug - for data thats lower than inputShape
            // byteData[] = decoder.readByteData();
          
            //Convert byte to short form and prepare the data before feeding the model
            // ByteBuffer byteBuffer = ByteBuffer.wrap(byteData);
            ShortBuffer shortBuffer = ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            int inputSize = recordingLength; //!todo - set this as automatic max(inputShape)
            // int shortDataLength = byteData.length/2;  // will drop last byte if odd number
            int shortDataLength = shortBuffer.limit();
            int numOfInferences =  numOfInferences =  (int) Math.ceil((float) shortDataLength/inputSize);
            Log.d(LOG_TAG, "byte length: " + byteData.length);
            Log.d(LOG_TAG, "short length: " + shortDataLength);
            Log.d(LOG_TAG, "numOfInference " + numOfInferences);

            //Keep track of preprocessing loop
            short [] audioChunk = new short[inputSize]; 
            int indexCount = 0;
            int inferenceCount = 0;

            //!Debugging - check if short plays sound
            // int sampleRate = 16000;
            // int bytes = 2; //16 bits = 2 bytes
            // double bitsPerSample = (double) byteData.length/sampleRate;
            // int totalSeconds = (int) Math.round(bitsPerSample/bytes);
            // Log.d(LOG_TAG, "Total Seconds: " + totalSeconds);

            // short [] out = new short[shortDataLength];
            //     for (int i = 0; i < shortDataLength; i++) {
            //         out[i] = shortBuffer.get(i);
            //     }

            // AudioTrack  at=new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO,
            //         AudioFormat.ENCODING_PCM_16BIT, sampleRate*totalSeconds /* 1 second buffer */,
            //         AudioTrack.MODE_STREAM);
            // at.write(out, 0, out.length);
            // at.play();  
            // forceStopRecogniton();

            if(shortDataLength <= inputSize){
                Log.d(LOG_TAG, "Short data is under input size.");
           
                short [] out = new short[shortDataLength];
                for (int i = 0; i < shortDataLength; i++) {
                    out[i] = shortBuffer.get(i);
                }
                System.arraycopy(out, 0, audioChunk, 0, shortDataLength);
                Arrays.fill(audioChunk, shortDataLength, inputSize-1, (short) 0);   //fills out remaining array with zeros
                lastInferenceRun = true;
                startRecognition(audioChunk);

                //!Remove - debugging
                Log.d(LOG_TAG, "audioChunk second last element: " + audioChunk[shortDataLength-2]);
                Log.d(LOG_TAG, "audioChunk last element: " + audioChunk[shortDataLength-1]);
                Log.d(LOG_TAG, "audioChunk first remaining element: " + audioChunk[shortDataLength]);
                Log.d(LOG_TAG, "audioChunk last remaining element: " + audioChunk[inputSize-1]);

            }else{
                 for (int i = 0; i < shortDataLength; i++){   
                
                    //Inferences that is not final
                    if((i+1) % inputSize == 0 && inferenceCount != numOfInferences){
                        
                        //!Remove - debugging
                        Log.d(LOG_TAG, "Inference count: " + (inferenceCount+1) + "/" + numOfInferences);
                        Log.d(LOG_TAG, "Index: " + i);
                        Log.d(LOG_TAG, "IndexCount: " + indexCount);
                        Log.d(LOG_TAG, "Audio file " + inferenceCount + ": " + Arrays.toString(audioChunk));

                        startRecognition(audioChunk);

                        // awaits for recogniton thread to finish before looping.
                        try{
                            recognitionThread.join();
                        }catch(InterruptedException ex){
                            Log.d(LOG_TAG, "Error with recognition thread: " + ex);
                        }
                        
                        //need to reset index or out of array error
                        audioChunk = new short[inputSize];
                        audioChunk[indexCount] = shortBuffer.get(i);
                        indexCount = 0; 
                        inferenceCount += 1;
                    
                    //Final inference 
                    }else if(i == shortDataLength-1 && inferenceCount == numOfInferences-1){

                            //!Remove - debugging
                            Log.d(LOG_TAG, "Inference count: " + (inferenceCount+1) + "/" + numOfInferences);
                            Log.d(LOG_TAG, "Index: " + i);
                            Log.d(LOG_TAG, "IndexCount: " + indexCount);
                            Log.d(LOG_TAG, "Final audio file: " + Arrays.toString(audioChunk));

                            if((i+1) % inputSize != 0){
                                Log.d(LOG_TAG, "Padding missing elements to audioChunk..");

                                //!Remove - debugging
                                Log.d(LOG_TAG, "audioChunk first element: " + audioChunk[0]);
                                Log.d(LOG_TAG, "audioChunk second last element: " + audioChunk[indexCount-2]);
                                Log.d(LOG_TAG, "audioChunk last element: " + audioChunk[indexCount-1]);
                                Log.d(LOG_TAG, "audioChunk first missing element: " + audioChunk[indexCount]);
                                Log.d(LOG_TAG, "audioChunk second missing element: " + audioChunk[indexCount+1]);
                                Log.d(LOG_TAG, "audioChunk second last missing element: " + audioChunk[inputSize-2]);
                                Log.d(LOG_TAG, "audioChunk last missing element: " + audioChunk[inputSize-1]);

                                Arrays.fill(audioChunk, indexCount, inputSize-1, (short) 0);
                            }

                            lastInferenceRun = true;
                            startRecognition(audioChunk);

                            // awaits for recogniton thread to finish before looping.
                            try{
                                recognitionThread.join();
                            }catch(InterruptedException ex){
                                Log.d(LOG_TAG, "Error with recognition thread: " + ex);
                            }

                            //clears out memmory and threads after preprocessing is done
                            stopPreprocessing();
                            byteData = null;
                            audioChunk = null;
                            indexCount = 0; 
                            inferenceCount = 0;

                            //!TODO - THIS IS NOT SHOWING UP - MAY BE THE REASON WHY ITS RETURNING NAN OUTPUT
                            Log.d(LOG_TAG, "Final audio file: " + Arrays.toString(audioChunk));
                    
                    //append mappedbytebuffer to inference buffer
                    }else{
                        audioChunk[indexCount] = shortBuffer.get(i);
                        //for debugging
                        // if(inferenceCount == numOfInferences-1){
                        //     Log.d(LOG_TAG, "Index: " + i);
                        //     Log.d(LOG_TAG, "IndexCount: " + indexCount);
                        // }
                        indexCount += 1;
                    }
                }
               
            }
            
          } catch(IOException e) {
            Log.d(LOG_TAG, "Error loading audio file: " + e);
          }

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
        // recordingBuffer = new short[recordingLength]; //this buffer will be fed into model
        short[] recordingBufferCache = new short[recordingLength]; //temporary holds recording buffer until recognitionStarts

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
                    // recordingBuffer = recordingBufferCache;
                    startRecognition(recordingBufferCache);
                    countNumOfInferences += 1;

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

                    // recordingBuffer = recordingBufferCache;
                    startRecognition(recordingBufferCache);
                    countNumOfInferences += 1;
                    
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

                    // recordingBuffer = recordingBufferCache;
                    lastInferenceRun = true;

                    startRecognition(recordingBufferCache);
                    stopRecording();

                    //reset after recognition and recording. Don't change position!!
                    recordingOffset = 0;
                    countNumOfInferences = 1;

                         
                //stop recording once numOfInference is reached.
                }else if(countNumOfInferences == numOfInferences && recordingOffsetCount == recordingLength){
                    Log.v(LOG_TAG, "Reached indicated number of inferences.");
                    
                    System.arraycopy(recordingFrame, 0, recordingBufferCache, recordingOffset, numberRead);
                    // recordingBuffer = recordingBufferCache;
                    lastInferenceRun = true;

                    startRecognition(recordingBufferCache);
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

    public synchronized void startRecognition(short[] recordingBuffer) {
        if (recognitionThread != null) {
            return;
        }

        recognitionThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                recognize(recordingBuffer);                       
                            }
                        });
        recognitionThread.start();
    }


    private void recognize(short[] recordingBuffer) {
        Log.v(LOG_TAG, "Recognition started.");
 
        //  catches null exception.
         if(events == null){
            throw new AssertionError("Events is null. Cannot start recognition");
        }

        int[] inputShape = tfLite.getInputTensor(0).shape();
        String inputShapeMsg = Arrays.toString(inputShape);
        Log.v(LOG_TAG, "Input shape: " + inputShapeMsg);

       //determine rawAudio or decodedWav input
        float[][] floatInputBuffer = {};
        int[] sampleRateList = {};
        float[][] floatOutputBuffer = new float[1][labels.size()];
        short[] inputBuffer = new short[recordingLength]; 

        //Used for multiple input and outputs (decodedWav)
        Object[] inputArray = {};
        Map<Integer, Object> outputMap = new HashMap<>();
        Map<String, Object> finalResults = new HashMap();

        switch (inputType) {
            case "decodedWav": 
                Log.v(LOG_TAG, "InputType: " + inputType);
                floatInputBuffer = new float[recordingLength][1];
                sampleRateList = new int[]{sampleRate};
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
                else{
                    throw new AssertionError(inputType + " is an incorrect input type");
                } 
            break;
        }


        recordingBufferLock.lock();
        try {
            int maxLength = recordingBuffer.length;
            System.arraycopy(recordingBuffer, 0, inputBuffer, 0, maxLength);
            //  System.arraycopy(recordingBuffer, 0, inputBuffer, 0, recordingLength);
        } finally {
            recordingBufferLock.unlock();
        }

        long startTime = new Date().getTime();
        switch (inputType) {
            case "decodedWav": 
                // We need to feed in float values between -1.0 and 1.0, so divide the
                // signed 16-bit inputs.
                for (int i = 0; i < recordingLength; ++i) {
                    floatInputBuffer[i][0] = inputBuffer[i] / 32767.0f;
                }

                inputArray = new Object[]{floatInputBuffer, sampleRateList};        
                outputMap.put(0, floatOutputBuffer);

                tfLite.runForMultipleInputsOutputs(inputArray, outputMap);
                lastProcessingTimeMs = new Date().getTime() - startTime;
            break;

            case "rawAudio":
                // We need to feed in float values between -1.0 and 1.0, so divide the
                 // signed 16-bit inputs.
                for (int i = 0; i < recordingLength; ++i) {
                    floatInputBuffer[0][i] = inputBuffer[i] / 32767.0f;
                }

                tfLite.run(floatInputBuffer, floatOutputBuffer);
                lastProcessingTimeMs = new Date().getTime() - startTime;
            break;
    }

        // debugging purposes
        Log.v(LOG_TAG, "Raw Scores: " + Arrays.toString(floatOutputBuffer[0]));
        // Log.v(LOG_TAG, Long.toString(lastProcessingTimeMs));

        if(outputRawScores == false){
            labelSmoothing =
            new LabelSmoothing(
                    labels,
                    averageWindowDuration,
                    detectionThreshold,
                    suppressionTime,
                    minimumTimeBetweenSamples);

            long currentTime = System.currentTimeMillis();
            final LabelSmoothing.RecognitionResult recognitionResult =
                    labelSmoothing.processLatestResults(floatOutputBuffer[0], currentTime);
            finalResults.put("recognitionResult", recognitionResult.foundCommand);
        }else{
            finalResults.put("recognitionResult", Arrays.toString(floatOutputBuffer[0]));
        }

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

    public void stopPreprocessing(){
        if (preprocessThread == null) {
            return;
        }

        Log.d(LOG_TAG, "Prepocesing stopped.");
        preprocessThread = null;

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

    public void forceStopRecogniton() {

        stopRecording();
        stopRecognition();
        stopPreprocessing();

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


