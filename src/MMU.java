import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MMU {
    int[] BiosContent;
    int[] ROMContent;
    int[] WorkingRAMContent;
    int[] ExternalRAMContent;
    int[] ZeroPageRAMContent;
    int[] IO;

    boolean finished_bios;
    
    GPU gpu;
    APU apu;
    Timer2 timer;

    boolean TEST_ROM = true;

    static Register interrupt_enable;
    static Register interrupt_flags;

    int cartridge_type;
    int ROM_offset;
    int RAM_offset;

    public class MBC{
        int mbc_type;
        int ROM_bank;
        int RAM_bank;
        boolean RAM_on;
        int mode; // 1 - RAM    0 - ROM

        public MBC(int mbc_type, int ROM_bank, int RAM_bank, boolean RAM_on, int mode){
            this.mbc_type = mbc_type;
            this.ROM_bank = ROM_bank;
            this.RAM_bank = RAM_bank;
            this.RAM_on = RAM_on;
            this.mode = mode;
        }
    }

    MBC memory_bank_controllers [] = new MBC[2];

    public MMU(GPU gpu, APU apu, Timer2 timer) {
        // BiosContent = new int[0x00FF - 0x0000];

        //ROMContent = new int[0x8000 - 0x0000];
        WorkingRAMContent = new int[0xFE00 - 0xC000];
        //ExternalRAMContent = new int[0xF000 - 0xA000];
        ZeroPageRAMContent = new int[0x10000 - 0xFF80];
        IO = new int[0xFF80 - 0xFF00];

        init_bios();

        finished_bios = false;
        this.gpu = gpu;
        this.apu = apu;
        this.timer = timer;
        cartridge_type = 0;
        ROM_offset = 0x4000;
        RAM_offset = 0x0000;

        memory_bank_controllers[0] = new MBC(0,0,0,false,0);
        memory_bank_controllers[1] = new MBC(1,0,0,false,0);

        interrupt_enable = new Register8Bit();
        interrupt_enable.write(0);
        interrupt_flags = new Register8Bit();
        interrupt_flags.write(0);
    }

    public int readByte(int address) {
        if(!finished_bios && address <= 0x00FF) {
            return BiosContent[address];
        }
        
//        if(!finished_bios && address >= 0x00FF){
//            finished_bios = true;
//        }
        
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
                return ROMContent[ROM_offset + address - 0x4000];

            // VRAM
            case 0x8000:
            case 0x9000:
                return gpu.VRAM[address - 0x8000];

            // External RAM
            case 0xA000:
            case 0xB000:
                if(ExternalRAMContent.length == 0) return 0xFF;
                return ExternalRAMContent[RAM_offset + address - 0xA000];

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
                            return gpu.OAM[address - 0xFE00];
                        }
                        else{
                            return 0;
                        }
                    
                    // zero page RAM and I/O
                    case 0xF00:
                        if(address >= 0xFF80){
                            if(address == 0xFFFF) {
                                return interrupt_enable.read();
                            }
                            return ZeroPageRAMContent[address-0xFF80];
                        }
                        else if(address >= 0xFF40) {
//                            System.out.println("MMU GPU rb");
                            return gpu.readByte(address);
                        }
                        else if(address >= 0xFF10) {
                            return apu.readByte(address);
                        }
                        else if(address > 0xFF00){
                            if(address == 0xFF0F) return interrupt_flags.read();
                            if(address >= 0xFF04 && address <= 0xFF07) return timer.readByte(address);
                            return IO[address - 0xFF00];
                        }
                        else{ // FF00 input
//                            System.out.println("reading from input");
                            int value = (gpu.game.input).readByte(address);
//                            if((value & 0x0F) != 0x0F) System.out.println(Integer.toHexString(value));
                            return value;
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

        int ROM_size = 0;
        int RAM_size = 0;

        System.out.println(Integer.toHexString(ROMInput[0x0148]));
        System.out.println(Integer.toHexString(ROMInput[0x0149]));

        switch(ROMInput[0x0148]){
            case 0: ROM_size = 0x8000; break;
            case 1: ROM_size = 2 * 0x8000; break;
            case 2: ROM_size = 4 * 0x8000; break;
            case 3: ROM_size = 8 * 0x8000; break;
            case 4: ROM_size = 16 * 0x8000; break;
            case 5: ROM_size = 32 * 0x8000; break;
            case 6: ROM_size = 64 * 0x8000; break;
            case 7: ROM_size = 128 * 0x8000; break;
            case 8: ROM_size = 256 * 0x8000; break;
            case 52: ROM_size = 72 * 0x4000; break;
            case 53: ROM_size = 80 * 0x4000; break;
            case 54: ROM_size = 96 * 0x4000; break;
        }

        switch(ROMInput[0x0149]){
            case 0: RAM_size = 0x0000; break;
            case 1: RAM_size = 0x0800; break;
            case 2: RAM_size = 4 * 0x0800; break;
            case 3: RAM_size = 16 * 0x0800; break;
            case 4: RAM_size = 64 * 0x0800; break;
            case 5: RAM_size = 32 * 0x0800; break;
        }

        ROMContent = new int[ROM_size];
        ExternalRAMContent = new int[RAM_size];
        cartridge_type = ROMInput[0x0147];

        for(int i = 0; i < ROMInput.length; i++) {
            // writeByte(i, ROMInput[i] & 0xFF); //TODO: This should write directly into ROMContent
            ROMContent[i] = ROMInput[i] & 0xFF;
        }

        System.out.println("RAM_size " + RAM_size);
        System.out.println("ROM_size " + ROM_size);
        System.out.println("Cartridge_type " + cartridge_type);

    }

    public void writeByte(int address, int value){
        value &= 0x00FF;
//        System.out.println(Integer.toString(address, 16));

        switch(address & 0xF000){
            // writing 0x0A turns on RAM
            case 0x0000:
            case 0x1000:
                if(cartridge_type == 2 || cartridge_type == 3){
                    memory_bank_controllers[1].RAM_on = ((value & 0x0F) == 0x0A);
                }
                return;

             // writes lower 5 bits of rom bank
            case 0x2000:
            case 0x3000:
                if(cartridge_type >=1 && cartridge_type <=3 ){
                    value &= 0x1F;
                    if(value == 0) value = 1;

                    //System.out.println("Writing " + Integer.toHexString(value) + " into 0x2000 - 0x3000 ");
                    //System.out.println("Original ROM_Bank " + memory_bank_controllers[1].ROM_bank);

                    memory_bank_controllers[1].ROM_bank = (memory_bank_controllers[1].ROM_bank & 0x60) + value;
                    ROM_offset = memory_bank_controllers[1].ROM_bank * 0x4000;


                    //System.out.println("Computed ROM_offset = " + Integer.toHexString(ROM_offset));
                }
                return;

            // writes either upper 2 bits or rom bank or lower entire ram bank (depending on the mode we are in)
            case 0x4000:
            case 0x5000:
                if(cartridge_type >=1 && cartridge_type <=3 ) {
                    if(memory_bank_controllers[1].mode == 1){
                        // RAM switching mode
                        memory_bank_controllers[1].RAM_bank = value & 3;
                        RAM_offset = memory_bank_controllers[1].RAM_bank * 0x2000;
                    }
                    else{
                        if(ROMContent.length < 32 * 0x4000){
                            return;
                        }

                        //System.out.println("Writing " + Integer.toHexString(value) + " into 0x4000 - 0x5000 ");
                        //System.out.println("Original ROM_Bank " + memory_bank_controllers[1].ROM_bank);

                        memory_bank_controllers[1].ROM_bank = (memory_bank_controllers[1].ROM_bank & 0x1F) +
                                ((value & 3) << 5);
                        ROM_offset = memory_bank_controllers[1].ROM_bank * 0x4000;


                        //System.out.println("Computed ROM_offset = " + Integer.toHexString(ROM_offset));
                    }
                }
                return;
            case 0x6000:
            case 0x7000:
                if(cartridge_type >=2 && cartridge_type <=3 ) {
                    memory_bank_controllers[1].mode = ((value & 1) > 0 ? 1 : 0) ;
                }
                return;
            
            case 0x8000:
            case 0x9000:
//                System.out.println("address: " + Integer.toHexString(address));
//                System.out.println("value: " + Integer.toHexString(value));
//                long time = System.currentTimeMillis();
//                System.out.println(time);

                gpu.VRAM[address - 0x8000] = value;
                gpu.updateTile(address, value);
                return;

            case 0xA000:
            case 0xB000:
//                System.out.println(Integer.toHexString(RAM_offset));
//                System.out.println(Integer.toHexString(ExternalRAMContent.length));
                if(ExternalRAMContent.length == 0) return;
                ExternalRAMContent[RAM_offset + address - 0xA000] = value;
                return;
            
            case 0xC000:
            case 0xD000:
            case 0xE000:
                WorkingRAMContent[address - 0xC000] = value;
                return;

            case 0xF000:
                switch(address & 0x0F00) { // address by the next 4 bits
                // Working RAM shadow
                case 0x000:
                    if(address == 0xFF02) {
                        if(value == 0x81) {
                            System.out.println(readByte(0xFF01));
                        }
                    }
                    return;
                case 0x100: case 0x200: case 0x300:
                case 0x400: case 0x500: case 0x600: case 0x700:
                case 0x800: case 0x900: case 0xA00: case 0xB00:
                case 0xC00: case 0xD00:
                    WorkingRAMContent[address - 0xC000] = value;
                    return;

                // Graphics Memory
                // OAM for sprites
                case 0xE00:
                    if(address < 0xFEA0){
                        gpu.OAM[address - 0xFE00] = value;
                    }
                    else{
                        // everything else in OAM should remain as 0 even when written into
                    }
                    gpu.buildSpriteData(address - 0xFE00, value);
                    return;
                
                // zero page RAM and I/O
                case 0xF00:
                    if(address >= 0xFF80){ 
                        if(address == 0xFFFF){
                            interrupt_enable.write(value);
                        }
                        if(address == 0xFFFE || address == 0xFFFF) {
//                            System.out.println("test");
//                            System.out.println(address);
                        }
                        ZeroPageRAMContent[address-0xFF80] = value;
                        if(address == 0xFF85) {
//                            System.out.println("Written into 0xFF85");
//                            System.out.println(Integer.toHexString(cpu.rf.PC.read()));
//                            System.out.println(Integer.toHexString(value));
                        }
                        return;
                    }
                    else if(address >= 0xFF40) {
                        gpu.writeByte(address, value);
                        return;
                    }
                    else if(address >= 0xFF10) {
                        apu.writeByte(address, value);
                        return;
                    }
                    else if(address > 0xFF00){ // I/O Mapping
                        if(address == 0xFF0F) {
                            interrupt_flags.write(value);
                            return;
                        }
                        if(address == 0xFF02 && (value == 0x81 || value == 81)) {
                            System.out.println((char) readByte(0xFF01));
                        }
                        if(address >= 0xFF04 && address <= 0xFF07) {
                            timer.writeByte(address, value);
                            return;
                        }
                        // not set up yet
                        IO[address - 0xFF00] = value;
                        return;
                    }
                    else{ // FF00 input
//                        System.out.println(Integer.toHexString(value));
                        (gpu.game.input).writeByte(address, value);
                        return;
                    }
            }
        
        }
    }

    public void writeWord(int address, int value){
//        System.out.println(Integer.toString(address, 16));
//        System.out.println(Integer.toString(address + 1, 16));
        writeByte(address, value & 0x00FF); // write lower 8 bits into smaller memory address
        writeByte(address+1, value >> 8); // write upper 8 bits into larger memory address
//        System.out.println(Integer.toString(readWord(address), 16));
//        System.out.println(Integer.toString(readByte(address), 16));
//        System.out.println(Integer.toString(readByte(address + 1), 16));
//        System.out.println(Integer.toString(address + 1, 16));
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
            0xDD, 0xDC, 0x99, 0x9F, 0xBB, 0xB9, 0x33, 0x3E, 0x3c, 0x42, 0xB9, 0xA5, 0xB9, 0xA5, 0x42, 0x3C,
            0x21, 0x04, 0x01, 0x11, 0xA8, 0x00, 0x1A, 0x13, 0xBE, 0x20, 0xFE, 0x23, 0x7D, 0xFE, 0x34, 0x20,
            0xF5, 0x06, 0x19, 0x78, 0x86, 0x23, 0x05, 0x20, 0xFB, 0x86, 0x20, 0xFE, 0x3E, 0x01, 0xE0, 0x50 };
    }
}
