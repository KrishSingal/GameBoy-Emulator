import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CPUTest {

    @Test
    public void flagsTest() {
        RegisterFile rf = new RegisterFile();

        System.out.println(RegisterFile.CFLAG_BIT);
        System.out.println(RegisterFile.NFLAG_BIT);
        System.out.println(RegisterFile.HFLAG_BIT);
        System.out.println(RegisterFile.ZFLAG_BIT);

        rf.setFlag(RegisterFile.CFLAG_BIT, true);
        rf.setFlag(RegisterFile.NFLAG_BIT, true);
        rf.setFlag(RegisterFile.HFLAG_BIT, true);
        rf.setFlag(RegisterFile.ZFLAG_BIT, true);
        assertEquals(rf.getFlag(RegisterFile.CFLAG_BIT), 1);
        assertEquals(rf.getFlag(RegisterFile.NFLAG_BIT), 1);
        assertEquals(rf.getFlag(RegisterFile.HFLAG_BIT), 1);
        assertEquals(rf.getFlag(RegisterFile.ZFLAG_BIT), 1);

        rf.setFlag(RegisterFile.CFLAG_BIT, false);
        assertEquals(rf.getFlag(RegisterFile.CFLAG_BIT), 0);
        assertEquals(rf.getFlag(RegisterFile.NFLAG_BIT), 1);
        assertEquals(rf.getFlag(RegisterFile.HFLAG_BIT), 1);
        assertEquals(rf.getFlag(RegisterFile.ZFLAG_BIT), 1);

        rf.setFlag(RegisterFile.ZFLAG_BIT, false);
        assertEquals(rf.getFlag(RegisterFile.CFLAG_BIT), 0);
        assertEquals(rf.getFlag(RegisterFile.NFLAG_BIT), 1);
        assertEquals(rf.getFlag(RegisterFile.HFLAG_BIT), 1);
        assertEquals(rf.getFlag(RegisterFile.ZFLAG_BIT), 0);
    }

    public void assertReg(Register r, int value) {
        assertEquals(r.read(), value);
    }

    public void assertRegsReset(CPU cpu) {
        assertReg(cpu.rf.A, 0);
        assertReg(cpu.rf.B, 0);
        assertReg(cpu.rf.C, 0);
        assertReg(cpu.rf.D, 0);
        assertReg(cpu.rf.E, 0);
        assertReg(cpu.rf.F, 0);
        assertReg(cpu.rf.H, 0);
        assertReg(cpu.rf.L, 0);
        assertReg(cpu.rf.AF, 0);
        assertReg(cpu.rf.BC, 0);
        assertReg(cpu.rf.DE, 0);
        assertReg(cpu.rf.HL, 0);
    }

    public void assertMem(MMU mmu, int address, int value) {
        assertEquals(mmu.readByte(address), value);
    }

    /*
    @Test
    public void testBootROM() throws IOException {
        CPU cpu = new CPU();

        cpu.mmu.loadROM(new File("roms/tetris.gb").toPath());
        assertEquals(cpu.rf.PC.read(), 0);
        assertEquals(cpu.rf.SP.read(), 0);
        assertRegsReset(cpu);

        // $0000
        cpu.tick(); // LD SP,$fffe

        // $0003
        cpu.tick(); // XOR A

        // $0004
        cpu.tick(); // LD HL,$9fff

        // Addr_0007: $0007
        cpu.tick(); // LD (HL-), A
        assertMem(cpu.mmu, 0x9fff, 0);
        assertReg(cpu.rf.HL, 0x9ffe); // HL-

        assertMem(cpu.mmu, 0x0008, 0xCB);

        // $0008
        assertEquals((cpu.rf.H.read() >> 8) & 1, 0);
        cpu.tick(); // BIT 7, H

        // $000a
        cpu.tick(); // JR NZ, Addr_0007

        // $000c
        cpu.tick(); // LD HL, $ff26

        // $000f
        cpu.tick(); // LD C,$11

        // $0011
        cpu.tick(); // LD A,$80

        // $0013
        cpu.tick(); // LD (HL-),A
//        assertMem(cpu.mmu, 0xff26, 0x80);

        // $0014
//        assertMem(cpu.mmu, 0xff11, 0x0);
        cpu.tick(); // LD ($FF00+C),A

        // $0015
        cpu.tick(); // INC C

        // $0016
        cpu.tick(); // LD A,$f3

        // $0018
        cpu.tick(); // LD ($FF00+C),A

        // $0019
        cpu.tick(); // LD (HL-),A

        // $001a
        cpu.tick(); // LD A,$77

        // $001c
        cpu.tick(); // LD (HL),A		;

        cpu.tick(); // LD A,$fc		; $001d  Setup BG palette
        cpu.tick(); // LD ($FF00+$47),A	; $001f

        cpu.tick(); // LD DE,$0104		; $0021  Convert and load logo data from cart into Video RAM
        cpu.tick(); // LD HL,$8010		; $0024
        // Addr_0027:
        cpu.tick(); // LD A,(DE)		; $0027
        cpu.tick(); // CALL $0095		; $0028

//        ; ==== Graphic routine ====
        cpu.tick(); // LD C,A		; $0095  "Double up" all the bits of the graphics data
        cpu.tick(); // LD B,$04		; $0096     and store in Video RAM

//        Addr_0098:
        cpu.tick(); // PUSH BC		; $0098
        cpu.tick(); // RL C			; $0099
        cpu.tick(); // RLA			; $009b
        cpu.tick(); // POP BC		; $009c
        cpu.tick(); // RL C			; $009d
        cpu.tick(); // RLA			; $009f
        cpu.tick(); // DEC B			; $00a0

        cpu.tick(); // JR NZ Addr_0098 ; 00a1

        while (cpu.rf.PC.read() != 0x00a3) {
            cpu.tick();
        }

        cpu.tick(); // LD (HL+),A		; $00a3
        cpu.tick(); // INC HL		; $00a4
        cpu.tick(); // LD (HL+),A		; $00a5
        cpu.tick(); // INC HL		; $00a6
        cpu.tick(); // RET			; $00a7

        cpu.tick(); // RET			; $00a7

        cpu.tick(); // CALL $0096		; $002b

        while (cpu.rf.PC.read() != 0x00a3) {
            cpu.tick();
        }

        // Finish loading logo data from cart into video RAM
        while (cpu.rf.PC.read() != 0x0034) {
            cpu.tick();
        }

        cpu.tick(); // LD DE,$00d8		; $0034  Load 8 additional bytes into Video RAM (the tile for ®)
        cpu.tick(); // LD B,$08		; $0037
        // Addr_0039:
        cpu.tick(); // LD A,(DE)		; $0039
        cpu.tick(); // INC DE		; $003a
        cpu.tick(); // LD (HL+),A		; $003b
        cpu.tick(); // INC HL		; $003c
        cpu.tick(); // DEC B			; $003d

        // finish loading in that tile
        while (cpu.rf.PC.read() != 0x0040) {
            cpu.tick();
        }

        // setup background tilemap

        cpu.tick(); // LD A,$19		; $0040  Setup background tilemap
        cpu.tick(); // LD ($9910),A	; $0042
        cpu.tick(); // LD HL,$992f		; $0045
        // Addr_0048:
        cpu.tick(); // LD C,$0c		; $0048
        // Addr_004A:
        cpu.tick(); // DEC A			; $004a
        cpu.tick(); // JR Z, Addr_0055	; $004b
        cpu.tick(); // LD (HL-),A		; $004d
        cpu.tick(); // DEC C			; $004e
        cpu.tick(); // JR NZ, Addr_004A	; $004f
        cpu.tick(); // LD L,$0f		; $0051
        cpu.tick(); // JR Addr_0048	; $0053

        while(cpu.rf.PC.read() != 0x0055) {
            cpu.tick();
        }

//        ; === Scroll logo on screen, and play logo sound===

//                Addr_0055:
        cpu.tick(); // LD H,A		; $0055  Initialize scroll count, H=0
        cpu.tick(); // LD A,$64		; $0056
        cpu.tick(); // LD D,A		; $0058  set loop count, D=$64
        cpu.tick(); // LD ($FF00+$42),A	; $0059  Set vertical scroll register
        cpu.tick(); // LD A,$91		; $005b
        cpu.tick(); // LD ($FF00+$40),A	; $005d  Turn on LCD, showing Background
        cpu.tick(); // INC B			; $005f  Set B=1

//        Addr_0060:
        cpu.tick(); // LD E,$02		; $0060
//        Addr_0062:
        cpu.tick(); // LD C,$0c		; $0062
//        Addr_0064:
        cpu.tick(); // LD A,($FF00+$44)	; $0064  wait for screen frame

        cpu.tick();

        cpu.tick();

        cpu.tick();

        cpu.tick();
    }

    @Test
    public void continuousExecuteTest() throws IOException {
        CPU cpu = new CPU();

        cpu.mmu.loadROM(new File("roms/tetris.gb").toPath());

        while (cpu.rf.PC.read() != 0x00fe) {
            cpu.tick();
        }
    }

     */

    @Test
    public void checkROMTest() throws IOException {
//        GPU gpu = new GPU();
//
//        APU apu = new APU();
//
//        MMU mmu = new MMU(gpu, apu);
//
//        CPU cpu = new CPU(mmu);
//
//        cpu.mmu.loadROM(new File("roms/tetris.gb").toPath());
//
//        for(int i = 0; i < 100; i++) {
//            System.out.println(Integer.toHexString(i) + " : " + cpu.mmu.ROMContent[i]);
//        }
    }
}