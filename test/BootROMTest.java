import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BootROMTest {
    @Test
    public void bootROMTest() throws IOException {
        
        GameBoy gb = new GameBoy();

        gb.loadROM("roms/02-interrupts.gb");

        // $0000
        gb.step(); // LD SP,$fffe

        // $0003
        gb.step(); // XOR A

        // $0004
        gb.step(); // LD HL,$9fff

        // Addr_0007: $0007
        gb.step(); // LD (HL-), A

        // $0008
        gb.step(); // BIT 7, H

        // $000a
        gb.step(); // JR NZ, Addr_0007

        // $000c
        gb.step(); // LD HL, $ff26

        // $000f
        gb.step(); // LD C,$11

        // $0011
        gb.step(); // LD A,$80

        // $0013
        gb.step(); // LD (HL-),A
//        assertMem(cpu.mmu, 0xff26, 0x80);

        // $0014
//        assertMem(cpu.mmu, 0xff11, 0x0);
        gb.step(); // LD ($FF00+C),A

        // $0015
        gb.step(); // INC C

        // $0016
        gb.step(); // LD A,$f3

        // $0018
        gb.step(); // LD ($FF00+C),A

        // $0019
        gb.step(); // LD (HL-),A

        // $001a
        gb.step(); // LD A,$77

        // $001c
        gb.step(); // LD (HL),A		;

        gb.step(); // LD A,$fc		; $001d  Setup BG palette
        gb.step(); // LD ($FF00+$47),A	; $001f

        gb.step(); // LD DE,$0104		; $0021  Convert and load logo data from cart into Video RAM
        gb.step(); // LD HL,$8010		; $0024
        // Addr_0027:
        gb.step(); // LD A,(DE)		; $0027
        gb.step(); // CALL $0095		; $0028

//        ; ==== Graphic routine ====
        gb.step(); // LD C,A		; $0095  "Double up" all the bits of the graphics data
        gb.step(); // LD B,$04		; $0096     and store in Video RAM

//        Addr_0098:
        gb.step(); // PUSH BC		; $0098
        gb.step(); // RL C			; $0099
        gb.step(); // RLA			; $009b
        gb.step(); // POP BC		; $009c
        gb.step(); // RL C			; $009d
        gb.step(); // RLA			; $009f
        gb.step(); // DEC B			; $00a0

        gb.step(); // JR NZ Addr_0098 ; 00a1

        while (gb.readPC() != 0x00a3) {
            gb.step();
        }

        gb.step(); // LD (HL+),A		; $00a3
        gb.step(); // INC HL		; $00a4
        gb.step(); // LD (HL+),A		; $00a5
        gb.step(); // INC HL		; $00a6
        gb.step(); // RET			; $00a7

        gb.step(); // RET			; $00a7

        gb.step(); // CALL $0096		; $002b

        while (gb.readPC() != 0x00a3) {
            gb.step();
        }

        // Finish loading logo data from cart into video RAM
        while (gb.readPC() != 0x0034) {
            gb.step();
        }

        gb.step(); // LD DE,$00d8		; $0034  Load 8 additional bytes into Video RAM (the tile for ®)
        gb.step(); // LD B,$08		; $0037
        // Addr_0039:
        gb.step(); // LD A,(DE)		; $0039
        gb.step(); // INC DE		; $003a
        gb.step(); // LD (HL+),A		; $003b
        gb.step(); // INC HL		; $003c
        gb.step(); // DEC B			; $003d

        // finish loading in that tile
        while (gb.readPC() != 0x0040) {
            gb.step();
        }

        // setup background tilemap

        gb.step(); // LD A,$19		; $0040  Setup background tilemap
        gb.step(); // LD ($9910),A	; $0042
        gb.step(); // LD HL,$992f		; $0045
        // Addr_0048:
        gb.step(); // LD C,$0c		; $0048
        // Addr_004A:
        gb.step(); // DEC A			; $004a
        gb.step(); // JR Z, Addr_0055	; $004b
        gb.step(); // LD (HL-),A		; $004d
        gb.step(); // DEC C			; $004e
        gb.step(); // JR NZ, Addr_004A	; $004f
        gb.step(); // LD L,$0f		; $0051
        gb.step(); // JR Addr_0048	; $0053

        while(gb.readPC() != 0x0055) {
            gb.step();
        }

//        ; === Scroll logo on screen, and play logo sound===

//                Addr_0055:
        gb.step(); // LD H,A		; $0055  Initialize scroll count, H=0
        gb.step(); // LD A,$64		; $0056
        gb.step(); // LD D,A		; $0058  set loop count, D=$64
        gb.step(); // LD ($FF00+$42),A	; $0059  Set vertical scroll register
        gb.step(); // LD A,$91		; $005b
        gb.step(); // LD ($FF00+$40),A	; $005d  Turn on LCD, showing Background
        gb.step(); // INC B			; $005f  Set B=1

//        Addr_0060:
        gb.step(); // LD E,$02		; $0060
//        Addr_0062:
        gb.step(); // LD C,$0c		; $0062
//        Addr_0064:
        gb.step(); // LD A,($FF00+$44)	; $0064  wait for screen frame

        gb.step();

        gb.step();

        gb.step();

        gb.step();

        while(gb.readPC() != 0x006a) {
            gb.step();
        }

        // DEC C			; $006a
        gb.step();

        // JR NZ, Addr_0064	; $006b
        while(gb.readPC() != 0x006d) {
            gb.step();
        }

        // DEC E			; $006d
        gb.step();

        // JR NZ, Addr_0062	; $006e
        while(gb.readPC() != 0x0070) {
            gb.step();
        }

        // LD C,$13		; $0070
        gb.step();

        gb.step(); // INC H			; $0072  increment scroll count
        gb.step(); // LD A,H		; $0073
        gb.step(); // LD E,$83		; $0074
        gb.step(); // CP $62		; $0076  $62 counts in, play sound #1

        // // JR Z, Addr_0080	; $0078
        while(gb.readPC() != 0x007a) {
            gb.step();
        }

        gb.step(); // LD E,$c1		; $007a

        gb.step(); // CP $64		; $007c
        gb.step(); // JR NZ, Addr_0086	; $007e  $64 counts in, play sound #2

        gb.step(); // LD A,($FF00+$42)	; $0086
        gb.step(); // SUB B			; $0088
        gb.step(); // LD ($FF00+$42),A	; $0089  scroll logo up if B=1
        gb.step(); // DEC D			; $008b

//         JR NZ, Addr_0060	; $008c
        while(gb.readPC() != 0x008e) {
            gb.step();
        }

        gb.step(); // DEC B			; $008e  set B=0 first time

        gb.step(); // JR NZ, Addr_00E0	; $008f    ... next time, cause jump to "Nintendo Logo check"

        gb.step(); // LD D,$20		; $0091  use scrolling loop to pause

//        JR Addr_0060	; $0093
        gb.step();

        gb.step();

        while(gb.readPC() != 0x008e) {
            gb.step();
        }

//        cpu.DEBUG = true;
        gb.step(); // DEC B			; $008e [SECOND TIME]

//        THIS IS THE NEXT TIME
        gb.step(); // JR NZ, Addr_00E0	; $008f    ... next time, cause jump to "Nintendo Logo check"


//        Addr_00E0:
        gb.step(); // LD HL,$0104		; $00e0	; point HL to Nintendo logo in cart
        gb.step(); // LD DE,$00a8		; $00e3	; point DE to Nintendo logo in DMG rom

        gb.step(); // LD A,(DE)		; $00e6
        gb.step(); // INC DE		; $00e7
        gb.step(); // CP (HL)		; $00e8	;compare logo data in cart to DMG rom
        gb.step(); // JR NZ,$fe		; $00e9	;if not a match, lock up here
        gb.step(); // INC HL		; $00eb
        gb.step(); // LD A,L		; $00ec
        gb.step(); // CP $34		; $00ed	;do this for $30 bytes
        gb.step(); // JR NZ, Addr_00E6	; $00ef

        while(gb.readPC() != 0x00f1) {
            gb.step();
        }
//        cpu.DEBUG = true;
        gb.onDebug();
        gb.step(); // LD B,$19		; $00f1
        gb.step(); // LD A,B		; $00f3

//        Addr_00F4:
        gb.step(); // ADD (HL)		; $00f4
        gb.step(); // INC HL		; $00f5
        gb.step(); // DEC B			; $00f6
        gb.step(); // JR NZ, Addr_00F4	; $00f7

        while(gb.readPC() != 0x00f9) {
            gb.step();
        }

        gb.step(); // ADD (HL)		; $00f9

        gb.step(); // JR NZ,$fe		; $00fa	; if $19 + bytes from $0134-$014D  don't add to $00 ;  ... lock up

        gb.step(); // LD A,$01		; $00fc
        gb.step(); // LD ($FF00+$50),A	; $00fe	;turn off DMG rom

//        for(int i = 0; i < gpu.VRAM.length; i++) {
//            System.out.println(Integer.toHexString(0x8000 + i) + " : " + gpu.VRAM[i]);
//        }

        // TODO: Check that BIOS finishes automatically
//        cpu.mmu.finished_bios = true;
//        System.out.println(Integer.toHexString(cpu.rf.PC.read()));

//        cpu.DEBUG = true;
        gb.onDebug();

        gb.step(); // nop

        gb.step(); // jp main

        gb.step(); // jp 0x020c

        gb.step(); // xor

        gb.step(); // ld

        gb.step(); // ld

        gb.step(); // ld

        gb.step(); // ld



//        long currTime = System.currentTimeMillis();
//        while(System.currentTimeMillis() < currTime + 100000) {
//            gb.step();
//        }
    }
}
