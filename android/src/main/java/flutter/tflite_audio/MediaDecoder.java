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
