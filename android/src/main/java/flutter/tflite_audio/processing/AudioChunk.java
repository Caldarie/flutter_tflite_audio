package flutter.tflite_audio;


// https://stackoverflow.com/questions/54566753/escaping-closure-in-swift-and-how-to-perform-it-in-java
public interface AudioChunk {
    void get(short [] data);
}