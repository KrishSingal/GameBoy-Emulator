interface Register {
    public int read();
    public void write(int value);
}

public class RegisterFile {
    Register A;
    Register B;
    Register C;
    Register D;
    Register E;
    Register H;
    Register L;
    Register F;

    Register AF;
    Register BC;
    Register DE;
    Register HL;

    Register SP;
    Register PC;

    final static int ZFLAG_BIT = 7;
    final static int NFLAG_BIT = 6;
    final static int HFLAG_BIT = 5;
    final static int CFLAG_BIT = 4;

    public RegisterFile() {
        A = new Register8Bit();
        B = new Register8Bit();
        C = new Register8Bit();
        D = new Register8Bit();
        E = new Register8Bit();
        H = new Register8Bit();
        L = new Register8Bit();
        F = new Register8Bit();

        AF = new DoubleRegister16Bit(A, F);
        BC = new DoubleRegister16Bit(B, C);
        DE = new DoubleRegister16Bit(D, E);
        HL = new DoubleRegister16Bit(H, L);

        SP = new Register16Bit();
        PC = new Register16Bit();
    }

    public void setFlag(int bit, boolean on) {
        if(on) {
            F.write(F.read() | 1 << bit);
        }
        else {
            F.write(F.read() & ~(1 << bit));
        }
    }

    public int getFlag(int bit) {
        return (F.read() >> bit) & 1; 
    }

    public boolean isFlagSet(int bit) {
        return getFlag(bit) == 1;
    }

    public void dump() {
        System.out.println(String.format("%10s%10s%10s%10s%10s%10s%10s%10s%10s%10s%10s%10s%10s%10s%3s%3s%3s%3s", "A", "B", "C", "D", "E", "F", "H", "L", "AF", "BC", "DE", "HL", "PC", "SP", "Z", "N", "H", "C"));
        System.out.println(String.format("%10s%10s%10s%10s%10s%10s%10s%10s%10s%10s%10s%10s%10s%10s%3s%3s%3s%3s", Integer.toString(A.read(), 16), Integer.toString(B.read(), 16), Integer.toString(C.read(), 16), Integer.toString(D.read(), 16), Integer.toString(E.read(), 16), Integer.toString(F.read(), 16), Integer.toString(H.read(), 16), Integer.toString(L.read(), 16), Integer.toString(AF.read(), 16), Integer.toString(BC.read(), 16), Integer.toString(DE.read(), 16), Integer.toString(HL.read(), 16), Integer.toString(PC.read(), 16), Integer.toString(SP.read(), 16), getFlag(ZFLAG_BIT), getFlag(NFLAG_BIT), getFlag(HFLAG_BIT), getFlag(CFLAG_BIT)));
    }
}

class Register8Bit implements Register {
    int value;

    public Register8Bit() {
        this.value = 0;
    }

    public int read() {
        return value;
    }

    public void write(int value) {
        this.value = value & 0x00FF;
    }
}   

class DoubleRegister16Bit implements Register  {
    Register8Bit r1;
    Register8Bit r2;

    public DoubleRegister16Bit(Register r1, Register r2) {
        this.r1 = (Register8Bit)r1;
        this.r2 = (Register8Bit)r2;
    }

    public int read() {
        return (r1.read() << 8) | (r2.read() & 0x00FF);
    }

    public void write(int value) {
        r1.write(value >> 8);
        r2.write(value & (0x00FF));
    }
}

class Register16Bit implements Register {
    int value;

    public Register16Bit() {
        value = 0;
    }

    public void write(int value) {
        this.value = (value & 0xFFFF);
    }

    public int read() {
        return value;
    }
}