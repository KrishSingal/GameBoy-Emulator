import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TetrisTest {
    @Test
    public void tetrisTest() throws IOException {

        GameBoy gb = new GameBoy();

        gb.loadROM("roms/Pocket Love (J) [S].gb");

        while(gb.readPC() != 0x100) {
            gb.step();
        }
//
//        cpu.mmu.finished_bios = true;

        // Check values of IE and IF: Should be 0x09 and 0xE1 after running EI at 0x2ba

//        cpu.DEBUG = true;
//        cpu.DEBUG_FILE = true;

        // gb.onDebug();
        while(true) {
            gb.step();
        }



    }
}
