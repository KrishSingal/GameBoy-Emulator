

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class MMUTest2 {
    public static void main(String[] args) throws Exception {
        GPU gpu = new GPU();

        APU apu = new APU();

        MMU mem = new MMU(gpu, apu);

        InputStream input = new FileInputStream("test/01-special.gb");

        File romFile = new File("test/01-special.gb");
        mem.loadROM(romFile.toPath());

        System.out.println(mem.ROMContent.length);
    }
}
