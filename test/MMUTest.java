import org.junit.Test;

import java.io.File;
import java.io.IOException;

class MMUTest {

    @Test
    public void loadROMTest() throws IOException {
        GPU gpu = new GPU();

        APU apu = new APU();

        MMU mem = new MMU(gpu, apu);

        File romFile = new File("01-special.gb");
        mem.loadROM(romFile.toPath());

        System.out.println(mem.ROMContent.length);
    }
}