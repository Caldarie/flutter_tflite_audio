package flutter.tflite_audio;

import android.util.Log;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.PublishSubject;

import java.nio.ShortBuffer;
import java.util.Arrays;
import java.nio.ByteBuffer; 
import java.nio.ByteOrder; 

public class AudioFile {

    private static final String LOG_TAG = "AudioFile";

    private ShortBuffer shortBuffer;
    private PublishSubject<short[]> subject;
    private AudioData audioData;

    private boolean isSplicing = false;

    public AudioFile(byte[] byteData, int audioLength) {

        shortBuffer = ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        subject = PublishSubject.create();
        audioData = new AudioData(audioLength, shortBuffer.limit());
        // fileSize = shortBuffer.limit(); // calculate how many bytes in 1 second in short array
        // shortAudioChunk = new short[audioLength];

    }

    public Observable<short[]> getObservable() {
        return (Observable<short[]>) this.subject;
    }

    public void stop() {
        isSplicing = false;
        subject.onComplete();
    }

    public void splice() {
        isSplicing = true;

        for (int i = 0; i < shortBuffer.limit(); i++) {

            short dataPoint = shortBuffer.get(i);

            if (isSplicing == false) {
                subject.onComplete();
                break;
            }

            switch (audioData.getState(i)) {
                case "append":
                    audioData
                        .append(dataPoint);
                break;
                case "recognise":
                    Log.d(LOG_TAG, "Recognising");
                    audioData
                        .append(dataPoint)
                        .displayInference()
                        .emit(new AudioChunk(){
                            @Override
                            public void get(short [] data) {
                                subject.onNext(data);
                                // Log.d(LOG_TAG, "Inference count: " + Arrays.toString(data));
                            }
                        })
                        .reset();
                    break;
                case "finalise":
                    Log.d(LOG_TAG, "Finalising");
                    audioData
                        .append(dataPoint)
                        .displayInference()
                        .emit(new AudioChunk(){
                            @Override
                            public void get(short [] data) {
                                subject.onNext(data);
                            }
                        });
                    stop();
                    break;
                case "padAndFinalise":
                    Log.d(LOG_TAG, "Padding and finalising");
                    audioData
                        .append(dataPoint)
                        .padSilence(i)
                        .displayInference()
                        .emit(new AudioChunk(){
                            @Override
                            public void get(short [] data) {
                                subject.onNext(data);
                            }
                        });
                    stop();
                    break;
         
                default:
                    throw new AssertionError("Incorrect state when preprocessing");
            }
        }
    }

}
