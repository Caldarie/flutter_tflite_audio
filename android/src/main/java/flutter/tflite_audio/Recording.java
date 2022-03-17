package flutter.tflite_audio;

import android.annotation.SuppressLint;
import android.media.MediaRecorder;
import android.media.AudioRecord;
import android.media.AudioFormat;
import android.util.Log;

import java.util.concurrent.locks.ReentrantLock;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.PublishSubject;

/*
!References
https://www.javatpoint.com/java-closure
https://www.geeksforgeeks.org/method-within-method-in-java/
https://stackoverflow.com/questions/54566753/escaping-closure-in-swift-and-how-to-perform-it-in-java

```
.emit(new AudioChunk(){
                    @Override
                    public void get(short [] data) {
                        subject.onNext(data);
                    }
                })
```
!Code above same as lambda below:

`.emit(data -> subject.onNext(data))`

*/


public class Recording{

    private static final String LOG_TAG = "Recording";
 
    private int bufferSize;
    private int audioLength;
    private int sampleRate;
    private int numOfInferences;

    private AudioRecord record;
    private boolean shouldContinue;

    private RecordingData recordingData;
    private PublishSubject<short []> subject;
    private ReentrantLock recordingBufferLock;

    @SuppressLint("MissingPermission")
    public Recording(int bufferSize, int audioLength, int sampleRate, int numOfInferences){
        this.bufferSize = bufferSize;
        this.audioLength = audioLength;
        this.sampleRate = sampleRate;
        this.numOfInferences = numOfInferences;

        this.subject = PublishSubject.create();
        this.recordingData = new RecordingData(audioLength, bufferSize, numOfInferences);
        this.record = new AudioRecord(
            MediaRecorder.AudioSource.DEFAULT,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize);
    
    }

    public void setReentrantLock(ReentrantLock recordingBufferLock){
        this.recordingBufferLock = recordingBufferLock;
    }


    public Observable<short []> getObservable() {
        return (Observable<short []>) this.subject;
     } 


    public void stop(){
        shouldContinue = false;
        record.stop();
        record.release();  
        subject.onComplete();
    }

    public void start(){
        Log.v(LOG_TAG, "Recording started");
        shouldContinue = true;
        record.startRecording();
        splice();
        
    }

    public void splice(){

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(LOG_TAG, "Audio Record can't initialize!");
            return;
        }

        while (shouldContinue) {

            short[] shortData = new short [bufferSize];
            record.read(shortData, 0, shortData.length);
            recordingBufferLock.lock();

            try {
                switch (recordingData.getState()) {
                    case "append":
                        recordingData
                            .append(shortData);
                        break;

                    case "recognise":
                        recordingData
                            .append(shortData)
                            .emit(data -> subject.onNext(data))
                            .updateInferenceCount()
                            .clear();
                        break;

                    case "finalise":
                        recordingData
                            .append(shortData)
                            .emit(data -> subject.onNext(data));
                        stop();
                        break;

                    case "trimAndRecognise":
                        recordingData
                            .updateRemain()
                            .trimToRemain(shortData)
                            .emit(data -> subject.onNext(data))
                            .updateInferenceCount()
                            .clear()
                            .updateExcess()
                            .addExcessToNew(shortData);
                        break;

                    case "trimAndFinalise":
                        recordingData
                            .updateRemain()
                            .trimToRemain(shortData)
                            .emit(data -> subject.onNext(data));
                        stop();
                        break;
                    
                    default:
                        recordingData.displayErrorLog();
                        throw new AssertionError("Incorrect state when preprocessing");
                }
            } finally {
                recordingBufferLock.unlock();
            }


        }
    }
}