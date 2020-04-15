package audioprocessing.audio_processing;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import android.media.AudioRecord;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

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

    //ui elements
    private static final String LOG_TAG = "AudioProcessing";
    private static final int REQUEST_RECORD_AUDIO = 13;

    //working variables
    int recordingOffset = 0;
    boolean shouldContinue = true;
    private Thread recordingThread;
    private final Registrar registrar;
    short[] recordingBuffer = new short[RECORDING_LENGTH];
    private final ReentrantLock recordingBufferLock = new ReentrantLock();


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
            case "hasPermissions":
                Log.d(LOG_TAG, "Get hasPermissions");
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
            default:
                result.notImplemented();
                break;
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
            Log.v(LOG_TAG, "read: " + numberRead);
            int maxLength = recordingBuffer.length;
            recordingBufferLock.lock();
            try {
                if (recordingOffset + numberRead < maxLength) {
                    System.arraycopy(audioBuffer, 0, recordingBuffer, recordingOffset, numberRead);
                } else {
                    shouldContinue = false;
                }
                recordingOffset += numberRead;
            } finally {
                recordingBufferLock.unlock();
            }
        }
        record.stop();
        Log.e(LOG_TAG, "Recording stopped.");
        record.release();
        //startRecognition();
    }

}


