import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestROMTesting {
    @Test
    public void CPUInstrsTest() throws IOException {
//        GPU gpu = new GPU();
//
//        APU apu = new APU();
//
//        MMU mmu = new MMU(gpu, apu);
//
//        CPU cpu = new CPU(mmu);
//
//        cpu.mmu.loadROM(new File("roms/02-interrupts.gb").toPath());
//        // TODO: Implement interrupts + timer
//
//        GameBoy gb = new GameBoy(cpu, gpu);

        GameBoy gb = new GameBoy();
        gb.loadROM("roms/cpu_instrs.gb");

//        cpu.DEBUG = true;

//        while(gb.readPC() != 0x100) {
//            gb.step();
//        }
//
//        cpu.mmu.finished_bios = true;

        gb.cpu.DEBUG = false;

        while(!gb.cpu.mmu.finished_bios) gb.step();

//        gb.cpu.DEBUG = true;
//        gb.onDebugFile();
        while(true) gb.step();

//        gb.step();
//        gb.step();
//        gb.step();
//        gb.step();
//        gb.step();
//        gb.step();
//
////        cpu.DEBUG = true;
////        cpu.DEBUG_FILE = true;
////        gb.onDebug();
////        gb.onDebugFile();
//        while(gb.readPC() != 0x200) {
//            gb.step();
//        }
//
//        // NOTE: TIMERS at 0x50 need to be implemented; we don't jump to 0x50
//
//
//        while(gb.readPC() != 0x210) {
//            gb.step();
//        }
//
////        cpu.DEBUG = true;
//        gb.onDebug();
//        gb.step();
////
//        gb.step();
//        gb.step();
//        gb.step();
//        gb.step();
//        gb.step();
//        long currTime = System.currentTimeMillis();
//        while(System.currentTimeMillis() < currTime + 100000) {
//            gb.step();
//        }
    }
}
