import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class CPU {

//    boolean DEBUG = true;

    static final int FREQUENCY = 4 * 1024 * 1024;

    boolean DEBUG = false;
    boolean DEBUG_FILE = false;

    interface Lambda {
        int execute(CPU cpu);
        // (CPU cpu) -> cpu.LD(A, B);
    }

    // Clock Elements
    static int m = 1;
    static int t = 4;
    Clock clk;

    // Register File
    RegisterFile rf;

    boolean interrupt_master_enable = false;
    boolean next_interrupt_master_enable = false;
    boolean halt;

    final static int ZFLAG_BIT = RegisterFile.ZFLAG_BIT;
    final static int NFLAG_BIT = RegisterFile.NFLAG_BIT;
    final static int HFLAG_BIT = RegisterFile.HFLAG_BIT;
    final static int CFLAG_BIT = RegisterFile.CFLAG_BIT;

    PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("cpu_instrs.out")));

    // Memory 
    MMU mmu;

    // Timer
    Timer2 timer;

    // HashMap<Integer, Instruction> opcodes = new HashMap<>();

    class Operation {
        String description;
        Lambda lambda;
        char [] flags; // Z N H C
        int length; // number of bytes
        int [] cycles; // machine cycles (m)

        public Operation(String description, Lambda lambda, char[] flags, int length, int [] cycles) {
            this.description = description;
            this.lambda = lambda;
            this.flags = flags;
            this.length = length;
            this.cycles = cycles;
        }

        public int execute(CPU cpu) {
            // TODO: Fix operation-dependent cycles
            m = cycles[0];
            t = cycles[0] * 4;
            return lambda.execute(cpu);
        }

        public String toString() {
            return (description);
        }
    }

    Operation[] operations = new Operation[512];

    public CPU(MMU mmu, Timer2 timer) throws IOException {
        clk = new Clock();
        rf = new RegisterFile();
        this.timer = timer;
        this.mmu = mmu;

        define_ops();
    }

    /*
             PC     PC+1      PC+2
    MEMORY  |opcode|immediate|opcode
             8-bits 8-bits
    */

    public int tick() {
        // grap opcode

        int current_pc = rf.PC.read();
        int opcode = mmu.readByte(current_pc);

        // increment PC
        int result_pc = current_pc + 1;
        result_pc &= 0xFFFF;
        rf.PC.write(result_pc);

        if(result_pc >= 0xff && !mmu.finished_bios) {
            mmu.finished_bios = true;
            System.out.println("finished BIOS");
//            DEBUG = true;
//            DEBUG_FILE = true;
        }

//        System.out.println(Integer.toString(current_pc, 16));
//        System.out.println(Integer.toString(opcode, 16));

        // CB-prefixed opcodes
        if(opcode == 0xCB) {
            int next_pc = rf.PC.read();
            int next_opcode = mmu.readByte(next_pc);
            opcode = 0x100 + next_opcode;
            result_pc = next_pc + 1;
            result_pc &= 0xFFFF;
            rf.PC.write(result_pc);
        }

        Operation op = operations[opcode];

        interrupt_master_enable = next_interrupt_master_enable;
//        try {
            op.execute(this);
//        }
//        catch(Exception e) {
//            System.err.println(e);
//            System.err.println(rf.SP.read());
//            System.err.println(Integer.toHexString(opcode));
//            System.err.println(Integer.toHexString(current_pc));
//            System.exit(1);
//            out.close();
//        }

        timer.tick(m);

        // Handle interrupts
        if(interrupt_master_enable && (MMU.interrupt_enable.read() != 0) & (MMU.interrupt_flags.read() != 0)) {
            int fired = MMU.interrupt_enable.read() & MMU.interrupt_flags.read();
//            System.out.println("fired : " + Integer.toHexString(fired));
            handleInterrupt(fired);
        }

//        timer.tick(m);

//        try {
//            op.execute(this);
//        }
//        catch (Exception e) {
//            System.out.println("PC Error");
//            System.out.println(Integer.toString(current_pc, 16) + " : " + Integer.toString(opcode >= 0x100 ? opcode - 0x100 : opcode, 16) + " : " + op);
//
//            rf.dump();
//        }

        if(DEBUG) {
            System.out.println(Integer.toString(current_pc, 16) + " : " + Integer.toString(opcode >= 0x100 ? opcode - 0x100 : opcode, 16) + " : " + op);
            if(DEBUG_FILE) out.println(Integer.toString(current_pc, 16) + " : " + Integer.toString(opcode >= 0x100 ? opcode - 0x100 : opcode, 16) + " : " + op);
            if(DEBUG_FILE) out.println(rf.dump());
            if(DEBUG_FILE) out.println(interrupt_master_enable + " " + Integer.toHexString(MMU.interrupt_enable.read()) + " " + Integer.toHexString(MMU.interrupt_flags.read()));
            System.out.println(rf.dump());
        }

        clk.tick();

        return 0;
    }

    public int d8() {
        int pc = rf.PC.read();
        int data = mmu.readByte(pc);
//        System.out.println("d8() " + Integer.toHexString(pc));
        pc += 1;
        pc &= 0xFFFF;
        rf.PC.write(pc);
        return data;
    }

    public int d16() {
        int lower = d8();
        int upper = d8();
        return upper << 8 | lower;
    }

    public int r8() {
        int data = d8();
        // if(data > 127) {
        //     return -(~data+1);
        // }
        // else return data;

//        System.out.println("r8()");
//        System.out.println(data);
//        System.out.println(-(((~data) & 127) + 1));

        return data > 127 ? -(((~data) & 127) + 1) : data;
    }

    public int a8() {
        int value = d8();
        return value;
    }

    public int a16() {
        int value = d16();
//        System.out.println(Integer.toHexString(value));
        return value;
    }

    public void dispatch(){
        while(true){
            int pc = rf.PC.read();
            int opcode = mmu.readByte(pc);
            // TODO: run instruction given by current pc

            rf.PC.write((pc + 1) % 65356); // update PC
            
            clk.tick();
        }
    }

    public void handleInterrupt(int fired) {
        // Consider VBLANK interrupt
        if((fired & 0x01) == 0x01) {
            interrupt_master_enable = false;
//            System.out.println("MMU.interrupt_flags.read()");
//            System.out.println(Integer.toHexString(MMU.interrupt_flags.read()));
            MMU.interrupt_flags.write(MMU.interrupt_flags.read() & ~0x01);
//            System.out.println(Integer.toHexString(MMU.interrupt_flags.read()));
            RST(0x40);

            m = 4;
            t = 16;
        }
        
        // Consider Joypad Interrupt
        if((fired & 0x10) == 0x10) {
//            System.out.println("keypad interrupt");
            interrupt_master_enable = false;
            MMU.interrupt_flags.write(MMU.interrupt_flags.read() & ~0x10);
            RST(0x60);

            m = 4;
            t = 16;
        }

        if((fired & 0x04) == 0x04) {
//            System.out.println("timer interrupt");
            interrupt_master_enable = false;
            MMU.interrupt_flags.write(MMU.interrupt_flags.read() & ~0x04);
            RST(0x50);

            m = 4;
            t = 16;
        }

        if((fired & 0x02) == 0x02) {
//            System.out.println("stat interrupt");
            interrupt_master_enable = false;
            MMU.interrupt_flags.write(MMU.interrupt_flags.read() & ~0x02);
            RST(0x48);

            m = 4;
            t = 16;
        }
    }

    /* 8-Bit ALU */

    public int INC_8(Register r) {
        int result = 0;
        int data = 0;

        if(r == rf.HL) {
            data = mmu.readByte(rf.HL.read());
            result = data + 1;
            if(result > 0xFF) result = 0;
            mmu.writeByte(rf.HL.read(), result);
        }
        else {
            data = r.read();
            result = data + 1;
            if(result > 0xFF) result = 0;
            r.write(result);
        }

        // TODO: Check flags
        // probably need to figure out a better flags system
        rf.setFlag(ZFLAG_BIT, result == 0);
        rf.setFlag(HFLAG_BIT, ((int) (data & 0x0F)) == 0x0F); // TODO: CHECK THIS
        rf.setFlag(NFLAG_BIT, false);

        return result;
    }

    public int DEC_8(Register r) {
        int result = 0;
        int data = 0;

        if(r == rf.HL) {
            data = mmu.readByte(rf.HL.read());
            result = data - 1;
            if(result < 0) result = 0xFF;
            mmu.writeByte(rf.HL.read(), result);
        }
        else {
            data = r.read();
            result = data - 1;
            if(result < 0) result = 0xFF;
            r.write(result);
        }

        // TODO: Check flags
        // probably need to figure out a better flags system
        rf.setFlag(ZFLAG_BIT, result == 0);
        rf.setFlag(HFLAG_BIT, ((int) (result & 0x0F)) == 0x0F); // TODO: CHECK THIS
        rf.setFlag(NFLAG_BIT, true);

        return result;
    }

    public int ADD_8(Register r1, Register r2, int immediate_value, boolean carry) {
        int data = 0;
        // Immediate value?
        if(r2 == null) {
            data = immediate_value;
        }
        else if(r2 == rf.HL) {
            data = mmu.readByte(rf.HL.read());
        }        
        else {
            data = r2.read();
        }
        data &= 0xFF;
        int original = r1.read();
        int carry_data = (carry ? rf.getFlag(CFLAG_BIT) : 0);
        int result = original + data + carry_data;


        rf.setFlag(ZFLAG_BIT, (result & 0xFF) == 0);
        rf.setFlag(CFLAG_BIT, result > 255);
        rf.setFlag(NFLAG_BIT, false);
        rf.setFlag(HFLAG_BIT, (original & 0xF) + (data & 0xF) + carry_data > 0xF);

        result &= 0xFF; // mask the result

        r1.write(result);

        return result;
    }

    public int ADC(Register r1, Register r2, int immediate_value) {
        return ADD_8(r1, r2, immediate_value, true);
    }

//    opcodes.put(0x01, LoadInstruction.LoadRegisterToRegister(registers.A, registers.B));


    public int SUB(Register r1, Register r2, int immediate_value, boolean carry, boolean write){
        int data = 0;

        if(r2 == null){
            data = immediate_value;
        }
        else if(r2.equals(rf.HL)){
            data = mmu.readByte(r2.read());
        }
        else{
            data = r2.read();
        }

        data &= 0xFF;
//        if(DEBUG) System.out.println("SUB " + Integer.toHexString(data));

        int original = r1.read();
        int carry_data = (carry ? rf.getFlag(CFLAG_BIT) : 0);
        int result = (original - data) - carry_data;

        // set flags
        rf.setFlag(ZFLAG_BIT, (result & 0xFF) == 0);
        rf.setFlag(NFLAG_BIT, true);
        rf.setFlag(HFLAG_BIT, (original & 0x0F) - (data & 0x0F) - carry_data < 0); // TODO: Check
        rf.setFlag(CFLAG_BIT, result < 0); // TODO: Check
        
        result &= 0xFF;
        if(write){
            r1.write(result);
        }
        
        return result;
    }

    public int SBC(Register r1, Register r2, int immediate_value){
        return SUB(r1, r2, immediate_value, true, true);
    }

    public int AND(Register r1, Register r2, int immediate_value) {
        int data = 0;
        
        // immediate value?
        if(r2 == null) {
            data = immediate_value;
        }
        else if(r2 == rf.HL) {
            data = mmu.readByte(rf.HL.read());
        }
        else {
            data = r2.read();
        }

        int original = r1.read();
        int result = original & data;

        rf.setFlag(ZFLAG_BIT, result == 0);
        rf.setFlag(CFLAG_BIT, false);
        rf.setFlag(NFLAG_BIT, false);
        rf.setFlag(HFLAG_BIT, true);

        r1.write(result);

        return result;
    }
    
    public int OR(Register r1, Register r2, int immediate_value) {
        int data = 0;
        
        // immediate value?
        if(r2 == null) {
            data = immediate_value;
        }
        else if(r2 == rf.HL) {
            data = mmu.readByte(rf.HL.read());
        }
        else {
            data = r2.read();
        }

        int original = r1.read();
        int result = original | data;

        rf.setFlag(ZFLAG_BIT, result == 0);
        rf.setFlag(CFLAG_BIT, false);
        rf.setFlag(NFLAG_BIT, false);
        rf.setFlag(HFLAG_BIT, false);

        r1.write(result);

        return result;
    }

    public int XOR(Register r1, Register r2, int immediate_value) {
        int data = 0;
        
        // immediate value?
        if(r2 == null) {
            data = immediate_value;
        }
        else if(r2 == rf.HL) {
            data = mmu.readByte(rf.HL.read());
        }
        else {
            data = r2.read();
        }

        int original = r1.read();
        int result = original ^ data;

        rf.setFlag(ZFLAG_BIT, result == 0);
        rf.setFlag(CFLAG_BIT, false);
        rf.setFlag(NFLAG_BIT, false);
        rf.setFlag(HFLAG_BIT, false);

        r1.write(result);

        return result;
    }

    public int CP(Register r1, Register r2, int immediate_value){
        return SUB(r1, r2, immediate_value, false, false);
    }


    /* 16-BIT ARITHMETIC */
    public int ADD_16(Register r1, Register r2) {
        int data = r2.read();
        int original = r1.read();

        int result = original + data;

        rf.setFlag(NFLAG_BIT, false);
        rf.setFlag(HFLAG_BIT, (original & 0xFFF) + (data & 0xFFF) > 0x0FFF);
        rf.setFlag(CFLAG_BIT, result > 0xFFFF);

        result &= 0xFFFF; // mask to 16-bit

        r1.write(result);

        return result;
    }

    public int ADD_SP(int immediate_value) {
        int original = rf.SP.read();

        rf.setFlag(ZFLAG_BIT, false);
        rf.setFlag(NFLAG_BIT, false);

        // negative value
        // if(immediate_value > 127) {
        //     data = -((~immediate_value+1)&0xFF);
        //     result = original + data;
        //     rf.setFlag(CFLAG_BIT, result < 0);
        //     rf.setFlag(HFLAG_BIT, (original & 0xFFF) < (data & 0xFFF));
        // }
        // // positive value
        // else {
        //     data = immediate_value;
        //     result = original + data;
        //     rf.setFlag(CFLAG_BIT, result > 0xFFFF);
        //     rf.setFlag(HFLAG_BIT, (original & 0xFFF) + (data & 0xFFF) > 0x0FFF);
        // }

        int data = immediate_value; // r8()

        if(data < 0) {
            data = (65536 + data) & 0xFFFF;
        }

        int result = original + data;
        result &= 0xFFFF; // mask to 16-bit

        rf.setFlag(CFLAG_BIT, (original & 0xFF) + (data & 0xFF) > 0xFF);
        rf.setFlag(HFLAG_BIT, (original & 0x0F) + (data & 0x0F) > 0x0F);
        
        rf.SP.write(result);

        return result;
    }

    public int INC_16(Register r) {
        int result = 0;
        int data = 0;

        data = r.read();
        result = data + 1;
        result &= 0xFFFF;
        r.write(result);

        return result;
    }

    public int DEC_16(Register r) {
        int result = 0;
        int data = 0;

        data = r.read();
        result = data - 1;
        if(data == 0x0000) result = 0xFFFF; // TODO: Check
        r.write(result);

        return result;
    }

    /* 8 bit Loads */
    
    public int LD_8(Register r1, Register r2, int immediate_value1, int immediate_value2, 
                  boolean operand1_mem, boolean operand2_mem, boolean operand1_8bit, 
                  boolean operand2_8bit, boolean increment, boolean decrement) {
        int result = 0;
        int address = 0;

        // loads into 8-bit register
        if(r1 instanceof Register8Bit) {
            
            // immediate value load (ex. LD A, d8)
            if(r2 == null) {
                if(operand2_mem) {
                    // special case 0xF0: LD A, ($FF00+n)
                    if(operand2_8bit) {
                        address = immediate_value2 + 0xFF00;
//                        System.out.println("LDH test: " + Integer.toHexString(address));
                    }
                    // immediate value is a memory address (LD A, (nn))
                    else {
                        address = immediate_value2;
                    }
                    result = mmu.readByte(address);
                }
                else{ // directly load immediate value into r1 (ex. LD A, n)
                    result = immediate_value2;
                }
            }
            else {
                if(r2 instanceof Register8Bit) {
                    // Put value at address $FF00 + register C into A (ex. 0xF2: LD A, (C))
                    if(operand2_mem) {
//                        System.out.println("Address: " + Integer.toHexString(r2.read() + 0xFF00));
                        result = mmu.readByte(r2.read() + 0xFF00);
                    }
                    // Put A into value at address $FF00 + register C (ex. 0xE2: LD (C), A)
                    else if(operand1_mem) {
                        mmu.writeByte(r1.read() + 0xFF00, r2.read());
                        return r2.read();
                    }
                    // 8-bit register copy (ex. LD A, B)
                    else {
                        result = r2.read();
                    }
                }
                // 16-bit register address (ex. LD A, (HL))
                else if(r2 instanceof DoubleRegister16Bit) {
                    result = mmu.readByte(r2.read());

                    if(increment) {
                        INC_16(r2);
                    }
                    // 0x3A: LD A, (HL-)
                    if(decrement) {
                        DEC_16(r2);
                    }
                }
            }

//            System.out.println("LDH result: " + Integer.toHexString(result));
            r1.write(result);

        }
        // loading into memory addressed by 16-bit register (ex. LD (HL), A and LD (HL), n)
        // or loading into memory addressed by immediate value (ex. LD (nn), A and LD (nn),n)
        // or loading into zero-page memory addressed by 8-bit immedate value (LDH (n), A)
        else {
            // loading into memory addressed by 16-bit register (ex. LD (HL), A and LD (HL), n)
            if(r1 instanceof DoubleRegister16Bit) {
                address = r1.read();

                if(increment) {
                    INC_16(r1);
                }
                // 0x32: LD (HL-), A
                if(decrement) {
                    DEC_16(r1);
                }
            }
            // memory addressed by immediate value
            else {
                // special case for 0xE0: LD ($FF00+n), A
                if(operand1_8bit) {
                    // 	LD ($FF00+$50),A	; $00fe	;turn off DMG rom
//                    System.out.println("loading $FF00+" + Integer.toHexString(immediate_value1));
//                    System.out.println("PC : " + Integer.toHexString(rf.PC.read()));
                    address = immediate_value1 + 0xFF00;
                }
                else {
                    address = immediate_value1;
                }
            }

            // The result
            if(r2 == null) { // immediate value
                result = immediate_value2;
            }
            else{
                result = r2.read();
            }
            // int address = (r1 instanceof Register16Bit) ? r1.read() : immediate_value1;
            // result = (r2 == null) ? immediate_value2 : r2.read();
//            System.out.println(Integer.toHexString(address));
            mmu.writeByte(address, result);
        }

        return result;
    }

    /* 16 bit Loads */ 

    public int LD_16(Register r1, Register r2, int immediate_value1, int immediate_value2, boolean operand1_8bit, boolean operand2_8bit){
        int address = 0;
        int result = 0;

        if(r1 instanceof Register16Bit || r1 instanceof DoubleRegister16Bit){
            // Handles  LDHL SP,n   LD BC,nn    
            if(r2 == null) {
                // LD HL, SP+n
                if(operand2_8bit) {
                    int original = rf.SP.read();
                    int data = immediate_value2;

                    if(data < 0){
                        data = (65536 + data) & 0xFFFF;
                    }
                    
                    result = original + data;
                    result &= 0xFFFF;

                    rf.setFlag(ZFLAG_BIT, false);
                    rf.setFlag(NFLAG_BIT, false);
                    rf.setFlag(CFLAG_BIT, (original & 0xFF) + (data & 0xFF) > 0xFF);
                    rf.setFlag(HFLAG_BIT, (original & 0x0F) + (data & 0x0F) > 0x0F);
                }
                else{ // ex. LD BC,nn
                    result = immediate_value2;
                }
            }
            else{ // Handles LD SP,HL
                result = r2.read();
            }

            r1.write(result);
        }
        else{ // Handles LD (nn),SP
            address = immediate_value1;
            result = r2.read();
            mmu.writeWord(address,result);
        }


        return result;
    }

    public int PUSH(Register r) {
        int original = rf.SP.read();
        int result = (original - 2) & 0xFFFF;
        result &= 0xFFFF;
        rf.SP.write(result);

        // TODO: check if data is written before/after SP is decremented
        mmu.writeWord(rf.SP.read(), r.read());

        return result;
    }

    public int POP(Register r) {
        // TODO: check if data is written before/after SP is decremented
        int data = mmu.readWord(rf.SP.read());

        if(r == rf.AF) {
            data &= 0xFFF0;
        }
        r.write(data);
                
        int original = rf.SP.read();
        int result = (original + 2) & 0xFFFF;
        result &= 0xFFFF;
        rf.SP.write(result);

        return result;
    }

    public int SWAP(Register r) {
        int data = 0;
        // Memory pointed to by (HL)
        if(r == rf.HL) {
            data = mmu.readByte(r.read());
        }
        // Register value
        else {
            data = r.read();
        }

        int lower = data & 0xF;
        int higher = data >> 4;
        int result = (lower << 4) | higher;

        rf.setFlag(ZFLAG_BIT, result == 0);
        rf.setFlag(CFLAG_BIT, false);
        rf.setFlag(NFLAG_BIT, false);
        rf.setFlag(HFLAG_BIT, false);

        if(r == rf.HL) {
            mmu.writeByte(r.read(), result);
        }
        else {
            r.write(result);
        }

        return result;
    }

    // http://www.z80.info/z80syntx.htm#DAA
    // 0x27: DAA
    public int DAA(Register r) {
        int result = 0;
        int data = r.read();

        if(!rf.isFlagSet(NFLAG_BIT)) {
            if(rf.isFlagSet(CFLAG_BIT) || data > 0x99) {
                data += 0x60;
                rf.setFlag(CFLAG_BIT, true);
            }

            if(rf.isFlagSet(RegisterFile.HFLAG_BIT) || (data & 0x0F) > 0x09) {
                data += 0x06;
                rf.setFlag(RegisterFile.HFLAG_BIT, false);
            }
        }
        else if(rf.isFlagSet(CFLAG_BIT) && rf.isFlagSet(HFLAG_BIT)) {
            data += 0x9A;
            rf.setFlag(HFLAG_BIT, false);
        }
        else if(rf.isFlagSet(CFLAG_BIT)) {
            data += 0xA0;
        }
        else if(rf.isFlagSet(HFLAG_BIT)) {
            data += 0xFA;
            rf.setFlag(HFLAG_BIT, false);
        }

        result = data & 0xFF;
        rf.setFlag(ZFLAG_BIT, result == 0);

        r.write(result);

        return result;
    }

    // 0x2F: CPL
    public int CPL() {
        int data = rf.A.read();

        int result = ~data & 0xFF;

        rf.A.write(result);

        rf.setFlag(RegisterFile.HFLAG_BIT, true);
        rf.setFlag(RegisterFile.NFLAG_BIT, true);

        return result;
    }

    public int CCF() {
        int data = rf.getFlag(CFLAG_BIT);
        rf.setFlag(CFLAG_BIT, !rf.isFlagSet(CFLAG_BIT));
        rf.setFlag(NFLAG_BIT, false);
        rf.setFlag(HFLAG_BIT, false);
        return ~data;
    }

    public int SCF() {
        rf.setFlag(CFLAG_BIT, true);
        rf.setFlag(NFLAG_BIT, false);
        rf.setFlag(HFLAG_BIT, false);

        return 1;
    }

    // TODO: Check NOOP behavior
    public int NOP() {
        return 0;
    }

    public int HALT() {
        // if()
        return 0;
    }

    // TODO: STOP length is 2!
    public int STOP() {
        halt = true; // TODO: stop flag? how is it different?
        return 0;
    }

    public int DI() {
        interrupt_master_enable = false;
        next_interrupt_master_enable = false;
//        System.out.println("DI");
        return 0;
    }

    public int EI() {
        next_interrupt_master_enable = true;
//        System.out.println("EI");
        return 1;
    }

    /* Rotates & Shifts */


    public int RLC(Register r) {
        int data = r == rf.HL ? mmu.readByte(r.read()) : r.read();
        rf.setFlag(CFLAG_BIT, (data >> 7) == 1); // old bit 7 to carry flag

        int carry = rf.getFlag(CFLAG_BIT);
        int result = (data << 1) | carry;

        result &= 0xFF;

        rf.setFlag(ZFLAG_BIT, result == 0);
        rf.setFlag(NFLAG_BIT, false);
        rf.setFlag(HFLAG_BIT, false);

        if(r == rf.HL) {
            mmu.writeByte(r.read(), result);
        }
        else {
            r.write(result);
        }

        return result;
    }

    public int RLCA() {
        int data = RLC(rf.A);
        rf.setFlag(ZFLAG_BIT, false);
        return data;
    }

    // Rotate left through carry flag
    public int RL(Register r) {
        int data = r == rf.HL ? mmu.readByte(r.read()) : r.read();
        int carry = rf.getFlag(CFLAG_BIT);
        rf.setFlag(CFLAG_BIT, (data >> 7) == 1); // old bit 7 to carry flag

//        System.out.println("RL");
//        System.out.println(Integer.toString(data, 16));
//        System.out.println(Integer.toString(carry, 16));
//        System.out.println(Integer.toString(data << 1, 16));

        int result = (data << 1) | carry;
        result &= 0xFF;
        rf.setFlag(ZFLAG_BIT, result == 0);
        rf.setFlag(NFLAG_BIT, false);
        rf.setFlag(HFLAG_BIT, false);

        if(r == rf.HL) {
            mmu.writeByte(r.read(), result);
        }
        else {
            r.write(result);
        }

        return result;
    }

    public int RLA() {
        int data = RL(rf.A);
        rf.setFlag(RegisterFile.ZFLAG_BIT, false);
        return data;
    }

    public int RRC(Register r) {
        int data = r == rf.HL ? mmu.readByte(r.read()) : r.read();
        rf.setFlag(CFLAG_BIT, (data & 0x01) == 1); // old bit 0 to carry flag

        int carry = rf.getFlag(CFLAG_BIT);
        int result = (data >> 1) | (carry << 7);

        result &= 0xFF;

        rf.setFlag(ZFLAG_BIT, result == 0);
        rf.setFlag(NFLAG_BIT, false);
        rf.setFlag(HFLAG_BIT, false);

        if(r == rf.HL) {
            mmu.writeByte(r.read(), result);
        }
        else {
            r.write(result);
        }

        return result;
    }

    public int RRCA() {
        int data = RRC(rf.A);
        rf.setFlag(RegisterFile.ZFLAG_BIT, false);
        return data;
    }

    public int RR(Register r) {
        int data = r == rf.HL ? mmu.readByte(r.read()) : r.read();
        int carry = rf.getFlag(CFLAG_BIT);
        rf.setFlag(CFLAG_BIT, (data & 0x01) == 1); // old bit 7 to carry flag

        int result = (data >> 1) | (carry << 7);
        rf.setFlag(ZFLAG_BIT, result == 0);
        rf.setFlag(NFLAG_BIT, false);
        rf.setFlag(HFLAG_BIT, false);

        if(r == rf.HL) {
            mmu.writeByte(r.read(), result);
        }
        else {
            r.write(result);
        }

        return result;
    }

    public int RRA(){
        int data = RR(rf.A);
        rf.setFlag(RegisterFile.ZFLAG_BIT, false);
        return data;
    }

    public int SLA(Register r) {
        int original = r == rf.HL ? mmu.readByte(r.read()) : r.read();
        rf.setFlag(CFLAG_BIT, (original >> 7) == 1); // contains old bit 7 data

        int result = original << 1;
        result &= 0xFF;

        rf.setFlag(ZFLAG_BIT, result == 0);
        rf.setFlag(NFLAG_BIT, false);
        rf.setFlag(HFLAG_BIT, false);

        if(r == rf.HL) {
            mmu.writeByte(r.read(), result);
        }
        else {
            r.write(result);
        }

        return result;
    }

    public int SRA(Register r) {
        int original = r == rf.HL ? mmu.readByte(r.read()) : r.read();
        rf.setFlag(CFLAG_BIT, (original & 0x01) == 1); // contains old bit 0 data

        int msb = original & 0x80; // maintain msb
        int result = msb | (original >> 1);

        rf.setFlag(ZFLAG_BIT, result == 0);
        rf.setFlag(NFLAG_BIT, false);
        rf.setFlag(HFLAG_BIT, false);

        if(r == rf.HL) {
            mmu.writeByte(r.read(), result);
        }
        else {
            r.write(result);
        }

        return result;
    }

    public int SRL(Register r) {
        int original = r == rf.HL ? mmu.readByte(r.read()) : r.read();
        rf.setFlag(CFLAG_BIT, (original & 0x01) == 1); // contains old bit 0 data

        int result = (original >> 1);

        rf.setFlag(ZFLAG_BIT, result == 0);
        rf.setFlag(NFLAG_BIT, false);
        rf.setFlag(HFLAG_BIT, false);

        if(r == rf.HL) {
            mmu.writeByte(r.read(), result);
        }
        else {
            r.write(result);
        }

        return result;
    }

    public int BIT(Register r, int bit){
        int data = 0;

        if(r == rf.HL){
            data = mmu.readByte(rf.HL.read());
        }
        else{
            data = r.read();
        }

        int result = data & (0x01 << (bit));

        rf.setFlag(ZFLAG_BIT, result == 0);
        rf.setFlag(NFLAG_BIT, false);
        rf.setFlag(HFLAG_BIT, true);

        return result;
    }

    public int SET(Register r, int bit){
        int original = 0;
        int rewrite = 0;
        int mask = (0x01 << bit);

        if(r == rf.HL){
            original = mmu.readByte(rf.HL.read());
            rewrite = original | mask;
            mmu.writeByte(rf.HL.read(), rewrite);
        }
        else{
            original = r.read();
            rewrite = original | mask;
            r.write(rewrite);
        }

        return rewrite;
    }

    public int RES(Register r, int bit){
        int original = 0;
        int rewrite = 0;
        int mask = ~ (0x01 << bit);

        if(r == rf.HL){
            original = mmu.readByte(rf.HL.read());
            rewrite = original & mask;
            mmu.writeByte(rf.HL.read(), rewrite);
        }
        else{
            original = r.read();
            rewrite = original & mask;
            r.write(rewrite);
        }

        return rewrite;
    }

    /* Jumps */

    public enum Condition {
        NZ,
        Z,
        NC,
        C,
        NONE
    }

    public int JP(Register r1, Condition c, int immediate_value){
        int location = 0;
        boolean jump = false;

        if(DEBUG_FILE) out.println(c.toString());

        if(r1 == null){ // JP nn or JP cc, nn
            switch(c){
                // JP cc,nn
                case NZ:
                    jump = !(rf.isFlagSet(ZFLAG_BIT));
                    break;
                case Z:
                    jump = rf.isFlagSet(ZFLAG_BIT);
                    break;
                case NC:
                    jump = !(rf.isFlagSet(CFLAG_BIT));
                    break;
                case C: 
                    jump = rf.isFlagSet(CFLAG_BIT);
                    break;
                case NONE: // JP nn
                    jump = true;
            }

            location = immediate_value;

        }
        else{ // JP (HL) 
            location = rf.HL.read(); 
            jump = true;
        }

        if(jump){
            rf.PC.write(location);
        }

//        if(DEBUG)
//            System.out.println("location JP : " + location);

        return location;
    }

    /* relative jump */

    public int JR(Condition c, int immediate_value){
        int original = rf.PC.read();
        int location = original + immediate_value;
        location &= 0xFFFF;

//        System.out.println("JR");
//        System.out.println(immediate_value);
//        System.out.println(location);

        boolean jump = false;
        
        switch(c){
            // JR cc,nn
            case NZ:
                jump = !(rf.isFlagSet(ZFLAG_BIT));
                break;
            case Z:
                jump = rf.isFlagSet(ZFLAG_BIT);
                break;
            case NC:
                jump = !(rf.isFlagSet(CFLAG_BIT));
                break;
            case C: 
                jump = rf.isFlagSet(CFLAG_BIT);
                break;
            case NONE: // JR nn
                jump = true;
        }

//        System.out.println(jump ? "JR taken" : "JR not taken");

        if(jump){
            rf.PC.write(location);
        }

        return location;
    }
    
    public int CALL(Condition c, int immediate_value){
        boolean call = false;
        int location = immediate_value;

        switch(c){
            case NZ:
                call = !(rf.isFlagSet(ZFLAG_BIT));
                break;
            case Z:
                call = rf.isFlagSet(ZFLAG_BIT);
                break;
            case NC:
                call = !(rf.isFlagSet(CFLAG_BIT));
                break;
            case C: 
                call = rf.isFlagSet(CFLAG_BIT);
                break;
            case NONE:
                call = true;
        }

        if(call){
            // push pc+1 onto stack

            int original = rf.SP.read();
            int result = (original - 2) & 0xFFFF;
            result &= 0xFFFF;
            rf.SP.write(result);

            // TODO: check if data is written before/after SP is decremented
            mmu.writeWord(rf.SP.read(), rf.PC.read());
            if(DEBUG && DEBUG_FILE) out.println("CALL WRITTEN at " + Integer.toHexString(rf.SP.read()) + " : " + Integer.toHexString(rf.PC.read()));
//            System.out.println("mmu.writeWord() " + Integer.toHexString(rf.SP.read()) + " " + Integer.toHexString(rf.PC.read()));

            JP(null, Condition.NONE, immediate_value);

            return immediate_value;
        }

        return -1;
    }


    public int RST(int immediate_value) {
        return CALL(Condition.NONE, immediate_value);
    }

    // https://azug.minpet.unibas.ch//~lukas/GBprojects/gbFAQ.html
    // What is the difference between assembly commands RET & RETI ?
    //RET is just a return from subroutine. RETI is two commands in one. RETI is EI / RET in that order. The command EI doesn't take effect immediately but DI does. EI takes effect following the instruction that follows it.
    public int RET(Condition c, boolean interrupt_enabled) {
        boolean satisfied = c == Condition.NONE ||
                            c == Condition.NZ && !rf.isFlagSet(ZFLAG_BIT) ||
                            c == Condition.Z && rf.isFlagSet(ZFLAG_BIT) ||
                            c == Condition.NC && !rf.isFlagSet(CFLAG_BIT) ||
                            c == Condition.C && rf.isFlagSet(CFLAG_BIT);

        int address = 0;
        if(satisfied) {
            int original = rf.SP.read();
            address = mmu.readWord(original);
            int result = original + 2;
            result &= 0xFFFF;
//            System.out.println("original " + Integer.toHexString(original));
//            System.out.println("address " + Integer.toHexString(address));
//            System.out.println("result " + Integer.toHexString(result));
            if(DEBUG && DEBUG_FILE) {
                out.println("original SP: " + Integer.toHexString(original));
                out.println("address: " + address);
            }
            rf.SP.write(result);
            rf.PC.write(address);
            if(interrupt_enabled) {
                next_interrupt_master_enable = true;
            }
        }

        return address;
    }

    public void define_ops() {
        operations[0x00] = new Operation("NOP", (CPU cpu) -> cpu.NOP(), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x01] = new Operation("LD BC d16", (CPU cpu) -> cpu.LD_16(rf.BC, null, -1, d16(), false, false), new char[] {'-', '-', '-', '-'}, 3, new int[] {12,0});
        operations[0x02] = new Operation("LD (BC) A", (CPU cpu) -> cpu.LD_8(rf.BC, rf.A, -1, -1, true, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x03] = new Operation("INC BC", (CPU cpu) -> cpu.INC_16(rf.BC), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x04] = new Operation("INC B", (CPU cpu) -> cpu.INC_8(rf.B), new char[] {'Z', '0', 'H', '-'}, 1, new int[] {4,0});
        operations[0x05] = new Operation("DEC B", (CPU cpu) -> cpu.DEC_8(rf.B), new char[] {'Z', '1', 'H', '-'}, 1, new int[] {4,0});
        operations[0x06] = new Operation("LD B d8", (CPU cpu) -> cpu.LD_8(rf.B, null, -1, d8(), false, false, false, true, false, false), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x07] = new Operation("RLCA", (CPU cpu) -> cpu.RLCA(), new char[] {'0', '0', '0', 'C'}, 1, new int[] {4,0});
        operations[0x08] = new Operation("LD (a16) SP", (CPU cpu) -> cpu.LD_16(null, rf.SP, a16(), -1, false, false), new char[] {'-', '-', '-', '-'}, 3, new int[] {20,0});
        operations[0x09] = new Operation("ADD HL BC", (CPU cpu) -> cpu.ADD_16(rf.HL, rf.BC), new char[] {'-', '0', 'H', 'C'}, 1, new int[] {8,0});
        operations[0x0a] = new Operation("LD A (BC)", (CPU cpu) -> cpu.LD_8(rf.A, rf.BC, -1, -1, false, true, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x0b] = new Operation("DEC BC", (CPU cpu) -> cpu.DEC_16(rf.BC), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x0c] = new Operation("INC C", (CPU cpu) -> cpu.INC_8(rf.C), new char[] {'Z', '0', 'H', '-'}, 1, new int[] {4,0});
        operations[0x0d] = new Operation("DEC C", (CPU cpu) -> cpu.DEC_8(rf.C), new char[] {'Z', '1', 'H', '-'}, 1, new int[] {4,0});
        operations[0x0e] = new Operation("LD C d8", (CPU cpu) -> cpu.LD_8(rf.C, null, -1, d8(), false, false, false, true, false, false), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x0f] = new Operation("RRCA", (CPU cpu) -> cpu.RRCA(), new char[] {'0', '0', '0', 'C'}, 1, new int[] {4,0});
        operations[0x10] = new Operation("STOP 0", (CPU cpu) -> cpu.STOP(), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x11] = new Operation("LD DE d16", (CPU cpu) -> cpu.LD_16(rf.DE, null, -1, d16(), false, false), new char[] {'-', '-', '-', '-'}, 3, new int[] {12,0});
        operations[0x12] = new Operation("LD (DE) A", (CPU cpu) -> cpu.LD_8(rf.DE, rf.A, -1, -1, true, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x13] = new Operation("INC DE", (CPU cpu) -> cpu.INC_16(rf.DE), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x14] = new Operation("INC D", (CPU cpu) -> cpu.INC_8(rf.D), new char[] {'Z', '0', 'H', '-'}, 1, new int[] {4,0});
        operations[0x15] = new Operation("DEC D", (CPU cpu) -> cpu.DEC_8(rf.D), new char[] {'Z', '1', 'H', '-'}, 1, new int[] {4,0});
        operations[0x16] = new Operation("LD D d8", (CPU cpu) -> cpu.LD_8(rf.D, null, -1, d8(), false, false, false, true, false, false), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x17] = new Operation("RLA", (CPU cpu) -> cpu.RLA(), new char[] {'0', '0', '0', 'C'}, 1, new int[] {4,0});
        operations[0x18] = new Operation("JR r8", (CPU cpu) -> cpu.JR(Condition.NONE, r8()), new char[] {'-', '-', '-', '-'}, 2, new int[] {12,0});
        operations[0x19] = new Operation("ADD HL DE", (CPU cpu) -> cpu.ADD_16(rf.HL, rf.DE), new char[] {'-', '0', 'H', 'C'}, 1, new int[] {8,0});
        operations[0x1a] = new Operation("LD A (DE)", (CPU cpu) -> cpu.LD_8(rf.A, rf.DE, -1, -1, false, true, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x1b] = new Operation("DEC DE", (CPU cpu) -> cpu.DEC_16(rf.DE), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x1c] = new Operation("INC E", (CPU cpu) -> cpu.INC_8(rf.E), new char[] {'Z', '0', 'H', '-'}, 1, new int[] {4,0});
        operations[0x1d] = new Operation("DEC E", (CPU cpu) -> cpu.DEC_8(rf.E), new char[] {'Z', '1', 'H', '-'}, 1, new int[] {4,0});
        operations[0x1e] = new Operation("LD E d8", (CPU cpu) -> cpu.LD_8(rf.E, null, -1, d8(), false, false, false, true, false, false), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x1f] = new Operation("RRA", (CPU cpu) -> cpu.RRA(), new char[] {'0', '0', '0', 'C'}, 1, new int[] {4,0});
        operations[0x20] = new Operation("JR NZ r8", (CPU cpu) -> cpu.JR(Condition.NZ, r8()), new char[] {'-', '-', '-', '-'}, 2, new int[] {12,8});
        operations[0x21] = new Operation("LD HL d16", (CPU cpu) -> cpu.LD_16(rf.HL, null, -1, d16(), false, false), new char[] {'-', '-', '-', '-'}, 3, new int[] {12,0});
        operations[0x22] = new Operation("LD (HL+) A", (CPU cpu) -> cpu.LD_8(rf.HL, rf.A, -1, -1, true, false, false, false, true, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x23] = new Operation("INC HL", (CPU cpu) -> cpu.INC_16(rf.HL), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x24] = new Operation("INC H", (CPU cpu) -> cpu.INC_8(rf.H), new char[] {'Z', '0', 'H', '-'}, 1, new int[] {4,0});
        operations[0x25] = new Operation("DEC H", (CPU cpu) -> cpu.DEC_8(rf.H), new char[] {'Z', '1', 'H', '-'}, 1, new int[] {4,0});
        operations[0x26] = new Operation("LD H d8", (CPU cpu) -> cpu.LD_8(rf.H, null, -1, d8(), false, false, false, true, false, false), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x27] = new Operation("DAA", (CPU cpu) -> cpu.DAA(rf.A), new char[] {'Z', '-', '0', 'C'}, 1, new int[] {4,0});
        operations[0x28] = new Operation("JR Z r8", (CPU cpu) -> cpu.JR(Condition.Z, r8()), new char[] {'-', '-', '-', '-'}, 2, new int[] {12,8});
        operations[0x29] = new Operation("ADD HL HL", (CPU cpu) -> cpu.ADD_16(rf.HL, rf.HL), new char[] {'-', '0', 'H', 'C'}, 1, new int[] {8,0});
        operations[0x2a] = new Operation("LD A (HL+)", (CPU cpu) -> cpu.LD_8(rf.A, rf.HL, -1, -1, false, true, false, false, true, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x2b] = new Operation("DEC HL", (CPU cpu) -> cpu.DEC_16(rf.HL), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x2c] = new Operation("INC L", (CPU cpu) -> cpu.INC_8(rf.L), new char[] {'Z', '0', 'H', '-'}, 1, new int[] {4,0});
        operations[0x2d] = new Operation("DEC L", (CPU cpu) -> cpu.DEC_8(rf.L), new char[] {'Z', '1', 'H', '-'}, 1, new int[] {4,0});
        operations[0x2e] = new Operation("LD L d8", (CPU cpu) -> cpu.LD_8(rf.L, null, -1, d8(), false, false, false, true, false, false), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x2f] = new Operation("CPL", (CPU cpu) -> cpu.CPL(), new char[] {'-', '1', '1', '-'}, 1, new int[] {4,0});
        operations[0x30] = new Operation("JR NC r8", (CPU cpu) -> cpu.JR(Condition.NC, r8()), new char[] {'-', '-', '-', '-'}, 2, new int[] {12,8});
        operations[0x31] = new Operation("LD SP d16", (CPU cpu) -> cpu.LD_16(rf.SP, null, -1, d16(), false, false), new char[] {'-', '-', '-', '-'}, 3, new int[] {12,0});
        operations[0x32] = new Operation("LD (HL-) A", (CPU cpu) -> cpu.LD_8(rf.HL, rf.A, -1, -1, true, false, false, false, false, true), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x33] = new Operation("INC SP", (CPU cpu) -> cpu.INC_16(rf.SP), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x34] = new Operation("INC (HL)", (CPU cpu) -> cpu.INC_8(rf.HL), new char[] {'Z', '0', 'H', '-'}, 1, new int[] {12,0});
        operations[0x35] = new Operation("DEC (HL)", (CPU cpu) -> cpu.DEC_8(rf.HL), new char[] {'Z', '1', 'H', '-'}, 1, new int[] {12,0});
        operations[0x36] = new Operation("LD (HL) d8", (CPU cpu) -> cpu.LD_8(rf.HL, null, -1, d8(), true, false, false, true, false, false), new char[] {'-', '-', '-', '-'}, 2, new int[] {12,0});
        operations[0x37] = new Operation("SCF", (CPU cpu) -> cpu.SCF(), new char[] {'-', '0', '0', '1'}, 1, new int[] {4,0});
        operations[0x38] = new Operation("JR C r8", (CPU cpu) -> cpu.JR(Condition.C, r8()), new char[] {'-', '-', '-', '-'}, 2, new int[] {12,8});
        operations[0x39] = new Operation("ADD HL SP", (CPU cpu) -> cpu.ADD_16(rf.HL, rf.SP), new char[] {'-', '0', 'H', 'C'}, 1, new int[] {8,0});
        operations[0x3a] = new Operation("LD A (HL-)", (CPU cpu) -> cpu.LD_8(rf.A, rf.HL, -1, -1, false, true, false, false, false, true), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x3b] = new Operation("DEC SP", (CPU cpu) -> cpu.DEC_16(rf.SP), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x3c] = new Operation("INC A", (CPU cpu) -> cpu.INC_8(rf.A), new char[] {'Z', '0', 'H', '-'}, 1, new int[] {4,0});
        operations[0x3d] = new Operation("DEC A", (CPU cpu) -> cpu.DEC_8(rf.A), new char[] {'Z', '1', 'H', '-'}, 1, new int[] {4,0});
        operations[0x3e] = new Operation("LD A d8", (CPU cpu) -> cpu.LD_8(rf.A, null, -1, d8(), false, false, false, true, false, false), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x3f] = new Operation("CCF", (CPU cpu) -> cpu.CCF(), new char[] {'-', '0', '0', 'C'}, 1, new int[] {4,0});
        operations[0x40] = new Operation("LD B B", (CPU cpu) -> cpu.LD_8(rf.B, rf.B, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x41] = new Operation("LD B C", (CPU cpu) -> cpu.LD_8(rf.B, rf.C, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x42] = new Operation("LD B D", (CPU cpu) -> cpu.LD_8(rf.B, rf.D, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x43] = new Operation("LD B E", (CPU cpu) -> cpu.LD_8(rf.B, rf.E, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x44] = new Operation("LD B H", (CPU cpu) -> cpu.LD_8(rf.B, rf.H, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x45] = new Operation("LD B L", (CPU cpu) -> cpu.LD_8(rf.B, rf.L, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x46] = new Operation("LD B (HL)", (CPU cpu) -> cpu.LD_8(rf.B, rf.HL, -1, -1, false, true, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x47] = new Operation("LD B A", (CPU cpu) -> cpu.LD_8(rf.B, rf.A, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x48] = new Operation("LD C B", (CPU cpu) -> cpu.LD_8(rf.C, rf.B, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x49] = new Operation("LD C C", (CPU cpu) -> cpu.LD_8(rf.C, rf.C, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x4a] = new Operation("LD C D", (CPU cpu) -> cpu.LD_8(rf.C, rf.D, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x4b] = new Operation("LD C E", (CPU cpu) -> cpu.LD_8(rf.C, rf.E, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x4c] = new Operation("LD C H", (CPU cpu) -> cpu.LD_8(rf.C, rf.H, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x4d] = new Operation("LD C L", (CPU cpu) -> cpu.LD_8(rf.C, rf.L, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x4e] = new Operation("LD C (HL)", (CPU cpu) -> cpu.LD_8(rf.C, rf.HL, -1, -1, false, true, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x4f] = new Operation("LD C A", (CPU cpu) -> cpu.LD_8(rf.C, rf.A, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x50] = new Operation("LD D B", (CPU cpu) -> cpu.LD_8(rf.D, rf.B, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x51] = new Operation("LD D C", (CPU cpu) -> cpu.LD_8(rf.D, rf.C, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x52] = new Operation("LD D D", (CPU cpu) -> cpu.LD_8(rf.D, rf.D, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x53] = new Operation("LD D E", (CPU cpu) -> cpu.LD_8(rf.D, rf.E, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x54] = new Operation("LD D H", (CPU cpu) -> cpu.LD_8(rf.D, rf.H, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x55] = new Operation("LD D L", (CPU cpu) -> cpu.LD_8(rf.D, rf.L, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x56] = new Operation("LD D (HL)", (CPU cpu) -> cpu.LD_8(rf.D, rf.HL, -1, -1, false, true, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x57] = new Operation("LD D A", (CPU cpu) -> cpu.LD_8(rf.D, rf.A, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x58] = new Operation("LD E B", (CPU cpu) -> cpu.LD_8(rf.E, rf.B, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x59] = new Operation("LD E C", (CPU cpu) -> cpu.LD_8(rf.E, rf.C, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x5a] = new Operation("LD E D", (CPU cpu) -> cpu.LD_8(rf.E, rf.D, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x5b] = new Operation("LD E E", (CPU cpu) -> cpu.LD_8(rf.E, rf.E, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x5c] = new Operation("LD E H", (CPU cpu) -> cpu.LD_8(rf.E, rf.H, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x5d] = new Operation("LD E L", (CPU cpu) -> cpu.LD_8(rf.E, rf.L, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x5e] = new Operation("LD E (HL)", (CPU cpu) -> cpu.LD_8(rf.E, rf.HL, -1, -1, false, true, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x5f] = new Operation("LD E A", (CPU cpu) -> cpu.LD_8(rf.E, rf.A, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x60] = new Operation("LD H B", (CPU cpu) -> cpu.LD_8(rf.H, rf.B, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x61] = new Operation("LD H C", (CPU cpu) -> cpu.LD_8(rf.H, rf.C, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x62] = new Operation("LD H D", (CPU cpu) -> cpu.LD_8(rf.H, rf.D, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x63] = new Operation("LD H E", (CPU cpu) -> cpu.LD_8(rf.H, rf.E, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x64] = new Operation("LD H H", (CPU cpu) -> cpu.LD_8(rf.H, rf.H, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x65] = new Operation("LD H L", (CPU cpu) -> cpu.LD_8(rf.H, rf.L, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x66] = new Operation("LD H (HL)", (CPU cpu) -> cpu.LD_8(rf.H, rf.HL, -1, -1, false, true, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x67] = new Operation("LD H A", (CPU cpu) -> cpu.LD_8(rf.H, rf.A, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x68] = new Operation("LD L B", (CPU cpu) -> cpu.LD_8(rf.L, rf.B, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x69] = new Operation("LD L C", (CPU cpu) -> cpu.LD_8(rf.L, rf.C, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x6a] = new Operation("LD L D", (CPU cpu) -> cpu.LD_8(rf.L, rf.D, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x6b] = new Operation("LD L E", (CPU cpu) -> cpu.LD_8(rf.L, rf.E, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x6c] = new Operation("LD L H", (CPU cpu) -> cpu.LD_8(rf.L, rf.H, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x6d] = new Operation("LD L L", (CPU cpu) -> cpu.LD_8(rf.L, rf.L, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x6e] = new Operation("LD L (HL)", (CPU cpu) -> cpu.LD_8(rf.L, rf.HL, -1, -1, false, true, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x6f] = new Operation("LD L A", (CPU cpu) -> cpu.LD_8(rf.L, rf.A, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x70] = new Operation("LD (HL) B", (CPU cpu) -> cpu.LD_8(rf.HL, rf.B, -1, -1, true, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x71] = new Operation("LD (HL) C", (CPU cpu) -> cpu.LD_8(rf.HL, rf.C, -1, -1, true, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x72] = new Operation("LD (HL) D", (CPU cpu) -> cpu.LD_8(rf.HL, rf.D, -1, -1, true, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x73] = new Operation("LD (HL) E", (CPU cpu) -> cpu.LD_8(rf.HL, rf.E, -1, -1, true, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x74] = new Operation("LD (HL) H", (CPU cpu) -> cpu.LD_8(rf.HL, rf.H, -1, -1, true, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x75] = new Operation("LD (HL) L", (CPU cpu) -> cpu.LD_8(rf.HL, rf.L, -1, -1, true, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x76] = new Operation("HALT", (CPU cpu) -> cpu.HALT(), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x77] = new Operation("LD (HL) A", (CPU cpu) -> cpu.LD_8(rf.HL, rf.A, -1, -1, true, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x78] = new Operation("LD A B", (CPU cpu) -> cpu.LD_8(rf.A, rf.B, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x79] = new Operation("LD A C", (CPU cpu) -> cpu.LD_8(rf.A, rf.C, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x7a] = new Operation("LD A D", (CPU cpu) -> cpu.LD_8(rf.A, rf.D, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x7b] = new Operation("LD A E", (CPU cpu) -> cpu.LD_8(rf.A, rf.E, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x7c] = new Operation("LD A H", (CPU cpu) -> cpu.LD_8(rf.A, rf.H, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x7d] = new Operation("LD A L", (CPU cpu) -> cpu.LD_8(rf.A, rf.L, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x7e] = new Operation("LD A (HL)", (CPU cpu) -> cpu.LD_8(rf.A, rf.HL, -1, -1, false, true, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0x7f] = new Operation("LD A A", (CPU cpu) -> cpu.LD_8(rf.A, rf.A, -1, -1, false, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0x80] = new Operation("ADD A B", (CPU cpu) -> cpu.ADD_8(rf.A, rf.B, 0, false), new char[] {'Z', '0', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x81] = new Operation("ADD A C", (CPU cpu) -> cpu.ADD_8(rf.A, rf.C, 0, false), new char[] {'Z', '0', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x82] = new Operation("ADD A D", (CPU cpu) -> cpu.ADD_8(rf.A, rf.D, 0, false), new char[] {'Z', '0', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x83] = new Operation("ADD A E", (CPU cpu) -> cpu.ADD_8(rf.A, rf.E, 0, false), new char[] {'Z', '0', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x84] = new Operation("ADD A H", (CPU cpu) -> cpu.ADD_8(rf.A, rf.H, 0, false), new char[] {'Z', '0', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x85] = new Operation("ADD A L", (CPU cpu) -> cpu.ADD_8(rf.A, rf.L, 0, false), new char[] {'Z', '0', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x86] = new Operation("ADD A (HL)", (CPU cpu) -> cpu.ADD_8(rf.A, rf.HL, 0, false), new char[] {'Z', '0', 'H', 'C'}, 1, new int[] {8,0});
        operations[0x87] = new Operation("ADD A A", (CPU cpu) -> cpu.ADD_8(rf.A, rf.A, 0, false), new char[] {'Z', '0', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x88] = new Operation("ADC A B", (CPU cpu) -> cpu.ADC(rf.A, rf.B, 0), new char[] {'Z', '0', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x89] = new Operation("ADC A C", (CPU cpu) -> cpu.ADC(rf.A, rf.C, 0), new char[] {'Z', '0', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x8a] = new Operation("ADC A D", (CPU cpu) -> cpu.ADC(rf.A, rf.D, 0), new char[] {'Z', '0', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x8b] = new Operation("ADC A E", (CPU cpu) -> cpu.ADC(rf.A, rf.E, 0), new char[] {'Z', '0', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x8c] = new Operation("ADC A H", (CPU cpu) -> cpu.ADC(rf.A, rf.H, 0), new char[] {'Z', '0', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x8d] = new Operation("ADC A L", (CPU cpu) -> cpu.ADC(rf.A, rf.L, 0), new char[] {'Z', '0', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x8e] = new Operation("ADC A (HL)", (CPU cpu) -> cpu.ADC(rf.A, rf.HL, 0), new char[] {'Z', '0', 'H', 'C'}, 1, new int[] {8,0});
        operations[0x8f] = new Operation("ADC A A", (CPU cpu) -> cpu.ADC(rf.A, rf.A, 0), new char[] {'Z', '0', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x90] = new Operation("SUB B", (CPU cpu) -> cpu.SUB(rf.A, rf.B, 0, false, true), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x91] = new Operation("SUB C", (CPU cpu) -> cpu.SUB(rf.A, rf.C, 0, false, true), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x92] = new Operation("SUB D", (CPU cpu) -> cpu.SUB(rf.A, rf.D, 0, false, true), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x93] = new Operation("SUB E", (CPU cpu) -> cpu.SUB(rf.A, rf.E, 0, false, true), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x94] = new Operation("SUB H", (CPU cpu) -> cpu.SUB(rf.A, rf.H, 0, false, true), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x95] = new Operation("SUB L", (CPU cpu) -> cpu.SUB(rf.A, rf.L, 0, false, true), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x96] = new Operation("SUB (HL)", (CPU cpu) -> cpu.SUB(rf.A, rf.HL, 0, false, true), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {8,0});
        operations[0x97] = new Operation("SUB A", (CPU cpu) -> cpu.SUB(rf.A, rf.A, 0, false, true), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x98] = new Operation("SBC A B", (CPU cpu) -> cpu.SBC(rf.A, rf.B, 0), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x99] = new Operation("SBC A C", (CPU cpu) -> cpu.SBC(rf.A, rf.C, 0), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x9a] = new Operation("SBC A D", (CPU cpu) -> cpu.SBC(rf.A, rf.D, 0), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x9b] = new Operation("SBC A E", (CPU cpu) -> cpu.SBC(rf.A, rf.E, 0), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x9c] = new Operation("SBC A H", (CPU cpu) -> cpu.SBC(rf.A, rf.H, 0), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x9d] = new Operation("SBC A L", (CPU cpu) -> cpu.SBC(rf.A, rf.L, 0), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {4,0});
        operations[0x9e] = new Operation("SBC A (HL)", (CPU cpu) -> cpu.SBC(rf.A, rf.HL, 0), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {8,0});
        operations[0x9f] = new Operation("SBC A A", (CPU cpu) -> cpu.SBC(rf.A, rf.A, 0), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {4,0});
        operations[0xa0] = new Operation("AND B", (CPU cpu) -> cpu.AND(rf.A, rf.B, 0), new char[] {'Z', '0', '1', '0'}, 1, new int[] {4,0});
        operations[0xa1] = new Operation("AND C", (CPU cpu) -> cpu.AND(rf.A, rf.C, 0), new char[] {'Z', '0', '1', '0'}, 1, new int[] {4,0});
        operations[0xa2] = new Operation("AND D", (CPU cpu) -> cpu.AND(rf.A, rf.D, 0), new char[] {'Z', '0', '1', '0'}, 1, new int[] {4,0});
        operations[0xa3] = new Operation("AND E", (CPU cpu) -> cpu.AND(rf.A, rf.E, 0), new char[] {'Z', '0', '1', '0'}, 1, new int[] {4,0});
        operations[0xa4] = new Operation("AND H", (CPU cpu) -> cpu.AND(rf.A, rf.H, 0), new char[] {'Z', '0', '1', '0'}, 1, new int[] {4,0});
        operations[0xa5] = new Operation("AND L", (CPU cpu) -> cpu.AND(rf.A, rf.L, 0), new char[] {'Z', '0', '1', '0'}, 1, new int[] {4,0});
        operations[0xa6] = new Operation("AND (HL)", (CPU cpu) -> cpu.AND(rf.A, rf.HL, 0), new char[] {'Z', '0', '1', '0'}, 1, new int[] {8,0});
        operations[0xa7] = new Operation("AND A", (CPU cpu) -> cpu.AND(rf.A, rf.A, 0), new char[] {'Z', '0', '1', '0'}, 1, new int[] {4,0});
        operations[0xa8] = new Operation("XOR B", (CPU cpu) -> cpu.XOR(rf.A, rf.B, 0), new char[] {'Z', '0', '0', '0'}, 1, new int[] {4,0});
        operations[0xa9] = new Operation("XOR C", (CPU cpu) -> cpu.XOR(rf.A, rf.C, 0), new char[] {'Z', '0', '0', '0'}, 1, new int[] {4,0});
        operations[0xaa] = new Operation("XOR D", (CPU cpu) -> cpu.XOR(rf.A, rf.D, 0), new char[] {'Z', '0', '0', '0'}, 1, new int[] {4,0});
        operations[0xab] = new Operation("XOR E", (CPU cpu) -> cpu.XOR(rf.A, rf.E, 0), new char[] {'Z', '0', '0', '0'}, 1, new int[] {4,0});
        operations[0xac] = new Operation("XOR H", (CPU cpu) -> cpu.XOR(rf.A, rf.H, 0), new char[] {'Z', '0', '0', '0'}, 1, new int[] {4,0});
        operations[0xad] = new Operation("XOR L", (CPU cpu) -> cpu.XOR(rf.A, rf.L, 0), new char[] {'Z', '0', '0', '0'}, 1, new int[] {4,0});
        operations[0xae] = new Operation("XOR (HL)", (CPU cpu) -> cpu.XOR(rf.A, rf.HL, 0), new char[] {'Z', '0', '0', '0'}, 1, new int[] {8,0});
        operations[0xaf] = new Operation("XOR A", (CPU cpu) -> cpu.XOR(rf.A, rf.A, 0), new char[] {'Z', '0', '0', '0'}, 1, new int[] {4,0});
        operations[0xb0] = new Operation("OR B", (CPU cpu) -> cpu.OR(rf.A, rf.B, 0), new char[] {'Z', '0', '0', '0'}, 1, new int[] {4,0});
        operations[0xb1] = new Operation("OR C", (CPU cpu) -> cpu.OR(rf.A, rf.C, 0), new char[] {'Z', '0', '0', '0'}, 1, new int[] {4,0});
        operations[0xb2] = new Operation("OR D", (CPU cpu) -> cpu.OR(rf.A, rf.D, 0), new char[] {'Z', '0', '0', '0'}, 1, new int[] {4,0});
        operations[0xb3] = new Operation("OR E", (CPU cpu) -> cpu.OR(rf.A, rf.E, 0), new char[] {'Z', '0', '0', '0'}, 1, new int[] {4,0});
        operations[0xb4] = new Operation("OR H", (CPU cpu) -> cpu.OR(rf.A, rf.H, 0), new char[] {'Z', '0', '0', '0'}, 1, new int[] {4,0});
        operations[0xb5] = new Operation("OR L", (CPU cpu) -> cpu.OR(rf.A, rf.L, 0), new char[] {'Z', '0', '0', '0'}, 1, new int[] {4,0});
        operations[0xb6] = new Operation("OR (HL)", (CPU cpu) -> cpu.OR(rf.A, rf.HL, 0), new char[] {'Z', '0', '0', '0'}, 1, new int[] {8,0});
        operations[0xb7] = new Operation("OR A", (CPU cpu) -> cpu.OR(rf.A, rf.A, 0), new char[] {'Z', '0', '0', '0'}, 1, new int[] {4,0});
        operations[0xb8] = new Operation("CP B", (CPU cpu) -> cpu.CP(rf.A, rf.B, 0), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {4,0});
        operations[0xb9] = new Operation("CP C", (CPU cpu) -> cpu.CP(rf.A, rf.C, 0), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {4,0});
        operations[0xba] = new Operation("CP D", (CPU cpu) -> cpu.CP(rf.A, rf.D, 0), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {4,0});
        operations[0xbb] = new Operation("CP E", (CPU cpu) -> cpu.CP(rf.A, rf.E, 0), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {4,0});
        operations[0xbc] = new Operation("CP H", (CPU cpu) -> cpu.CP(rf.A, rf.H, 0), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {4,0});
        operations[0xbd] = new Operation("CP L", (CPU cpu) -> cpu.CP(rf.A, rf.L, 0), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {4,0});
        operations[0xbe] = new Operation("CP (HL)", (CPU cpu) -> cpu.CP(rf.A, rf.HL, 0), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {8,0});
        operations[0xbf] = new Operation("CP A", (CPU cpu) -> cpu.CP(rf.A, rf.A, 0), new char[] {'Z', '1', 'H', 'C'}, 1, new int[] {4,0});
        operations[0xc0] = new Operation("RET NZ", (CPU cpu) -> cpu.RET(Condition.NZ, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {20,8});
        operations[0xc1] = new Operation("POP BC", (CPU cpu) -> cpu.POP(rf.BC), new char[] {'-', '-', '-', '-'}, 1, new int[] {12,0});
        operations[0xc2] = new Operation("JP NZ a16", (CPU cpu) -> cpu.JP(null, Condition.NZ, a16()), new char[] {'-', '-', '-', '-'}, 3, new int[] {16,12});
        operations[0xc3] = new Operation("JP a16", (CPU cpu) -> cpu.JP(null, Condition.NONE, a16()), new char[] {'-', '-', '-', '-'}, 3, new int[] {16,0});
        operations[0xc4] = new Operation("CALL NZ a16", (CPU cpu) -> cpu.CALL(Condition.NZ, a16()), new char[] {'-', '-', '-', '-'}, 3, new int[] {24,12});
        operations[0xc5] = new Operation("PUSH BC", (CPU cpu) -> cpu.PUSH(rf.BC), new char[] {'-', '-', '-', '-'}, 1, new int[] {16,0});
        operations[0xc6] = new Operation("ADD A d8", (CPU cpu) -> cpu.ADD_8(rf.A, null, d8(), false), new char[] {'Z', '0', 'H', 'C'}, 2, new int[] {8,0});
        operations[0xc7] = new Operation("RST 00H", (CPU cpu) -> cpu.RST(0x00), new char[] {'-', '-', '-', '-'}, 1, new int[] {16,0});
        operations[0xc8] = new Operation("RET Z", (CPU cpu) -> cpu.RET(Condition.Z, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {20,8});
        operations[0xc9] = new Operation("RET", (CPU cpu) -> cpu.RET(Condition.NONE, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {16,0});
        operations[0xca] = new Operation("JP Z a16", (CPU cpu) -> cpu.JP(null, Condition.Z, a16()), new char[] {'-', '-', '-', '-'}, 3, new int[] {16,12});
        operations[0xcc] = new Operation("CALL Z a16", (CPU cpu) -> cpu.CALL(Condition.Z, a16()), new char[] {'-', '-', '-', '-'}, 3, new int[] {24,12});
        operations[0xcd] = new Operation("CALL a16", (CPU cpu) -> cpu.CALL(Condition.NONE, a16()), new char[] {'-', '-', '-', '-'}, 3, new int[] {24,0});
        operations[0xce] = new Operation("ADC A d8", (CPU cpu) -> cpu.ADC(rf.A, null, d8()), new char[] {'Z', '0', 'H', 'C'}, 2, new int[] {8,0});
        operations[0xcf] = new Operation("RST 08H", (CPU cpu) -> cpu.RST(0x08), new char[] {'-', '-', '-', '-'}, 1, new int[] {16,0});
        operations[0xd0] = new Operation("RET NC", (CPU cpu) -> cpu.RET(Condition.NC, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {20,8});
        operations[0xd1] = new Operation("POP DE", (CPU cpu) -> cpu.POP(rf.DE), new char[] {'-', '-', '-', '-'}, 1, new int[] {12,0});
        operations[0xd2] = new Operation("JP NC a16", (CPU cpu) -> cpu.JP(null, Condition.NC, a16()), new char[] {'-', '-', '-', '-'}, 3, new int[] {16,12});
        operations[0xd4] = new Operation("CALL NC a16", (CPU cpu) -> cpu.CALL(Condition.NC, a16()), new char[] {'-', '-', '-', '-'}, 3, new int[] {24,12});
        operations[0xd5] = new Operation("PUSH DE", (CPU cpu) -> cpu.PUSH(rf.DE), new char[] {'-', '-', '-', '-'}, 1, new int[] {16,0});
        operations[0xd6] = new Operation("SUB d8", (CPU cpu) -> cpu.SUB(rf.A, null, d8(), false, true), new char[] {'Z', '1', 'H', 'C'}, 2, new int[] {8,0});
        operations[0xd7] = new Operation("RST 10H", (CPU cpu) -> cpu.RST(0x10), new char[] {'-', '-', '-', '-'}, 1, new int[] {16,0});
        operations[0xd8] = new Operation("RET C", (CPU cpu) -> cpu.RET(Condition.C, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {20,8});
        operations[0xd9] = new Operation("RETI", (CPU cpu) -> cpu.RET(Condition.NONE, true), new char[] {'-', '-', '-', '-'}, 1, new int[] {16,0});
        operations[0xda] = new Operation("JP C a16", (CPU cpu) -> cpu.JP(null, Condition.C, a16()), new char[] {'-', '-', '-', '-'}, 3, new int[] {16,12});
        operations[0xdc] = new Operation("CALL C a16", (CPU cpu) -> cpu.CALL(Condition.C, a16()), new char[] {'-', '-', '-', '-'}, 3, new int[] {24,12});
        operations[0xde] = new Operation("SBC A d8", (CPU cpu) -> cpu.SBC(rf.A, null, d8()), new char[] {'Z', '1', 'H', 'C'}, 2, new int[] {8,0});
        operations[0xdf] = new Operation("RST 18H", (CPU cpu) -> cpu.RST(0x18), new char[] {'-', '-', '-', '-'}, 1, new int[] {16,0});
        operations[0xe0] = new Operation("LDH (a8) A", (CPU cpu) -> cpu.LD_8(null, rf.A, a8(), -1, true, false, true, false, false, false), new char[] {'-', '-', '-', '-'}, 2, new int[] {12,0});
        operations[0xe1] = new Operation("POP HL", (CPU cpu) -> cpu.POP(rf.HL), new char[] {'-', '-', '-', '-'}, 1, new int[] {12,0});
        operations[0xe2] = new Operation("LD (C) A", (CPU cpu) -> cpu.LD_8(rf.C, rf.A, -1, -1, true, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0xe5] = new Operation("PUSH HL", (CPU cpu) -> cpu.PUSH(rf.HL), new char[] {'-', '-', '-', '-'}, 1, new int[] {16,0});
        operations[0xe6] = new Operation("AND d8", (CPU cpu) -> cpu.AND(rf.A, null, d8()), new char[] {'Z', '0', '1', '0'}, 2, new int[] {8,0});
        operations[0xe7] = new Operation("RST 20H", (CPU cpu) -> cpu.RST(0x20), new char[] {'-', '-', '-', '-'}, 1, new int[] {16,0});
        operations[0xe8] = new Operation("ADD SP r8", (CPU cpu) -> cpu.ADD_SP(r8()), new char[] {'0', '0', 'H', 'C'}, 2, new int[] {16,0});
        operations[0xe9] = new Operation("JP (HL)", (CPU cpu) -> cpu.JP(rf.HL, Condition.NONE, -1), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0xea] = new Operation("LD (a16) A", (CPU cpu) -> cpu.LD_8(null, rf.A, a16(), -1, true, false, false, false, false, false), new char[] {'-', '-', '-', '-'}, 3, new int[] {16,0});
        operations[0xee] = new Operation("XOR d8", (CPU cpu) -> cpu.XOR(rf.A, null, d8()), new char[] {'Z', '0', '0', '0'}, 2, new int[] {8,0});
        operations[0xef] = new Operation("RST 28H", (CPU cpu) -> cpu.RST(0x28), new char[] {'-', '-', '-', '-'}, 1, new int[] {16,0});
        operations[0xf0] = new Operation("LDH A (a8)", (CPU cpu) -> cpu.LD_8(rf.A, null, -1, a8(), false, true, false, true, false, false), new char[] {'-', '-', '-', '-'}, 2, new int[] {12,0});
        operations[0xf1] = new Operation("POP AF", (CPU cpu) -> cpu.POP(rf.AF), new char[] {'Z', 'N', 'H', 'C'}, 1, new int[] {12,0});
        operations[0xf2] = new Operation("LD A (C)", (CPU cpu) -> cpu.LD_8(rf.A, rf.C, -1, -1, false, true, false, false, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0xf3] = new Operation("DI", (CPU cpu) -> cpu.DI(), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0xf5] = new Operation("PUSH AF", (CPU cpu) -> cpu.PUSH(rf.AF), new char[] {'-', '-', '-', '-'}, 1, new int[] {16,0});
        operations[0xf6] = new Operation("OR d8", (CPU cpu) -> cpu.OR(rf.A, null, d8()), new char[] {'Z', '0', '0', '0'}, 2, new int[] {8,0});
        operations[0xf7] = new Operation("RST 30H", (CPU cpu) -> cpu.RST(0x30), new char[] {'-', '-', '-', '-'}, 1, new int[] {16,0});
        operations[0xf8] = new Operation("LD HL SP+r8", (CPU cpu) -> cpu.LD_16(rf.HL, null, -1, r8(), false, true), new char[] {'0', '0', 'H', 'C'}, 2, new int[] {12,0});
        operations[0xf9] = new Operation("LD SP HL", (CPU cpu) -> cpu.LD_16(rf.SP, rf.HL, -1, -1, false, false), new char[] {'-', '-', '-', '-'}, 1, new int[] {8,0});
        operations[0xfa] = new Operation("LD A (a16)", (CPU cpu) -> cpu.LD_8(rf.A, null, -1, a16(), false, true, false, false, false, false), new char[] {'-', '-', '-', '-'}, 3, new int[] {16,0});
        operations[0xfb] = new Operation("EI", (CPU cpu) -> cpu.EI(), new char[] {'-', '-', '-', '-'}, 1, new int[] {4,0});
        operations[0xfe] = new Operation("CP d8", (CPU cpu) -> cpu.CP(rf.A, null, d8()), new char[] {'Z', '1', 'H', 'C'}, 2, new int[] {8,0});
        operations[0xff] = new Operation("RST 38H", (CPU cpu) -> cpu.RST(0x38), new char[] {'-', '-', '-', '-'}, 1, new int[] {16,0});
        operations[0x100+0x00] = new Operation("RLC B", (CPU cpu) -> cpu.RLC(rf.B), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x01] = new Operation("RLC C", (CPU cpu) -> cpu.RLC(rf.C), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x02] = new Operation("RLC D", (CPU cpu) -> cpu.RLC(rf.D), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x03] = new Operation("RLC E", (CPU cpu) -> cpu.RLC(rf.E), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x04] = new Operation("RLC H", (CPU cpu) -> cpu.RLC(rf.H), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x05] = new Operation("RLC L", (CPU cpu) -> cpu.RLC(rf.L), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x06] = new Operation("RLC (HL)", (CPU cpu) -> cpu.RLC(rf.HL), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {16,0});
        operations[0x100+0x07] = new Operation("RLC A", (CPU cpu) -> cpu.RLC(rf.A), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x08] = new Operation("RRC B", (CPU cpu) -> cpu.RRC(rf.B), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x09] = new Operation("RRC C", (CPU cpu) -> cpu.RRC(rf.C), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x0a] = new Operation("RRC D", (CPU cpu) -> cpu.RRC(rf.D), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x0b] = new Operation("RRC E", (CPU cpu) -> cpu.RRC(rf.E), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x0c] = new Operation("RRC H", (CPU cpu) -> cpu.RRC(rf.H), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x0d] = new Operation("RRC L", (CPU cpu) -> cpu.RRC(rf.L), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x0e] = new Operation("RRC (HL)", (CPU cpu) -> cpu.RRC(rf.HL), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {16,0});
        operations[0x100+0x0f] = new Operation("RRC A", (CPU cpu) -> cpu.RRC(rf.A), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x10] = new Operation("RL B", (CPU cpu) -> cpu.RL(rf.B), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x11] = new Operation("RL C", (CPU cpu) -> cpu.RL(rf.C), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x12] = new Operation("RL D", (CPU cpu) -> cpu.RL(rf.D), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x13] = new Operation("RL E", (CPU cpu) -> cpu.RL(rf.E), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x14] = new Operation("RL H", (CPU cpu) -> cpu.RL(rf.H), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x15] = new Operation("RL L", (CPU cpu) -> cpu.RL(rf.L), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x16] = new Operation("RL (HL)", (CPU cpu) -> cpu.RL(rf.HL), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {16,0});
        operations[0x100+0x17] = new Operation("RL A", (CPU cpu) -> cpu.RL(rf.A), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x18] = new Operation("RR B", (CPU cpu) -> cpu.RR(rf.B), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x19] = new Operation("RR C", (CPU cpu) -> cpu.RR(rf.C), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x1a] = new Operation("RR D", (CPU cpu) -> cpu.RR(rf.D), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x1b] = new Operation("RR E", (CPU cpu) -> cpu.RR(rf.E), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x1c] = new Operation("RR H", (CPU cpu) -> cpu.RR(rf.H), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x1d] = new Operation("RR L", (CPU cpu) -> cpu.RR(rf.L), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x1e] = new Operation("RR (HL)", (CPU cpu) -> cpu.RR(rf.HL), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {16,0});
        operations[0x100+0x1f] = new Operation("RR A", (CPU cpu) -> cpu.RR(rf.A), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x20] = new Operation("SLA B", (CPU cpu) -> cpu.SLA(rf.B), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x21] = new Operation("SLA C", (CPU cpu) -> cpu.SLA(rf.C), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x22] = new Operation("SLA D", (CPU cpu) -> cpu.SLA(rf.D), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x23] = new Operation("SLA E", (CPU cpu) -> cpu.SLA(rf.E), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x24] = new Operation("SLA H", (CPU cpu) -> cpu.SLA(rf.H), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x25] = new Operation("SLA L", (CPU cpu) -> cpu.SLA(rf.L), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x26] = new Operation("SLA (HL)", (CPU cpu) -> cpu.SLA(rf.HL), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {16,0});
        operations[0x100+0x27] = new Operation("SLA A", (CPU cpu) -> cpu.SLA(rf.A), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x28] = new Operation("SRA B", (CPU cpu) -> cpu.SRA(rf.B), new char[] {'Z', '0', '0', '0'}, 2, new int[] {8,0});
        operations[0x100+0x29] = new Operation("SRA C", (CPU cpu) -> cpu.SRA(rf.C), new char[] {'Z', '0', '0', '0'}, 2, new int[] {8,0});
        operations[0x100+0x2a] = new Operation("SRA D", (CPU cpu) -> cpu.SRA(rf.D), new char[] {'Z', '0', '0', '0'}, 2, new int[] {8,0});
        operations[0x100+0x2b] = new Operation("SRA E", (CPU cpu) -> cpu.SRA(rf.E), new char[] {'Z', '0', '0', '0'}, 2, new int[] {8,0});
        operations[0x100+0x2c] = new Operation("SRA H", (CPU cpu) -> cpu.SRA(rf.H), new char[] {'Z', '0', '0', '0'}, 2, new int[] {8,0});
        operations[0x100+0x2d] = new Operation("SRA L", (CPU cpu) -> cpu.SRA(rf.L), new char[] {'Z', '0', '0', '0'}, 2, new int[] {8,0});
        operations[0x100+0x2e] = new Operation("SRA (HL)", (CPU cpu) -> cpu.SRA(rf.HL), new char[] {'Z', '0', '0', '0'}, 2, new int[] {16,0});
        operations[0x100+0x2f] = new Operation("SRA A", (CPU cpu) -> cpu.SRA(rf.A), new char[] {'Z', '0', '0', '0'}, 2, new int[] {8,0});
        operations[0x100+0x30] = new Operation("SWAP B", (CPU cpu) -> cpu.SWAP(rf.B), new char[] {'Z', '0', '0', '0'}, 2, new int[] {8,0});
        operations[0x100+0x31] = new Operation("SWAP C", (CPU cpu) -> cpu.SWAP(rf.C), new char[] {'Z', '0', '0', '0'}, 2, new int[] {8,0});
        operations[0x100+0x32] = new Operation("SWAP D", (CPU cpu) -> cpu.SWAP(rf.D), new char[] {'Z', '0', '0', '0'}, 2, new int[] {8,0});
        operations[0x100+0x33] = new Operation("SWAP E", (CPU cpu) -> cpu.SWAP(rf.E), new char[] {'Z', '0', '0', '0'}, 2, new int[] {8,0});
        operations[0x100+0x34] = new Operation("SWAP H", (CPU cpu) -> cpu.SWAP(rf.H), new char[] {'Z', '0', '0', '0'}, 2, new int[] {8,0});
        operations[0x100+0x35] = new Operation("SWAP L", (CPU cpu) -> cpu.SWAP(rf.L), new char[] {'Z', '0', '0', '0'}, 2, new int[] {8,0});
        operations[0x100+0x36] = new Operation("SWAP (HL)", (CPU cpu) -> cpu.SWAP(rf.HL), new char[] {'Z', '0', '0', '0'}, 2, new int[] {16,0});
        operations[0x100+0x37] = new Operation("SWAP A", (CPU cpu) -> cpu.SWAP(rf.A), new char[] {'Z', '0', '0', '0'}, 2, new int[] {8,0});
        operations[0x100+0x38] = new Operation("SRL B", (CPU cpu) -> cpu.SRL(rf.B), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x39] = new Operation("SRL C", (CPU cpu) -> cpu.SRL(rf.C), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x3a] = new Operation("SRL D", (CPU cpu) -> cpu.SRL(rf.D), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x3b] = new Operation("SRL E", (CPU cpu) -> cpu.SRL(rf.E), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x3c] = new Operation("SRL H", (CPU cpu) -> cpu.SRL(rf.H), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x3d] = new Operation("SRL L", (CPU cpu) -> cpu.SRL(rf.L), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x3e] = new Operation("SRL (HL)", (CPU cpu) -> cpu.SRL(rf.HL), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {16,0});
        operations[0x100+0x3f] = new Operation("SRL A", (CPU cpu) -> cpu.SRL(rf.A), new char[] {'Z', '0', '0', 'C'}, 2, new int[] {8,0});
        operations[0x100+0x40] = new Operation("BIT 0 B", (CPU cpu) -> cpu.BIT(rf.B, 0), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x41] = new Operation("BIT 0 C", (CPU cpu) -> cpu.BIT(rf.C, 0), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x42] = new Operation("BIT 0 D", (CPU cpu) -> cpu.BIT(rf.D, 0), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x43] = new Operation("BIT 0 E", (CPU cpu) -> cpu.BIT(rf.E, 0), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x44] = new Operation("BIT 0 H", (CPU cpu) -> cpu.BIT(rf.H, 0), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x45] = new Operation("BIT 0 L", (CPU cpu) -> cpu.BIT(rf.L, 0), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x46] = new Operation("BIT 0 (HL)", (CPU cpu) -> cpu.BIT(rf.HL, 0), new char[] {'Z', '0', '1', '-'}, 2, new int[] {16,0});
        operations[0x100+0x47] = new Operation("BIT 0 A", (CPU cpu) -> cpu.BIT(rf.A, 0), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x48] = new Operation("BIT 1 B", (CPU cpu) -> cpu.BIT(rf.B, 1), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x49] = new Operation("BIT 1 C", (CPU cpu) -> cpu.BIT(rf.C, 1), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x4a] = new Operation("BIT 1 D", (CPU cpu) -> cpu.BIT(rf.D, 1), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x4b] = new Operation("BIT 1 E", (CPU cpu) -> cpu.BIT(rf.E, 1), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x4c] = new Operation("BIT 1 H", (CPU cpu) -> cpu.BIT(rf.H, 1), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x4d] = new Operation("BIT 1 L", (CPU cpu) -> cpu.BIT(rf.L, 1), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x4e] = new Operation("BIT 1 (HL)", (CPU cpu) -> cpu.BIT(rf.HL, 1), new char[] {'Z', '0', '1', '-'}, 2, new int[] {16,0});
        operations[0x100+0x4f] = new Operation("BIT 1 A", (CPU cpu) -> cpu.BIT(rf.A, 1), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x50] = new Operation("BIT 2 B", (CPU cpu) -> cpu.BIT(rf.B, 2), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x51] = new Operation("BIT 2 C", (CPU cpu) -> cpu.BIT(rf.C, 2), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x52] = new Operation("BIT 2 D", (CPU cpu) -> cpu.BIT(rf.D, 2), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x53] = new Operation("BIT 2 E", (CPU cpu) -> cpu.BIT(rf.E, 2), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x54] = new Operation("BIT 2 H", (CPU cpu) -> cpu.BIT(rf.H, 2), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x55] = new Operation("BIT 2 L", (CPU cpu) -> cpu.BIT(rf.L, 2), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x56] = new Operation("BIT 2 (HL)", (CPU cpu) -> cpu.BIT(rf.HL, 2), new char[] {'Z', '0', '1', '-'}, 2, new int[] {16,0});
        operations[0x100+0x57] = new Operation("BIT 2 A", (CPU cpu) -> cpu.BIT(rf.A, 2), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x58] = new Operation("BIT 3 B", (CPU cpu) -> cpu.BIT(rf.B, 3), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x59] = new Operation("BIT 3 C", (CPU cpu) -> cpu.BIT(rf.C, 3), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x5a] = new Operation("BIT 3 D", (CPU cpu) -> cpu.BIT(rf.D, 3), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x5b] = new Operation("BIT 3 E", (CPU cpu) -> cpu.BIT(rf.E, 3), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x5c] = new Operation("BIT 3 H", (CPU cpu) -> cpu.BIT(rf.H, 3), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x5d] = new Operation("BIT 3 L", (CPU cpu) -> cpu.BIT(rf.L, 3), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x5e] = new Operation("BIT 3 (HL)", (CPU cpu) -> cpu.BIT(rf.HL, 3), new char[] {'Z', '0', '1', '-'}, 2, new int[] {16,0});
        operations[0x100+0x5f] = new Operation("BIT 3 A", (CPU cpu) -> cpu.BIT(rf.A, 3), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x60] = new Operation("BIT 4 B", (CPU cpu) -> cpu.BIT(rf.B, 4), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x61] = new Operation("BIT 4 C", (CPU cpu) -> cpu.BIT(rf.C, 4), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x62] = new Operation("BIT 4 D", (CPU cpu) -> cpu.BIT(rf.D, 4), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x63] = new Operation("BIT 4 E", (CPU cpu) -> cpu.BIT(rf.E, 4), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x64] = new Operation("BIT 4 H", (CPU cpu) -> cpu.BIT(rf.H, 4), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x65] = new Operation("BIT 4 L", (CPU cpu) -> cpu.BIT(rf.L, 4), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x66] = new Operation("BIT 4 (HL)", (CPU cpu) -> cpu.BIT(rf.HL, 4), new char[] {'Z', '0', '1', '-'}, 2, new int[] {16,0});
        operations[0x100+0x67] = new Operation("BIT 4 A", (CPU cpu) -> cpu.BIT(rf.A, 4), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x68] = new Operation("BIT 5 B", (CPU cpu) -> cpu.BIT(rf.B, 5), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x69] = new Operation("BIT 5 C", (CPU cpu) -> cpu.BIT(rf.C, 5), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x6a] = new Operation("BIT 5 D", (CPU cpu) -> cpu.BIT(rf.D, 5), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x6b] = new Operation("BIT 5 E", (CPU cpu) -> cpu.BIT(rf.E, 5), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x6c] = new Operation("BIT 5 H", (CPU cpu) -> cpu.BIT(rf.H, 5), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x6d] = new Operation("BIT 5 L", (CPU cpu) -> cpu.BIT(rf.L, 5), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x6e] = new Operation("BIT 5 (HL)", (CPU cpu) -> cpu.BIT(rf.HL, 5), new char[] {'Z', '0', '1', '-'}, 2, new int[] {16,0});
        operations[0x100+0x6f] = new Operation("BIT 5 A", (CPU cpu) -> cpu.BIT(rf.A, 5), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x70] = new Operation("BIT 6 B", (CPU cpu) -> cpu.BIT(rf.B, 6), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x71] = new Operation("BIT 6 C", (CPU cpu) -> cpu.BIT(rf.C, 6), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x72] = new Operation("BIT 6 D", (CPU cpu) -> cpu.BIT(rf.D, 6), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x73] = new Operation("BIT 6 E", (CPU cpu) -> cpu.BIT(rf.E, 6), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x74] = new Operation("BIT 6 H", (CPU cpu) -> cpu.BIT(rf.H, 6), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x75] = new Operation("BIT 6 L", (CPU cpu) -> cpu.BIT(rf.L, 6), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x76] = new Operation("BIT 6 (HL)", (CPU cpu) -> cpu.BIT(rf.HL, 6), new char[] {'Z', '0', '1', '-'}, 2, new int[] {16,0});
        operations[0x100+0x77] = new Operation("BIT 6 A", (CPU cpu) -> cpu.BIT(rf.A, 6), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x78] = new Operation("BIT 7 B", (CPU cpu) -> cpu.BIT(rf.B, 7), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x79] = new Operation("BIT 7 C", (CPU cpu) -> cpu.BIT(rf.C, 7), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x7a] = new Operation("BIT 7 D", (CPU cpu) -> cpu.BIT(rf.D, 7), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x7b] = new Operation("BIT 7 E", (CPU cpu) -> cpu.BIT(rf.E, 7), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x7c] = new Operation("BIT 7 H", (CPU cpu) -> cpu.BIT(rf.H, 7), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x7d] = new Operation("BIT 7 L", (CPU cpu) -> cpu.BIT(rf.L, 7), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x7e] = new Operation("BIT 7 (HL)", (CPU cpu) -> cpu.BIT(rf.HL, 7), new char[] {'Z', '0', '1', '-'}, 2, new int[] {16,0});
        operations[0x100+0x7f] = new Operation("BIT 7 A", (CPU cpu) -> cpu.BIT(rf.A, 7), new char[] {'Z', '0', '1', '-'}, 2, new int[] {8,0});
        operations[0x100+0x80] = new Operation("RES 0 B", (CPU cpu) -> cpu.RES(rf.B, 0), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x81] = new Operation("RES 0 C", (CPU cpu) -> cpu.RES(rf.C, 0), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x82] = new Operation("RES 0 D", (CPU cpu) -> cpu.RES(rf.D, 0), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x83] = new Operation("RES 0 E", (CPU cpu) -> cpu.RES(rf.E, 0), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x84] = new Operation("RES 0 H", (CPU cpu) -> cpu.RES(rf.H, 0), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x85] = new Operation("RES 0 L", (CPU cpu) -> cpu.RES(rf.L, 0), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x86] = new Operation("RES 0 (HL)", (CPU cpu) -> cpu.RES(rf.HL, 0), new char[] {'-', '-', '-', '-'}, 2, new int[] {16,0});
        operations[0x100+0x87] = new Operation("RES 0 A", (CPU cpu) -> cpu.RES(rf.A, 0), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x88] = new Operation("RES 1 B", (CPU cpu) -> cpu.RES(rf.B, 1), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x89] = new Operation("RES 1 C", (CPU cpu) -> cpu.RES(rf.C, 1), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x8a] = new Operation("RES 1 D", (CPU cpu) -> cpu.RES(rf.D, 1), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x8b] = new Operation("RES 1 E", (CPU cpu) -> cpu.RES(rf.E, 1), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x8c] = new Operation("RES 1 H", (CPU cpu) -> cpu.RES(rf.H, 1), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x8d] = new Operation("RES 1 L", (CPU cpu) -> cpu.RES(rf.L, 1), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x8e] = new Operation("RES 1 (HL)", (CPU cpu) -> cpu.RES(rf.HL, 1), new char[] {'-', '-', '-', '-'}, 2, new int[] {16,0});
        operations[0x100+0x8f] = new Operation("RES 1 A", (CPU cpu) -> cpu.RES(rf.A, 1), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x90] = new Operation("RES 2 B", (CPU cpu) -> cpu.RES(rf.B, 2), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x91] = new Operation("RES 2 C", (CPU cpu) -> cpu.RES(rf.C, 2), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x92] = new Operation("RES 2 D", (CPU cpu) -> cpu.RES(rf.D, 2), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x93] = new Operation("RES 2 E", (CPU cpu) -> cpu.RES(rf.E, 2), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x94] = new Operation("RES 2 H", (CPU cpu) -> cpu.RES(rf.H, 2), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x95] = new Operation("RES 2 L", (CPU cpu) -> cpu.RES(rf.L, 2), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x96] = new Operation("RES 2 (HL)", (CPU cpu) -> cpu.RES(rf.HL, 2), new char[] {'-', '-', '-', '-'}, 2, new int[] {16,0});
        operations[0x100+0x97] = new Operation("RES 2 A", (CPU cpu) -> cpu.RES(rf.A, 2), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x98] = new Operation("RES 3 B", (CPU cpu) -> cpu.RES(rf.B, 3), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x99] = new Operation("RES 3 C", (CPU cpu) -> cpu.RES(rf.C, 3), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x9a] = new Operation("RES 3 D", (CPU cpu) -> cpu.RES(rf.D, 3), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x9b] = new Operation("RES 3 E", (CPU cpu) -> cpu.RES(rf.E, 3), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x9c] = new Operation("RES 3 H", (CPU cpu) -> cpu.RES(rf.H, 3), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x9d] = new Operation("RES 3 L", (CPU cpu) -> cpu.RES(rf.L, 3), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0x9e] = new Operation("RES 3 (HL)", (CPU cpu) -> cpu.RES(rf.HL, 3), new char[] {'-', '-', '-', '-'}, 2, new int[] {16,0});
        operations[0x100+0x9f] = new Operation("RES 3 A", (CPU cpu) -> cpu.RES(rf.A, 3), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xa0] = new Operation("RES 4 B", (CPU cpu) -> cpu.RES(rf.B, 4), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xa1] = new Operation("RES 4 C", (CPU cpu) -> cpu.RES(rf.C, 4), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xa2] = new Operation("RES 4 D", (CPU cpu) -> cpu.RES(rf.D, 4), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xa3] = new Operation("RES 4 E", (CPU cpu) -> cpu.RES(rf.E, 4), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xa4] = new Operation("RES 4 H", (CPU cpu) -> cpu.RES(rf.H, 4), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xa5] = new Operation("RES 4 L", (CPU cpu) -> cpu.RES(rf.L, 4), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xa6] = new Operation("RES 4 (HL)", (CPU cpu) -> cpu.RES(rf.HL, 4), new char[] {'-', '-', '-', '-'}, 2, new int[] {16,0});
        operations[0x100+0xa7] = new Operation("RES 4 A", (CPU cpu) -> cpu.RES(rf.A, 4), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xa8] = new Operation("RES 5 B", (CPU cpu) -> cpu.RES(rf.B, 5), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xa9] = new Operation("RES 5 C", (CPU cpu) -> cpu.RES(rf.C, 5), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xaa] = new Operation("RES 5 D", (CPU cpu) -> cpu.RES(rf.D, 5), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xab] = new Operation("RES 5 E", (CPU cpu) -> cpu.RES(rf.E, 5), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xac] = new Operation("RES 5 H", (CPU cpu) -> cpu.RES(rf.H, 5), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xad] = new Operation("RES 5 L", (CPU cpu) -> cpu.RES(rf.L, 5), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xae] = new Operation("RES 5 (HL)", (CPU cpu) -> cpu.RES(rf.HL, 5), new char[] {'-', '-', '-', '-'}, 2, new int[] {16,0});
        operations[0x100+0xaf] = new Operation("RES 5 A", (CPU cpu) -> cpu.RES(rf.A, 5), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xb0] = new Operation("RES 6 B", (CPU cpu) -> cpu.RES(rf.B, 6), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xb1] = new Operation("RES 6 C", (CPU cpu) -> cpu.RES(rf.C, 6), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xb2] = new Operation("RES 6 D", (CPU cpu) -> cpu.RES(rf.D, 6), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xb3] = new Operation("RES 6 E", (CPU cpu) -> cpu.RES(rf.E, 6), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xb4] = new Operation("RES 6 H", (CPU cpu) -> cpu.RES(rf.H, 6), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xb5] = new Operation("RES 6 L", (CPU cpu) -> cpu.RES(rf.L, 6), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xb6] = new Operation("RES 6 (HL)", (CPU cpu) -> cpu.RES(rf.HL, 6), new char[] {'-', '-', '-', '-'}, 2, new int[] {16,0});
        operations[0x100+0xb7] = new Operation("RES 6 A", (CPU cpu) -> cpu.RES(rf.A, 6), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xb8] = new Operation("RES 7 B", (CPU cpu) -> cpu.RES(rf.B, 7), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xb9] = new Operation("RES 7 C", (CPU cpu) -> cpu.RES(rf.C, 7), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xba] = new Operation("RES 7 D", (CPU cpu) -> cpu.RES(rf.D, 7), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xbb] = new Operation("RES 7 E", (CPU cpu) -> cpu.RES(rf.E, 7), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xbc] = new Operation("RES 7 H", (CPU cpu) -> cpu.RES(rf.H, 7), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xbd] = new Operation("RES 7 L", (CPU cpu) -> cpu.RES(rf.L, 7), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xbe] = new Operation("RES 7 (HL)", (CPU cpu) -> cpu.RES(rf.HL, 7), new char[] {'-', '-', '-', '-'}, 2, new int[] {16,0});
        operations[0x100+0xbf] = new Operation("RES 7 A", (CPU cpu) -> cpu.RES(rf.A, 7), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xc0] = new Operation("SET 0 B", (CPU cpu) -> cpu.SET(rf.B, 0), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xc1] = new Operation("SET 0 C", (CPU cpu) -> cpu.SET(rf.C, 0), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xc2] = new Operation("SET 0 D", (CPU cpu) -> cpu.SET(rf.D, 0), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xc3] = new Operation("SET 0 E", (CPU cpu) -> cpu.SET(rf.E, 0), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xc4] = new Operation("SET 0 H", (CPU cpu) -> cpu.SET(rf.H, 0), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xc5] = new Operation("SET 0 L", (CPU cpu) -> cpu.SET(rf.L, 0), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xc6] = new Operation("SET 0 (HL)", (CPU cpu) -> cpu.SET(rf.HL, 0), new char[] {'-', '-', '-', '-'}, 2, new int[] {16,0});
        operations[0x100+0xc7] = new Operation("SET 0 A", (CPU cpu) -> cpu.SET(rf.A, 0), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xc8] = new Operation("SET 1 B", (CPU cpu) -> cpu.SET(rf.B, 1), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xc9] = new Operation("SET 1 C", (CPU cpu) -> cpu.SET(rf.C, 1), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xca] = new Operation("SET 1 D", (CPU cpu) -> cpu.SET(rf.D, 1), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xcb] = new Operation("SET 1 E", (CPU cpu) -> cpu.SET(rf.E, 1), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xcc] = new Operation("SET 1 H", (CPU cpu) -> cpu.SET(rf.H, 1), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xcd] = new Operation("SET 1 L", (CPU cpu) -> cpu.SET(rf.L, 1), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xce] = new Operation("SET 1 (HL)", (CPU cpu) -> cpu.SET(rf.HL, 1), new char[] {'-', '-', '-', '-'}, 2, new int[] {16,0});
        operations[0x100+0xcf] = new Operation("SET 1 A", (CPU cpu) -> cpu.SET(rf.A, 1), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xd0] = new Operation("SET 2 B", (CPU cpu) -> cpu.SET(rf.B, 2), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xd1] = new Operation("SET 2 C", (CPU cpu) -> cpu.SET(rf.C, 2), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xd2] = new Operation("SET 2 D", (CPU cpu) -> cpu.SET(rf.D, 2), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xd3] = new Operation("SET 2 E", (CPU cpu) -> cpu.SET(rf.E, 2), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xd4] = new Operation("SET 2 H", (CPU cpu) -> cpu.SET(rf.H, 2), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xd5] = new Operation("SET 2 L", (CPU cpu) -> cpu.SET(rf.L, 2), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xd6] = new Operation("SET 2 (HL)", (CPU cpu) -> cpu.SET(rf.HL, 2), new char[] {'-', '-', '-', '-'}, 2, new int[] {16,0});
        operations[0x100+0xd7] = new Operation("SET 2 A", (CPU cpu) -> cpu.SET(rf.A, 2), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xd8] = new Operation("SET 3 B", (CPU cpu) -> cpu.SET(rf.B, 3), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xd9] = new Operation("SET 3 C", (CPU cpu) -> cpu.SET(rf.C, 3), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xda] = new Operation("SET 3 D", (CPU cpu) -> cpu.SET(rf.D, 3), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xdb] = new Operation("SET 3 E", (CPU cpu) -> cpu.SET(rf.E, 3), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xdc] = new Operation("SET 3 H", (CPU cpu) -> cpu.SET(rf.H, 3), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xdd] = new Operation("SET 3 L", (CPU cpu) -> cpu.SET(rf.L, 3), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xde] = new Operation("SET 3 (HL)", (CPU cpu) -> cpu.SET(rf.HL, 3), new char[] {'-', '-', '-', '-'}, 2, new int[] {16,0});
        operations[0x100+0xdf] = new Operation("SET 3 A", (CPU cpu) -> cpu.SET(rf.A, 3), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xe0] = new Operation("SET 4 B", (CPU cpu) -> cpu.SET(rf.B, 4), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xe1] = new Operation("SET 4 C", (CPU cpu) -> cpu.SET(rf.C, 4), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xe2] = new Operation("SET 4 D", (CPU cpu) -> cpu.SET(rf.D, 4), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xe3] = new Operation("SET 4 E", (CPU cpu) -> cpu.SET(rf.E, 4), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xe4] = new Operation("SET 4 H", (CPU cpu) -> cpu.SET(rf.H, 4), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xe5] = new Operation("SET 4 L", (CPU cpu) -> cpu.SET(rf.L, 4), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xe6] = new Operation("SET 4 (HL)", (CPU cpu) -> cpu.SET(rf.HL, 4), new char[] {'-', '-', '-', '-'}, 2, new int[] {16,0});
        operations[0x100+0xe7] = new Operation("SET 4 A", (CPU cpu) -> cpu.SET(rf.A, 4), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xe8] = new Operation("SET 5 B", (CPU cpu) -> cpu.SET(rf.B, 5), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xe9] = new Operation("SET 5 C", (CPU cpu) -> cpu.SET(rf.C, 5), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xea] = new Operation("SET 5 D", (CPU cpu) -> cpu.SET(rf.D, 5), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xeb] = new Operation("SET 5 E", (CPU cpu) -> cpu.SET(rf.E, 5), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xec] = new Operation("SET 5 H", (CPU cpu) -> cpu.SET(rf.H, 5), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xed] = new Operation("SET 5 L", (CPU cpu) -> cpu.SET(rf.L, 5), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xee] = new Operation("SET 5 (HL)", (CPU cpu) -> cpu.SET(rf.HL, 5), new char[] {'-', '-', '-', '-'}, 2, new int[] {16,0});
        operations[0x100+0xef] = new Operation("SET 5 A", (CPU cpu) -> cpu.SET(rf.A, 5), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xf0] = new Operation("SET 6 B", (CPU cpu) -> cpu.SET(rf.B, 6), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xf1] = new Operation("SET 6 C", (CPU cpu) -> cpu.SET(rf.C, 6), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xf2] = new Operation("SET 6 D", (CPU cpu) -> cpu.SET(rf.D, 6), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xf3] = new Operation("SET 6 E", (CPU cpu) -> cpu.SET(rf.E, 6), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xf4] = new Operation("SET 6 H", (CPU cpu) -> cpu.SET(rf.H, 6), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xf5] = new Operation("SET 6 L", (CPU cpu) -> cpu.SET(rf.L, 6), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xf6] = new Operation("SET 6 (HL)", (CPU cpu) -> cpu.SET(rf.HL, 6), new char[] {'-', '-', '-', '-'}, 2, new int[] {16,0});
        operations[0x100+0xf7] = new Operation("SET 6 A", (CPU cpu) -> cpu.SET(rf.A, 6), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xf8] = new Operation("SET 7 B", (CPU cpu) -> cpu.SET(rf.B, 7), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xf9] = new Operation("SET 7 C", (CPU cpu) -> cpu.SET(rf.C, 7), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xfa] = new Operation("SET 7 D", (CPU cpu) -> cpu.SET(rf.D, 7), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xfb] = new Operation("SET 7 E", (CPU cpu) -> cpu.SET(rf.E, 7), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xfc] = new Operation("SET 7 H", (CPU cpu) -> cpu.SET(rf.H, 7), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xfd] = new Operation("SET 7 L", (CPU cpu) -> cpu.SET(rf.L, 7), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
        operations[0x100+0xfe] = new Operation("SET 7 (HL)", (CPU cpu) -> cpu.SET(rf.HL, 7), new char[] {'-', '-', '-', '-'}, 2, new int[] {16,0});
        operations[0x100+0xff] = new Operation("SET 7 A", (CPU cpu) -> cpu.SET(rf.A, 7), new char[] {'-', '-', '-', '-'}, 2, new int[] {8,0});
    }
}