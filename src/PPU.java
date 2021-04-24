public class PPU {
    enum Mode {
        READ_OAM,
        READ_VRAM,
        HBLANK,
        VBLANK
    }

    int ppu_clk;
    int line_num;
    Mode mode;
    boolean render = true;
    
    public PPU() {

    }

    public void step() {
        ppu_clk += CPU.t;

        switch(mode) {
            case READ_OAM:
                if(ppu_clk >= 80) {
                    ppu_clk = 0;
                    mode = Mode.READ_VRAM;
                }
                break;

            case READ_VRAM:
                if(ppu_clk >= 172) {
                    ppu_clk = 0;
                    mode = Mode.HBLANK;
                    
                    // TODO: line rendering tings
                    render_line();
                }
                break;

            case HBLANK:
                if(ppu_clk >= 204) {
                    ppu_clk = 0;
                    line_num++;

                    // last line to hblank, now we write data to the canvas
                    if(line_num == 143) {
                        mode = Mode.VBLANK;

                        // TODO: canvas rendering tings
                        render = true;
                    }
                    else {
                        mode = Mode.READ_OAM;
                    }
                }

            // end of a frame where the beam travels back to the top-left corner
            case VBLANK:

                // 456 is time it takes to complete one line
                if(ppu_clk >= 456) {
                    ppu_clk = 0;
                    line_num++;

                    if(line_num > 153) {
                        mode = Mode.READ_OAM;
                        line_num = 0;
                    }
                }
        }
    }

    public void render_line() {

    }
}
