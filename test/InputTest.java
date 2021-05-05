import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InputTest {
    @Test
    public void inputTest() throws IOException {
        GameBoy gb = new GameBoy();

        gb.loadROM("roms/GBTICTAC.GB");

        gb.mmu.finished_bios = true;

        while(true) {
            gb.step();
        }
    }
}
