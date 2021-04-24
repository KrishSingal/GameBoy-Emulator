import org.junit.Test;

import java.io.File;
import java.io.IOException;

class MMUTest {

    @Test
    public void loadROMTest() throws IOException {
        MMU mem = new MMU();

        File romFile = new File("01-special.gb");
        mem.loadROM(romFile.toPath());

        System.out.println(mem.ROMContent.length);
    }
}