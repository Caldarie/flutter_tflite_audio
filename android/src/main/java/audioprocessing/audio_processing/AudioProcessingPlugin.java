package audioprocessing.audio_processing;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

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
    private static final String LOG_TAG = "AudioProcessing";
    private static final int REQUEST_RECORD_AUDIO = 13;
    private final Registrar registrar;


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
                result.success(checkPermissions());
                break;
            case "startRecording":
                Log.d(LOG_TAG, "startRecording");
                requestMicrophonePermission();
                //record();
                result.success(null);
                break;
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
            Log.d(LOG_TAG, "Request for Audio - Permission Granted");
        }

        return true;
    }

    private boolean checkPermissions() {
        Context context = registrar.context();
        PackageManager pm = context.getPackageManager();
        //int hasStoragePerm = pm.checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, context.getPackageName());
        int hasRecordPerm = pm.checkPermission(Manifest.permission.RECORD_AUDIO, context.getPackageName());
//        boolean hasPermissions = hasStoragePerm == PackageManager.PERMISSION_GRANTED
//                && hasRecordPerm == PackageManager.PERMISSION_GRANTED;
        boolean hasPermissions = hasRecordPerm == PackageManager.PERMISSION_GRANTED;
        return hasPermissions;
    }

}


