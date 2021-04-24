import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MMU {
    int[] BiosContent;
    int[] ROMContent;
    int[] WorkingRAMContent;
    int[] ExternalRAMContent;
    int[] ZeroPageRAMContent;

    boolean finished_bios;    

    public MMU() {
        // BiosContent = new int[0x00FF - 0x0000];
        ROMContent = new int[0x8000 - 0x0000];
        WorkingRAMContent = new int[0xFE00 - 0xC000];
        ExternalRAMContent = new int[0xC000 - 0xA000];
        ZeroPageRAMContent = new int[0x10000 - 0xFF80];

        init_bios();

        finished_bios = false;
    }

    public int readByte(int address) {
        if(!finished_bios && address < 0x00FF) {
            return BiosContent[address];
        }
        
        if(!finished_bios && address >= 0x00FF){
            finished_bios = true;
        }
        
        switch(address & 0xF000) {
            // ROM Bank 0
            case 0x0000:
            case 0x1000:
            case 0x2000:
            case 0x3000:
                return ROMContent[address];
            
            // ROM Bank 1
            case 0x4000:
            case 0x5000:
            case 0x6000:
            case 0x7000:
                return ROMContent[address];

            // VRAM
            case 0x8000:
            case 0x9000:
//                return GPU.vram[address - 0x8000];

            // External RAM
            case 0xA000:
            case 0xB000:
                return ExternalRAMContent[address - 0xA000];

            // Working RAM
            case 0xC000:
            case 0xD000:
                return WorkingRAMContent[address - 0xC000];

            // Working RAM Shadow
            case 0xE000:
                return WorkingRAMContent[address - 0xC000];

            case 0xF000:
                switch(address & 0x0F00) { // address by the next 4 bits
                    // Working RAM shadow
                    case 0x000: case 0x100: case 0x200: case 0x300:
                    case 0x400: case 0x500: case 0x600: case 0x700:
                    case 0x800: case 0x900: case 0xA00: case 0xB00:
                    case 0xC00: case 0xD00:
                        return WorkingRAMContent[address - 0xC000];

                    // Graphics Memory
                    // OAM for sprites
                    case 0xE00:
                        if(address < 0xFEA0){
//                            return GPU.oam[address - 0xFE00];
                        }
                        else{
                            return 0;
                        }
                    
                    // zero page RAM and I/O
                    case 0xF00:
                        if(address >= 0xFF80){ 
                            return ZeroPageRAMContent[address-0xFF00];
                        }
                        else{ // I/O Mapping
                            return 0;
                        }
                }
        }
        
        return 0;
    }

    public int readWord(int address) {
        // Little endian
        return readByte(address) | (readByte(address + 1) << 8);
    }

    public void loadROM(Path path) throws IOException {
        byte[] ROMInput = Files.readAllBytes(path);
        for(int i = 0; i < ROMInput.length; i++) {
            ROMContent[i] = ROMInput[i];
        }
    }

    public void writeByte(int address, int value){
        switch(address & 0xF000){
            case 0x1000:
            case 0x2000:
            case 0x3000:
            case 0x4000:
            case 0x5000:
            case 0x6000:
            case 0x7000:
                ROMContent[address] = value;
            
            case 0x8000:
            case 0x9000:
                // GPU.vram[address - 0x8000] = value;

            case 0xA000:
            case 0xB000:
                ExternalRAMContent[address - 0xA000] = value;
            
            case 0xC000:
            case 0xD000:
            case 0xE000:
                ExternalRAMContent[address - 0xC000] = value;

            case 0xF000:
            switch(address & 0x0F00) { // address by the next 4 bits
                // Working RAM shadow
                case 0x000: case 0x100: case 0x200: case 0x300:
                case 0x400: case 0x500: case 0x600: case 0x700:
                case 0x800: case 0x900: case 0xA00: case 0xB00:
                case 0xC00: case 0xD00:
                    WorkingRAMContent[address - 0xC000] = value;

                // Graphics Memory
                // OAM for sprites
                case 0xE00:
                    if(address < 0xFEA0){
//                      GPU.oam[address - 0xFE00] = value;
                    }
                    else{
                        // everything else in OAM should remain as 0 even when written into
                    }
                
                // zero page RAM and I/O
                case 0xF00:
                    if(address >= 0xFF80){ 
                        ZeroPageRAMContent[address-0xFF00] = value;
                    }
                    else{ // I/O Mapping
                        // not set up yet
                    }
            }
        
        }
    }

    public void writeWord(int address, int value){
        writeByte(value & 0x00FF, address); // write lower 8 bits into smaller memory address
        writeByte(value & 0xFF00, address+1); // write upper 8 bits into larger memory address
    }

    public void init_bios() {
        BiosContent = new int[] {
            0x31, 0xFE, 0xFF, 0xAF, 0x21, 0xFF, 0x9F, 0x32, 0xCB, 0x7C, 0x20, 0xFB, 0x21, 0x26, 0xFF, 0x0E,
            0x11, 0x3E, 0x80, 0x32, 0xE2, 0x0C, 0x3E, 0xF3, 0xE2, 0x32, 0x3E, 0x77, 0x77, 0x3E, 0xFC, 0xE0,
            0x47, 0x11, 0x04, 0x01, 0x21, 0x10, 0x80, 0x1A, 0xCD, 0x95, 0x00, 0xCD, 0x96, 0x00, 0x13, 0x7B,
            0xFE, 0x34, 0x20, 0xF3, 0x11, 0xD8, 0x00, 0x06, 0x08, 0x1A, 0x13, 0x22, 0x23, 0x05, 0x20, 0xF9,
            0x3E, 0x19, 0xEA, 0x10, 0x99, 0x21, 0x2F, 0x99, 0x0E, 0x0C, 0x3D, 0x28, 0x08, 0x32, 0x0D, 0x20,
            0xF9, 0x2E, 0x0F, 0x18, 0xF3, 0x67, 0x3E, 0x64, 0x57, 0xE0, 0x42, 0x3E, 0x91, 0xE0, 0x40, 0x04,
            0x1E, 0x02, 0x0E, 0x0C, 0xF0, 0x44, 0xFE, 0x90, 0x20, 0xFA, 0x0D, 0x20, 0xF7, 0x1D, 0x20, 0xF2,
            0x0E, 0x13, 0x24, 0x7C, 0x1E, 0x83, 0xFE, 0x62, 0x28, 0x06, 0x1E, 0xC1, 0xFE, 0x64, 0x20, 0x06,
            0x7B, 0xE2, 0x0C, 0x3E, 0x87, 0xF2, 0xF0, 0x42, 0x90, 0xE0, 0x42, 0x15, 0x20, 0xD2, 0x05, 0x20,
            0x4F, 0x16, 0x20, 0x18, 0xCB, 0x4F, 0x06, 0x04, 0xC5, 0xCB, 0x11, 0x17, 0xC1, 0xCB, 0x11, 0x17,
            0x05, 0x20, 0xF5, 0x22, 0x23, 0x22, 0x23, 0xC9, 0xCE, 0xED, 0x66, 0x66, 0xCC, 0x0D, 0x00, 0x0B,
            0x03, 0x73, 0x00, 0x83, 0x00, 0x0C, 0x00, 0x0D, 0x00, 0x08, 0x11, 0x1F, 0x88, 0x89, 0x00, 0x0E,
            0xDC, 0xCC, 0x6E, 0xE6, 0xDD, 0xDD, 0xD9, 0x99, 0xBB, 0xBB, 0x67, 0x63, 0x6E, 0x0E, 0xEC, 0xCC,
            0xDD, 0xDC, 0x99, 0x9F, 0xBB, 0xB9, 0x33, 0x3E, 0x3c, 0x42, 0xB9, 0xA5, 0xB9, 0xA5, 0x42, 0x4C,
            0x21, 0x04, 0x01, 0x11, 0xA8, 0x00, 0x1A, 0x13, 0xBE, 0x20, 0xFE, 0x23, 0x7D, 0xFE, 0x34, 0x20,
            0xF5, 0x06, 0x19, 0x78, 0x86, 0x23, 0x05, 0x20, 0xFB, 0x86, 0x20, 0xFE, 0x3E, 0x01, 0xE0, 0x50 };
    }
}
