import java.awt.image.BufferedImage;
import java.awt.Color;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

public class GPU {
    int line;
    Clock modeclk;

    Register SCX;
    Register SCY;
    Register STAT;

    Register WX;
    Register WY;

    int[] VRAM;
    int[] OAM;
    int[] regs;

    Mode mode;

    boolean lcd_on;
    boolean bg_on;
    boolean sprite_on;

    int background_map_base;
    int background_tile_base;
    int palette[];
    int sprite_size;
    
    boolean window_on;
    int window_mapbase;

    final int PPU_SCALE = 10;
    // int num_tiles_per_row = 20;

    enum Mode {
        HBLANK, VBLANK, SCANLINE_OAM, SCANLINE_VRAM
    }

    int tileset [][][];
    Display game;

    Sprite sprites[] = new Sprite[40];
    int sprite_palette1 [];
    int sprite_palette2 [];

    PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("PPU-Test.out")));

    boolean OUT_DEBUG = true;

    MMU mmu;

    public GPU() throws IOException {
        line = 0;
        mode = Mode.SCANLINE_OAM;
        modeclk = new Clock();

        SCX = new Register8Bit();
        SCY = new Register8Bit();
        STAT = new Register8Bit();
        WX = new Register8Bit();
        WY = new Register8Bit();

        VRAM = new int[0xA000 - 0x8000];
        OAM = new int[0xFF00 - 0xFE00];
        regs = new int[0xFF80 - 0xFF40];

        tileset = new int[512][8][8];
        // palette = new int[] {0xFFFFFF, 0xC0C0C0, 0x606060, 0x000000};
        palette = new int[] {0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF};
        sprite_palette1 = new int[] {0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF};
        sprite_palette2 = new int[] {0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF};
        background_map_base = 0x1800;
        background_tile_base = 0x0800;
        window_mapbase = 0x1800;

        for(int i=0; i<40; i++){
            sprites[i] = new Sprite(i);
        }

        //this.mmu = mmu;
        
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
            if (modeclk.t >= 204 * PPU_SCALE) {
                // modeclk.reset();
                line++;

                if (line == 143) { // traverse back to top of frame buffer
                    mode = Mode.VBLANK;
                    // place entire image on screen
                    game.putImageData();
                    MMU.interrupt_flags.write(MMU.interrupt_flags.read() | 0x01);
                    requestStatInterrupt(4); // request VBLANK stat interrupt
            } else {
                    mode = Mode.SCANLINE_OAM;
                }
                modeclk.reset();
            }
            requestStatInterrupt(5); // request OAM stat interrupt
            requestCoincidenceInterrupt();
            break;
        case VBLANK:
            if (modeclk.t >= 456 * PPU_SCALE) {
                modeclk.reset();
                line++;

                if (line > 153) {
                    mode = Mode.SCANLINE_OAM;
                    line = 0;
                    requestStatInterrupt(5); // request OAM stat interrupt
                }
            }
            requestCoincidenceInterrupt();
            break;
        case SCANLINE_OAM:
            if (modeclk.t >= 80 * PPU_SCALE) {
                // switch to vram scanline mode
                modeclk.reset();
                mode = Mode.SCANLINE_VRAM;
            }
            break;
        case SCANLINE_VRAM:
            if (modeclk.t >= 172 * PPU_SCALE) {
                // switch to hblank mode
                modeclk.reset();
                mode = Mode.HBLANK;
                requestStatInterrupt(3); // request HBLANK stat interrupt
                // we are done with a full line, so we write to frame buffer
                
                if(lcd_on && bg_on){
                    renderscan();
                }
            }
            break;

        }
//        System.out.println("line " + line);
    }

    public void requestStatInterrupt(int stat_bit) { // LCDC interrupt
        if((STAT.read() & (1 << stat_bit)) != 0) {
            MMU.interrupt_flags.write(MMU.interrupt_flags.read() | 0x02);
        }
    }

    public void requestCoincidenceInterrupt() {
        if(regs[0x05] == line) {
            requestStatInterrupt(6); // request LY=LYC STAT Interrupt
        }
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
            case 0: // LCDC Reg
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
                /* if((value & 0x40) == 0x40) {
                    System.out.println("window select");
                }
                if((value & 0x20) == 0x20) {
                    System.out.println("window enable");
                } */
                bg_on = ((value & 0x01) == 0x01);
                sprite_on = ((value & 0x02) == 0x02);
                sprite_size = ((value & 0x04) == 0x04) ? 1 : 0;
                background_map_base = ((value & 0x08) == 0x08) ? 0x1C00 : 0x1800;
                background_tile_base = ((value & 0x10) == 0x10) ? 0x0000 : 0x0800;
                window_on = ((value & 0x20) == 0x20);
                window_mapbase = ((value & 0x40) == 0x40) ? 0x1C00 : 0x1800;
                lcd_on = ((value & 0x80) == 0x80);
                
                return;

            case 1: // STAT Reg
                STAT.write(STAT.read() | (value & 0xF8)); // last 3 bits are read-only
                return;
            case 2:
//                System.out.println("SCY");
                SCY.write(value);
                return;
            case 3:
                SCX.write(value);
//                System.out.println("Writing " + value + " into SCX");
                return;
            case 5: 
                regs[0x05] = value;
            case 6:
                int update;
                for(int i = 0; i < 160; i++){
                    update = mmu.readByte((value << 8) + i);
                    OAM[i] = update;
                    buildSpriteData(i, update);
                }
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
            
//                System.out.println("test 7");
//                System.out.println(Integer.toHexString(value));
                return;
            case 8:
//                System.out.println("test 8");
                for(int i = 0; i < 4; i++) {
                    switch((value >> (i*2)) & 3) {
                        case 0:
                            sprite_palette1[i] = 0xFFFFFF;
                            break;
                        case 1:
                            sprite_palette1[i] = 0xC0C0C0;
                            break;
                        case 2:
                            sprite_palette1[i] = 0x606060;
                            break;
                        case 3:
                            sprite_palette1[i] = 0x000000;
                            break;
                    }
                }
                return;
            case 9:
//                System.out.println("test 9");
                for(int i = 0; i < 4; i++) {
                    switch((value >> (i*2)) & 3) {
                        case 0:
                            sprite_palette2[i] = 0xFFFFFF;
                            break;
                        case 1:
                            sprite_palette2[i] = 0xC0C0C0;
                            break;
                        case 2:
                            sprite_palette2[i] = 0x606060;
                            break;
                        case 3:
                            sprite_palette2[i] = 0x000000;
                            break;
                    }
                }
                return;

            case 0xA:
                WY.write(value);
                return;

            case 0xB:
                WX.write(value);
                return;

        }
    }

    public int readByte(int address) {
        int gaddr = address - 0xFF40;
//        System.out.println("gpu rb");
//        System.out.println(line);
        switch (gaddr) {
            case 0:
//                System.out.println(Integer.toHexString(regs[gaddr]));
                return regs[gaddr];
            case 2:
                return SCY.read();
                
            case 4: // LY
//                System.out.println("0xFF44");
//                System.out.println(line);
                return line;
            case 3:
                return SCX.read();
            case 1: // read from STAT
                return STAT.read() | 0x80 | mode.ordinal() | (line == regs[0x05] ? 0x04 : 0);
            case 5: // LYC
                return regs[0x05];
            case 0xA:
                return WY.read();
            case 0xB:
                return WX.read();
            default:
                return regs[gaddr];
        }
        // return regs[gaddr];
    }

    public void buildSpriteData(int address, int value){
        int obj_index = address / 4;

        //System.out.println("address: " + Integer.toHexString(address));
        //System.out.println("value: " + Integer.toHexString(value));

        if(obj_index < 40){
            switch(address & 0x3){
                case 0:
                    sprites[obj_index].y = value -16;
                    break;

                case 1:
                    sprites[obj_index].x = value -8;
                    break;

                case 2:
                    if(sprite_size == 1) sprites[obj_index].tile = value & 0xFE; // floor to mod 2
                    else sprites[obj_index].tile = value;
                    break;

                case 3:
                    sprites[obj_index].palette = ((value & 0x10) == 0x10) ? sprite_palette2 : sprite_palette1;
                    sprites[obj_index].xflip   = ((value & 0x20) == 0x20);
                    sprites[obj_index].yflip   = ((value & 0x40) == 0x40);
                    sprites[obj_index].priority    = ((value & 0x80) == 0x80);
                    break;
            }

            // System.out.println("palette: " + Arrays.toString(sprites[obj_index].palette));
        }

    }

    public void renderscan() {
        // System.out.println("inside renderscan");

        // int map_offset = (background_map) ? 0x1C00 : 0x1800;
        int cache[] = new int[160];

        if(bg_on) {
            int map_offset = background_map_base + ((((line + SCY.read()) & 255) >> 3) << 5);

            int line_offset = SCX.read() >> 3;

            int y = (line + SCY.read()) & 7;

            int x = SCX.read() & 7;

            // int tile = map_offset * (num_tiles_per_row) + line_offset;
            int tile = VRAM[map_offset + line_offset];

            if (background_tile_base == 0x0800 && tile < 128) {
                tile += 256;
            }

            int canvas_offset = (line) * 160;

            for (int i = 0; i < 160; i++) {
//            System.out.println(tile);
//            System.out.println(tileset[tile][y][x]);
                int color = palette[tileset[tile][y][x]];

                game.scanline(canvas_offset, color);

                if (OUT_DEBUG) {
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

                cache[i] = tileset[tile][y][x];

                for (int k = 0; k < 8; k++) {
                    for (int m = 0; m < 8; m++) {
                        //out.print(tileset[tile][k][m]);
                    }
                    //out.println();
                }

                canvas_offset++;

                x++;
                if (x == 8) {
                    x = 0;
                    line_offset = (line_offset + 1) & 31;
                    tile = VRAM[map_offset + line_offset];
                    if (background_tile_base == 0x0800 && tile < 128) {
                        tile += 256;
                    }
                }
            }
        }

        if(window_on && line >= WY.read() && line <= WY.read() + 256){
//            System.out.println("Rendering window at line " + line + ". WY is " + WY.read() + ". WX is " + WX.read());
            int window_map_offset = window_mapbase + ((((line + SCY.read() - WY.read()) & 255) >> 3) << 5);

            int line_offset = WX.read() >= 7 ? (WX.read() - 7 + SCX.read()) >> 3:
                                                SCX.read() >> 3;

            int y = (line + SCY.read()) & 7;

            int x = WX.read() >= 7 ? (WX.read() - 7 + SCX.read()) & 7:
                                        SCX.read() & 7;

            int tile = VRAM[window_map_offset + line_offset];

//            System.out.println("VRAM  address" + Integer.toHexString(window_map_offset + line_offset));
//            System.out.println("tile: " + tile);

            if (background_tile_base == 0x0800 && tile < 128) {
                tile += 256;
            }

            int canvas_offset = (line) * 160 + (WX.read() >= 7 ? WX.read() - 7 : 0);

            for (int i = (WX.read() >= 7 ? WX.read() - 7 : 0); i < 160; i++) {
                int color = palette[tileset[tile][y][x]];

                game.scanline(canvas_offset, color);

                cache[i] = tileset[tile][y][x];

                canvas_offset++;

                x++;
                if (x == 8) {
                    x = 0;
                    line_offset = (line_offset + 1) & 31;
                    tile = VRAM[window_map_offset + line_offset];
                    if (background_tile_base == 0x0800 && tile < 128) {
                        tile += 256;
                    }
                }
            }

        }

        if(sprite_on){

            if(sprite_size == 1) {
                for(int i=0; i< 40; i++){

                }
            }

            else {

                int tilerow[];
                int color;
                for(int i=0; i< 40; i++){
                    Sprite curr = sprites[i];

                    if(curr.y <= line && curr.y + 8 > line){

                        int sprite_canvas_offset = line*160 + curr.x;

                        if(curr.yflip){
                            tilerow = tileset[curr.tile][7 - (line - curr.y)];
                        }
                        else{
                            tilerow= tileset[curr.tile][line - curr.y];
                        }

                        for(int x=0; x< 8; x++){
                            //if(inRange(curr.x+x) && tilerow[curr.xflip ? (7-x) : x] > 0 && (curr.priority || cache[curr.x + x] != 3)){
                            if(inRange(curr.x+x) && tilerow[curr.xflip ? (7-x) : x] > 0 ){
                                color = curr.palette[tilerow[curr.xflip ? (7-x) : x]];
                                // System.out.println("Object " + curr.index + )

                                game.scanline(sprite_canvas_offset, color);
                            }
                            sprite_canvas_offset++;
                        }

                    }

                }
            }
        }

    }

    public boolean inRange(int x){
        return x>=0 && x < 160;
    }
}
