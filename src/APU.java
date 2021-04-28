public class APU {
    int[] regs;

    public APU() {
        regs = new int[0xFF40 - 0xFF10];
    }

    public int readByte(int address) {
        int aaddr = address - 0xFF10;
        return regs[aaddr];
    }

    public void writeByte(int address, int value) {
        int aaddr = address - 0xFF10;
        regs[aaddr] = value;
    }
}
