public class Timer2 {

    int divCycles;
    int divCounter;

    int timerCounter;
    int timerCycles;
    int modulus;

    int control;
    int freq;

    public void tick(int m) {
        updateDiv(m);
        if((control & 0x04) == 0x04) {
            updateTimer(m);
        }
    }

    public void updateDiv(int m) {
        divCycles -= m;

        while(divCycles <= 0) {
            divCycles += 0xFF;
            divCounter = (divCounter + 1) & 0xFF;
        }
    }

    public void updateTimer(int m) {
        timerCycles -= m;

        while(timerCycles <= 0) {
            timerCycles += CPU.FREQUENCY / freq;

            if(timerCounter == 0xFF) {
                timerCounter = modulus;
                MMU.interrupt_flags.write(MMU.interrupt_flags.read() | 0x04); // timer interrupts
            }
            else {
                timerCounter++;
            }
        }
    }

    public void writeByte(int address, int value) {
        switch(address) {
            case 0xff04:
                divCounter = value;
                break;
            case 0xff05:
                timerCounter = value;
                break;
            case 0xff06:
                modulus = value;
                break;
            case 0xff07:
                control = value;
                switch(value & 0x03) {
                    case 0x00: freq = 4096; break;
                    case 0x01: freq = 262144; break;
                    case 0x02: freq = 65536; break;
                    case 0x03: freq = 16284; break;
                }
                break;
        }
    }

    public int readByte(int address) {
        switch (address) {
            case 0xff04:
                return divCounter;
            case 0xff05:
                return timerCounter;
            case 0xff06:
                return modulus;
            case 0xff07:
                return control;
        }
        return 0;
    }


}
