import java.io.File;
import java.io.IOException;

public class GameBoy {
    CPU cpu;
    MMU mmu;
    GPU gpu;
    APU apu;
    Timer2 timer;
    final static int TICKS_PER_SEC = 4194304;

    public GameBoy() throws IOException {
        apu = new APU();

        gpu = new GPU();

        timer = new Timer2();

        mmu = new MMU(gpu, apu, timer);

        gpu.mmu = this.mmu;

        cpu = new CPU(mmu, timer);

    }

    public void step() {
        cpu.tick();
        gpu.step();
    }

    public void loadROM(String file_name) throws IOException {
        cpu.mmu.loadROM(new File(file_name).toPath());
    }

    public int readPC() {
        return cpu.rf.PC.read();
    }

    public void onDebug() {
        cpu.DEBUG = true;
    }

    public void onDebugFile() {
        cpu.DEBUG_FILE = true;
    }
}
