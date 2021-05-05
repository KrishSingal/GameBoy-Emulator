import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class AudioDevice {
    final int BITS_PER_SAMPLE = 8;
    final int CHANNELS = 2;
    final int BUFFER_TIME_MS = 100;

    private SourceDataLine sdl;

    private byte[] buffer;

    public void start(int samplingRate) {
        AudioFormat af = new AudioFormat(samplingRate, BITS_PER_SAMPLE, CHANNELS, true, true);
        buffer = new byte[(int) (((double) samplingRate) / 1000 * BUFFER_TIME_MS) * CHANNELS]; // sample frames
        try {
            sdl = AudioSystem.getSourceDataLine(af);
            sdl.open(af, buffer.length);
            sdl.start();
        }
        catch(LineUnavailableException e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
//        playing = false;
    }
}
