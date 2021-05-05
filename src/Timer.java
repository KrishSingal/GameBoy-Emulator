public class Timer {
    // 4 8-it Registers
    Register8Bit divider; // counts up at a fixed rate of 16384 Hz; resets to 0 whenever written to
    Register8Bit counter; // counts up at specified rate; triggers INT 0x50 when going from 255 -> 0   0xFF05 - TIMA
    Register8Bit modulus; // when counter overflows, resets to start at modulus                        0xFF06 - TMA
    /*
     Bits 0-1: Speed
           00: 4096 Hz
           01: 262144 Hz
           10: 65536 Hz
           11: 16384 Hz
        Bit 2: 1 to run timer, 0 to stop */
    Register8Bit control;                                                                           // 0xFF07 - TAC

    Clock cpu_clock;
    int[] clock; // 0 - main, 1 - sub, 2 - div

    public Timer() {
        divider = new Register8Bit();
        counter = new Register8Bit();
        modulus = new Register8Bit();
        control = new Register8Bit();
        clock = new int[3];
    }

    public void tick(int m, int t) {
        clock[1] += m;
        if(clock[1] >= 4) {
            clock[0]++; // the main timing
            clock[1] -= 4; // sub stores the increment

            clock[2]++; // div increases at 1/16th of the rate

            // increment the div register whenever 16 divs have passed
            if(clock[2] == 16) {
                divider.write((divider.read() + 1) & 0xFF);
                clock[2] = 0;
            }
        }
    }

    // Determine if we need to update timer regs
    public void update() {
        int limit = 64;
        // if the timer is on
        if((control.read() & 0x4) == 0x4) {
            // switch on the speed
            switch(control.read() & 0x3) {
                // 00: 4096 Hz
                case 0x00:
                    limit = 64;
                    break;
                // 01: 262144 Hz
                case 0x01:
                    limit = 1;
                    break;
                // 10: 65536 Hz
                case 0x10:
                    limit = 4;
                    break;
                // 11: 16384 Hz
                case 0x11:
                    limit = 16;
                    break;
            }
        }

        if(clock[0] >= limit) {
            step();
        }
    }

    public void step() {
        clock[0] = 0;
        counter.write(counter.read() + 1);

        // When overflowing
        if(counter.read() > 0xFF) {
            counter.write(modulus.read()); // set value to be modulus reg
            MMU.interrupt_flags.write(MMU.interrupt_flags.read() | 0x04); // timer interrupts
        }
    }

    public int readByte(int address) {
        if(address == 0xFF04) {
            return divider.read();
        }
        if(address == 0xFF05) {
            return counter.read();
        }
        if(address == 0xFF06) {
            return modulus.read();
        }
        if(address == 0xFF07) {
            return control.read();
        }
        return -1;
    }

    public void writeByte(int address, int value) {
        if(address == 0xFF04) {
            divider.write(value);
        }
        if(address == 0xFF05) {
            counter.write(value);
        }
        if(address == 0xFF06) {
            modulus.write(value);
        }
        if(address == 0xFF07) {
            control.write(value);
        }
    }
}
