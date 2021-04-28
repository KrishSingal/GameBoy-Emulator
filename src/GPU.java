import java.awt.image.BufferedImage;
import java.awt.Color;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.StringTokenizer;
import javax.swing.*;

public class GPU {
    int line;
    Clock modeclk;

    Register SCX;
    Register SCY;

    int[] VRAM;
    int[] OAM;
    int[] regs;

    Mode mode;

    boolean lcd_on;
    boolean bg_on;

    int background_map_base;
    int background_tile_base;
    int palette[];
    // int num_tiles_per_row = 20;

    enum Mode {
        HBLANK, VBLANK, SCANLINE_OAM, SCANLINE_VRAM
    }

    int tileset [][][];
    Display game;

    PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("PPU-Test.out")));

    boolean OUT_DEBUG = false;


    public GPU() throws IOException {
        line = 0;
        mode = Mode.SCANLINE_OAM;
        modeclk = new Clock();

        SCX = new Register8Bit();
        SCY = new Register8Bit();

        VRAM = new int[0xA000 - 0x8000];
        OAM = new int[0xFF00 - 0xFE00];
        regs = new int[0xFF80 - 0xFF40];

        tileset = new int[512][8][8];
        // palette = new int[] {0xFFFFFF, 0xC0C0C0, 0x606060, 0x000000};
        palette = new int[] {0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF};
        background_map_base = 0x1800;
        background_tile_base = 0x0800;
        
        game = new Display();
        game.frame.setResizable(false); // should be first thing
        game.frame.setTitle("GameBoy");
        game.frame.add(game); // adds canvas display-able object
        game.frame.pack(); // makes size same as frame?
        game.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // ends process when window is closed
        game.frame.setLocationRelativeTo(null);
        game.frame.setVisible(true);

        game.start();
        
    }

    public void step() {
        modeclk.tick();

//        System.out.println("modeclk.t " + modeclk.t);
//        System.out.println("line " + Integer.toHexString(line));
//        System.out.println("mode " + mode.toString());

        switch (mode) {
        case HBLANK:
            if (modeclk.t >= 204) {
                // modeclk.reset();
                line++;

                if (line == 143) { // traverse back to top of frame buffer
                    mode = Mode.VBLANK;
                    // place entire image on screen
                    game.putImageData();
                } else {
                    mode = Mode.SCANLINE_OAM;
                }
                modeclk.reset();
            }
            break;
        case VBLANK:
            if (modeclk.t >= 456) {
                modeclk.reset();
                line++;

                if (line > 153) {
                    mode = Mode.SCANLINE_OAM;
                    line = 0;
                }
            }
            break;
        case SCANLINE_OAM:
            if (modeclk.t >= 80) {
                // switch to vram scanline mode
                modeclk.reset();
                mode = Mode.SCANLINE_VRAM;
            }
            break;
        case SCANLINE_VRAM:
            if (modeclk.t >= 172) {
                // switch to hblank mode
                modeclk.reset();
                mode = Mode.HBLANK;

                // we are done with a full line, so we write to frame buffer
                
                if(lcd_on && bg_on){
                    renderscan();
                }
            }
            break;

        }
//        System.out.println("line " + line);
    }

    public void reset(){
        for(int i=0; i<384; i++){
            for(int j=0; j<8; j++){
                for(int k=0; k<8; k++){
                    tileset[i][j][k] = 0;
                }
            }
        }
    }

    public void updateTile(int address, int value){
        /*address -= 0x8000;
        address -= address % 2;*/

        address &= 0x1FFE;

        int tile_index = (address >> 4) & 511;
        int row = (address >> 1) & 7;

        // System.out.println("address: " + address + " updating tile " + tile_index + " with value " + value);

        int bx;
        for(int x = 0; x<8; x++){
            bx = 1 << (7 - x);

            tileset[tile_index][row][x] = ((VRAM[address] & bx) > 0) ? 1 : 0;
            tileset[tile_index][row][x] += ((VRAM[address+1] & bx) > 0) ? 2: 0;
        }
    }

    public void writeByte(int address, int value){
        int gaddr = address - 0xFF40;
        regs[gaddr] = value;
        switch(gaddr) {
            case 0:
            /*
                Bit 7 - LCD Display Enable             (0=Off, 1=On)
                Bit 6 - Window Tile Map Display Select (0=9800-9BFF, 1=9C00-9FFF)
                Bit 5 - Window Display Enable          (0=Off, 1=On)
                Bit 4 - BG & Window Tile Data Select   (0=8800-97FF, 1=8000-8FFF)
                Bit 3 - BG Tile Map Display Select     (0=9800-9BFF, 1=9C00-9FFF)
                Bit 2 - OBJ (Sprite) Size              (0=8x8, 1=8x16)
                Bit 1 - OBJ (Sprite) Display Enable    (0=Off, 1=On)
                Bit 0 - BG Display (for CGB see below) (0=Off, 1=On)
            */
                background_tile_base = ((value & 0x10) == 0x10) ? 0x0000 : 0x0800;
                background_map_base = ((value & 0x8) == 0x8) ? 0x1C00 : 0x1800;
                lcd_on = ((value & 0x80) == 0x80);
                bg_on = ((value & 0x01) == 0x01);
                return;
            case 2:
//                System.out.println("SCY");
                SCY.write(value);
                return;
            case 3:
                SCX.write(value);
                return;
            case 6:
                System.out.println("test 6");
                return;
            case 5:
                System.out.println("test 5");
                return;
            case 7:
                for(int i = 0; i < 4; i++) {
                    switch((value >> (i*2)) & 3) {
                        case 0:
                            palette[i] = 0xFFFFFF;
                            break;
                        case 1:
                            palette[i] = 0xC0C0C0;
                            break;
                        case 2:
                            palette[i] = 0x606060;
                            break;
                        case 3:
                            palette[i] = 0x000000;
                            break;
                    }
                }
            
                System.out.println("test 7");
                System.out.println(Integer.toHexString(value));
                return;
            case 8:
                System.out.println("test 8");
                return;
            case 9:
                System.out.println("test 9");
                return;
        }
    }

    public int readByte(int address) {
        int gaddr = address - 0xFF40;
//        System.out.println("gpu rb");
//        System.out.println(line);
        switch (gaddr) {
            case 0:
                return (background_tile_base == 0x0000 ? 0x10: 0 ) | (background_map_base == 0x1C00 ? 0x08 : 0);
            case 2:
                return SCY.read();
                
            case 4:
//                System.out.println("0xFF44");
//                System.out.println(line);
                return line;
            case 3:
                return SCX.read();
            case 5:
                System.out.println("case 5 readByte()");
                return 0;
            case 1:
                System.out.println("case 1 readByte()");
                return 0;
        }
        return regs[gaddr];
    }

    public void renderscan() {
        // System.out.println("inside renderscan");

        // int map_offset = (background_map) ? 0x1C00 : 0x1800;

        int map_offset = background_map_base + ((((line + SCY.read()) & 255) >> 3 ) << 5);

        int line_offset = SCX.read() >> 3;

        int y = (line + SCY.read()) & 7;

        int x = SCX.read() & 7;

        // int tile = map_offset * (num_tiles_per_row) + line_offset;
        int tile = VRAM[map_offset + line_offset];

        if(background_tile_base == 0x0800 && tile < 128){
            tile+=256;
        }

        int canvas_offset = (line) * 160;

        for(int i=0; i< 160; i++){
//            System.out.println(tile);
//            System.out.println(tileset[tile][y][x]);
            int color = palette[tileset[tile][y][x]];

            game.scanline(canvas_offset, color);

            if(OUT_DEBUG) {
                out.println("i = " + i);
                //out.println("map_offset = " + Integer.toHexString(map_offset));
                //out.println("line_offset = " + line_offset);
                out.println("vram access: " + Integer.toHexString(map_offset + line_offset));
                // out.println("canvas_offset = " + canvas_offset);
                //out.println("color = " + color);
                out.println("tile # = " + tile);
                // out.println("line # = " + line);
                long time = System.currentTimeMillis();
                out.println(time);
                out.println();
            }            


            for(int k=0; k< 8; k++){
                for(int m = 0; m < 8; m++){
                    //out.print(tileset[tile][k][m]);
                }
                //out.println();
            }

            canvas_offset++;

            x++;
            if(x == 8){
                x = 0;
                line_offset = (line_offset + 1) & 31;
                tile = VRAM[map_offset + line_offset];
                if(background_tile_base == 0x0800 && tile < 128){
                    tile+=256;
                }
            }
        }
    }
}
