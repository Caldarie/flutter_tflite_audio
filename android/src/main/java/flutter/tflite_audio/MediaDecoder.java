/* Referemces
1. https://gist.github.com/a-m-s/1991ab18fbcb0fcc2cf9
2. https://github.com/tuntorius/mightier_amp/blob/7256c1cb120cc0c4fa1da7fd08ef6464964cadb4/android/app/src/main/java/com/tuntori/mightieramp/MediaDecoder.java
  */

package flutter.tflite_audio;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.content.res.AssetFileDescriptor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MediaDecoder {
    private static final String LOG_TAG = "Media_Decoder";

    private MediaExtractor extractor = new MediaExtractor();
    private MediaCodec decoder;
    
    private MediaFormat inputFormat;
    
    private ByteBuffer[] inputBuffers;
    private boolean end_of_input_file;

    private ByteBuffer[] outputBuffers;
    private int outputBufferIndex = -1;

    public MediaDecoder(AssetFileDescriptor fileDescriptor, long startOffset, long declaredLength) {

        try {
            extractor.setDataSource(fileDescriptor.getFileDescriptor(), startOffset, declaredLength);
        } catch(Exception e) {
            throw new RuntimeException("Failed to load audio file: ", e);
        }
            
            // Select the first audio track we find.
            int numTracks = extractor.getTrackCount();
            for (int i = 0; i < numTracks; ++i) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    extractor.selectTrack(i);
                    try {
                        decoder = MediaCodec.createDecoderByType(mime);
                    } catch(Exception e) {
                        throw new RuntimeException("Extractor.selectTrack error: ", e);
                    }
                    decoder.configure(format, null, null, 0);

                    /* when adding encoder, use these settings
                    format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
                    format.setInteger(MediaFormat.KEY_SAMPLE_RATE, 8000);
                    format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_8BIT);
                    decoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);*/

                    inputFormat = format;
                    break;
                }
            }
            
            if (decoder == null) {
                throw new IllegalArgumentException("No decoder for file format");
            }
            
            decoder.start();
            inputBuffers = decoder.getInputBuffers();
            outputBuffers = decoder.getOutputBuffers();
            end_of_input_file = false;
    }

    public void release()
    {
        extractor.release();
    }
    
    // Read the raw data from MediaCodec.
    // The caller should copy the data out of the ByteBuffer before calling this again
    // or else it may get overwritten.
    // private ByteBuffer readData(BufferInfo info) {
    private BufferInfo readData() {
        if (decoder == null)
            return null;

        BufferInfo info = new BufferInfo();
        
        for (;;) {
            // Read data from the file into the codec.
            if (!end_of_input_file) {
                int inputBufferIndex = decoder.dequeueInputBuffer(10000);
                if (inputBufferIndex >= 0) {
                    int size = extractor.readSampleData(inputBuffers[inputBufferIndex], 0); 
                    if (size < 0) {
                        // End Of File
                        decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        end_of_input_file = true;
                    } else {
                        decoder.queueInputBuffer(inputBufferIndex, 0, size, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }
            }

            // Read the output from the codec.
            if (outputBufferIndex >= 0)
                // Ensure that the data is placed at the start of the buffer
                outputBuffers[outputBufferIndex].position(0);
                
            outputBufferIndex = decoder.dequeueOutputBuffer(info, 10000);
            if (outputBufferIndex >= 0) {
                // Handle EOF
                if (info.flags != 0) {
                    decoder.stop();
                    decoder.release();
                    decoder = null;
                    return null;
                }
                
                return info;
         
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // This usually happens once at the start of the file.
                outputBuffers = decoder.getOutputBuffers();
               }
        }
    }
    
    // Return the Audio sample rate, in samples/sec.
    public int getSampleRate() {
        return inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
    }
    
    // Read the raw audio data in 16-bit format
    // Returns null on EOF
    // public short[] readShortData() {
    //     BufferInfo info = new BufferInfo();
    //     ByteBuffer data = readData(info);
        
    //     if (data == null)
    //         return null;
        
    //     int samplesRead = info.size/2;
    //     short[] returnData = new short[samplesRead];
        
    //     // Converting the ByteBuffer to an array doesn't actually make a copy
    //     // so we must do so or it will be overwritten later.
    //     System.arraycopy(data.asShortBuffer().array(), 0, returnData, 0, samplesRead);
    //     return returnData;
    // }

    public byte[] readByteData() {
        BufferInfo info = readData();

        if (info==null)
            return null;

        ByteBuffer data = currentBuffer();
        
        if (data == null)
            return null;
        
        byte[] returnData = new byte[info.size];
        
        if (info.size>0)
            data.get(returnData);

        releaseBuffer();
        return returnData;
    }


    private ByteBuffer currentBuffer()
        {
            return outputBuffers[outputBufferIndex];
        }

    private void releaseBuffer()
        {
            // Release the buffer so MediaCodec can use it again.
            // The data should stay there until the next time we are called.
            decoder.releaseOutputBuffer(outputBufferIndex, false);
        }
    }

        // private void startDecoding(MediaExtractor mediaExtractor) {
    //     boolean bIsEos = false;
    //     long startMs = System.currentTimeMillis();
    //     while (!quit.get()) {
    //         if (!bIsEos) {
    //             int inIndex = mediaCodec.dequeueInputBuffer(0);
    //             if (inIndex >= 0) {
    //                 ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inIndex);
    //                 // 读取一帧数据至buffer中
    //                 int nSampleSize = mediaExtractor.readSampleData(inputBuffer, 0);
    //                 if (nSampleSize < 0) {
    //                     mediaCodec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
    //                     bIsEos = true;
    //                 } else {
    //                     mediaCodec.queueInputBuffer(inIndex, 0, nSampleSize, mediaExtractor.getSampleTime(), 0);
    //                     // 继续下一取样
    //                     mediaExtractor.advance();
    //                 }

    //             }
    //         }
    //         int outIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
    //         if (outIndex >= 0) {
    //             ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outIndex);
    //             while (bufferInfo.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
    //                 try {
    //                     preprocessThread.sleep(10);
    //                 } catch (InterruptedException e) {
    //                     e.printStackTrace();
    //                 }
    //             }
    //             byte[] outData = new byte[bufferInfo.size];
    //             outputBuffer.get(outData);
    //             //清空缓存
    //             outputBuffer.clear();
    //             //播放解码后的数据
    //             Log.d(LOG_TAG, "Audio data: " + Arrays.toString(outData));
    //             Log.d(LOG_TAG, "Audio Size: " + bufferInfo.size);
    //             audioTrack.write(outData, 0, bufferInfo.size);
    //             // audioPlayer.play(outData, 0, bufferInfo.size);
    //             mediaCodec.releaseOutputBuffer(outIndex, true);
    //         }
    //         //所有解码的帧均已渲染，我们现在可以停止播放
    //         // if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
    //         //     quit();
    //         // }
    //     }
    // }

             // MediaPlayer mediaPlayer = new MediaPlayer();
            // mp.setDataSource(fileDescriptor.getFileDescriptor(), startOffset, declaredLength);
            // fileDescriptor.close();
            // mp.prepare();
            // mp.start();
            
            // MediaPlayer mediaPlayer;
            // MediaExtractor mediaExtractor = new MediaExtractor();
            // mediaExtractor.setDataSource(fileDescriptor.getFileDescriptor(), startOffset, declaredLength);
            // for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
            //     MediaFormat format = mediaExtractor.getTrackFormat(i);
            //     String mimeType = format.getString(MediaFormat.KEY_MIME);
            //     Log.d(LOG_TAG, "Audio Format: " + format);
            //     //Determine whether it is an audio channel
            //     if (mimeType != null && mimeType.startsWith("audio/")) {
            //         //Switch to audio channel
            //         mediaExtractor.selectTrack(i);
            //         mediaCodec = MediaCodec.createDecoderByType(mimeType);
            //         mediaCodec.configure(format, null, null, 0);
            //         // mediaPlayer = new MediaPlayer(
            //         //         format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
            //         //         AudioFormat.CHANNEL_OUT_STEREO,
            //         //         AudioFormat.ENCODING_PCM_16BIT);

            //         //--------AudioTrack--------
            //         int frequency = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            //         int channel = AudioFormat.CHANNEL_OUT_MONO;
            //         int sampBit = AudioFormat.ENCODING_PCM_16BIT;
            //         int minBufSize = AudioTrack.getMinBufferSize(frequency, channel, sampBit);
            //         audioTrack = new AudioTrack(
            //                 AudioManager.STREAM_MUSIC,
            //                 frequency, channel, sampBit, minBufSize, AudioTrack.MODE_STREAM
            //         );
            //         audioTrack.play();
            //         //--------AudioTrack--------

            //         mediaCodec.start();
            //         break;
            //     }
            // }

            // startDecoding(mediaExtractor);