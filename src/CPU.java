public class CPU {

    interface Lambda {
        int execute(CPU cpu);
        // (CPU cpu) -> cpu.LD(A, B);
    }

    // Clock Elements
    static int m = 3;
    static int t = 12;
    Clock clk;

    // Register File
    RegisterFile rf;

    boolean interrupt;
    boolean halt;

    final static int ZFLAG_BIT = RegisterFile.ZFLAG_BIT;
    final static int NFLAG_BIT = RegisterFile.NFLAG_BIT;
    final static int HFLAG_BIT = RegisterFile.HFLAG_BIT;
    final static int CFLAG_BIT = RegisterFile.CFLAG_BIT;

    // Memory 
    MMU mmu;

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

    }

    Operation[] operations = new Operation[512];

    public CPU(){
        clk = new Clock();
        rf = new RegisterFile();
        mmu = new MMU();
    }

    /*
             PC     PC+1      PC+2
    MEMORY  |opcode|immediate|opcode
             8-bits 8-bits
    */

    public int tick() {
        int pc = rf.PC.read();
        int opcode = mmu.readByte(pc);
        pc += 1;
        pc &= 0xFFFF;
        rf.PC.write(pc);

        Operation op = operations[opcode];
        
        op.execute();

    }

    public int d8() {
        int pc = rf.PC.read();
        int data = mmu.readByte(pc);
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

        return data > 127 ? -(~data + 1) : data; 
    }

    public int a8() {
        return d8();
    }

    public int a16() {
        return d16();
    }

    public void dispatch(){
        while(true){
            int pc = rf.PC.read();
            int opcode = mmu.readByte(pc);
            // TODO: run instruction given by current pc

            rf.PC.write((pc + 1) % 65356); // update PC
            
            clk.m += this.m;
            clk.t += this.t;
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
        
        int original = r1.read();
        int result = original + data + (carry ? rf.getFlag(CFLAG_BIT) : 0);
        
        rf.setFlag(ZFLAG_BIT, result == 0);
        rf.setFlag(CFLAG_BIT, result > 255);
        rf.setFlag(NFLAG_BIT, false);
        rf.setFlag(HFLAG_BIT, (original & 0xF) + (data & 0xF) > 0xF);

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

        int original = r1.read();
        int result = (original - data) - (carry ? rf.getFlag(CFLAG_BIT) : 0);

        // set flags
        rf.setFlag(ZFLAG_BIT, result == 0);
        rf.setFlag(NFLAG_BIT, true);
        rf.setFlag(HFLAG_BIT, (original & 0x0F) < (data & 0x0F)); // TODO: Check
        rf.setFlag(CFLAG_BIT, result < 0); // TODO: Check
        
        result %= 256;
        if(write){
            r1.write(result);
        }
        
        return result;
    }

    public int SUBC(Register r1, Register r2, int immediate_value){
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

        r2.write(result);

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

        int data = immediate_value;
        int result = original + data;
        result &= 0xFFFF; // mask to 16-bit

        if(data < 0) {
            rf.setFlag(CFLAG_BIT, result < 0);
            rf.setFlag(HFLAG_BIT, (original & 0xFFF) < (data & 0xFFF));
        }
        else {
            rf.setFlag(CFLAG_BIT, result > 0xFFFF);
            rf.setFlag(HFLAG_BIT, (original & 0xFFF) + (data & 0xFFF) > 0x0FFF);   
        }
        
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
                        result = mmu.readByte(r2.read() + 0xFF00);
                    }
                    // Put A into value at address $FF00 + register C (ex. 0xE2: LD (C), A)
                    else if(operand1_mem) {
                        mmu.writeByte(r1.read() + 0xFF00, r2.read());
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
                if(operand2_8bit){
                    result = rf.SP.read() + immediate_value2;
                    /*int original = rf.SP.read();
                    int data = 0;
                    // negative
                    if(immediate_value2 > 127) {
                        data = -((~immediate_value2+1)&0xFF);
                        result = original + data;
                    }
                    else{ // positive
                        data = immediate_value;
                        result = original + data;
                    }*/
                    // TODO: immediate_value2 is a signed value -> done
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

        r.write(result);

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

        int result = ~data;

        rf.A.write(result);

        return result;
    }

    public int CCF() {
        int data = rf.getFlag(CFLAG_BIT);
        rf.setFlag(CFLAG_BIT, !rf.isFlagSet(CFLAG_BIT));
        return ~data;
    }

    public int SCF() {
        rf.setFlag(CFLAG_BIT, true);
        return 1;
    }

    // TODO: Check NOOP behavior
    public int NOP() {
        return 0;
    }

    public int HALT() {
        halt = true;
        return 0;
    }

    // TODO: STOP length is 2!
    public int STOP() {
        halt = true; // TODO: stop flag? how is it different?
        return 0;
    }

    public int DI() {
        interrupt = false;
    }

    public int EI() {
        interrupt = true;
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

    // Rotate left through carry flag
    public int RL(Register r) {
        int data = r == rf.HL ? mmu.readByte(r.read()) : r.read();
        int carry = rf.getFlag(CFLAG_BIT);
        rf.setFlag(CFLAG_BIT, (data >> 7) == 1); // old bit 7 to carry flag

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

        int result = data & (0x01 << bit);

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

    public int JP(Register r1, int immediate_value, Condition c){
        int location = 0;
        boolean jump = false;

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

        return location;
    }

    /* relative jump */

    public int JR(Condition c, int immediate_value){
        int original = rf.PC.read();
        int location = original + immediate_value;
        location &= 0xFFFF;

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
            mmu.writeWord(rf.SP.read(), rf.SP.read() + 1);

            JP(null, immediate_value, Condition.NONE);
        }
        
    }


    public int RST(int immediate_value) {
        return CALL(Condition.NONE, immediate_value);
    }

    public int RET(Condition c, boolean interrupt_enabled) {
        boolean satisfied = c == Condition.NONE ||
                            c == Condition.NZ && !rf.isFlagSet(ZFLAG_BIT) ||
                            c == Condition.Z && rf.isFlagSet(ZFLAG_BIT) ||
                            c == Condition.NC && !rf.isFlagSet(CFLAG_BIT) ||
                            c == Condition.C && rf.isFlagSet(CFLAG_BIT);

        int address = 0;
        if(satisfied) {
            int original = SP.read();
            address = mmu.readWord(original);
            int result = original + 2;
            result &= 0xFFFF;
            rf.SP.write(result);
            rf.PC.write(address);
            interrupt = interrupt_enabled;
        }

        return address;
    }

    public void define_ops() {
    }
}