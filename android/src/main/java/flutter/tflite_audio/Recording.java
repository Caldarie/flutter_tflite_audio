package flutter.tflite_audio;

import android.media.MediaRecorder;
import android.media.AudioRecord;
import android.media.AudioFormat;
import android.util.Log;

import java.util.concurrent.locks.ReentrantLock;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.PublishSubject;


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

    public Recording(int bufferSize, int audioLength, int sampleRate, int numOfInferences){
        this.bufferSize = bufferSize;
        this.audioLength = audioLength;
        this.sampleRate = sampleRate;
        this.numOfInferences = numOfInferences;

        this.subject = PublishSubject.create();
        this.recordingData = new RecordingData();
        this.record = new AudioRecord(
            MediaRecorder.AudioSource.DEFAULT,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize);
        
        recordingData.setRecordingBufferSize(audioLength);
        recordingData.setNumOfInferences(numOfInferences);
        recordingData.setAudioLength(audioLength);
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

            short[] recordingFrame = new short [bufferSize/2];
            record.read(recordingFrame, 0, recordingFrame.length);
            // recordingData.updateReadCount(numberRead);

            recordingBufferLock.lock();

            try {
                switch (recordingData.getState()) {
                    case "append":
                        recordingData
                            .append(recordingFrame);
                        break;

                    case "recognise":
                        // Log.v(LOG_TAG, "recognising");
                        recordingData
                            .append(recordingFrame)
                            .emit(new AudioChunk(){
                                @Override
                                public void get(short [] data) {
                                    subject.onNext(data);
                                }
                            })
                            .updateCount()
                            .clear();
                        break;

                    case "finalise":
                        // Log.v(LOG_TAG, "finalising");
                        recordingData
                            .append(recordingFrame)
                            .emit(new AudioChunk(){
                                @Override
                                public void get(short [] data) {
                                    subject.onNext(data);
                                }
                            });
                        stop();
                        break;

                    case "trimAndRecognise":
                        // Log.v(LOG_TAG, "trimming and recognising");
                        recordingData
                            .calculateExcess()
                            .trimExcessToRemain(recordingFrame)
                            .emit(new AudioChunk(){
                                @Override
                                public void get(short [] data) {
                                    subject.onNext(data);
                                }
                            })
                            .updateCount()
                            .clear()
                            .addExcessToNew(recordingFrame)
                            .resetExcessCount();
                        break;

                    case "trimAndFinalise":
                        // Log.v(LOG_TAG, "trimming and finalising");
                        recordingData
                            .calculateExcess()
                            .trimExcessToRemain(recordingFrame)
                            .emit(new AudioChunk(){
                                @Override
                                public void get(short [] data) {
                                    subject.onNext(data);
                                }
                            });
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