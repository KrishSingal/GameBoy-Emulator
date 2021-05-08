# GameBoyz Game Boy Emulator

Final Project for 429H: A functional Game Boy Emulator written in Java.

Developed by Krish Singal and Shakeel Samsudeen.

## Implementation Details

### The CPU

The CPU was the first thing that we tackled; we thought long and hard about the 
best way to properly implement a CPU, and settled on having every instruction 
live in a method inside of the CPU and have a functional interface to run lambda
functions for those instructions. We can then implement methods an invoke them 
by storing those lambda functions inside of our own optable, indexed by the 
opcode. We thought this was an elegant solution that took advantage of 
automation features, and was nice to debug as well.

Notable Files:
* `src/CPU.java` - The actual CPU for our emulator, housing all of the 
instruction implementations, optable, interrupt handling, and timing 
responsiblities.
* `src/generate_instructions.py` - A python script used to generate our optable,
which was then pasted into our `CPU.java` file.
* `src/RegisterFile.java` - Manages any and all registers of the CPU. We created
a `Register` interface that made it easy to pass in any type of register to a
function. While this added some case work, it helped ease the process and manage
the registers altogether because we had created `Register8Bit` and 
`DoubleRegister8Bit` types to model the combined registers effectively.
* `src/Clock.java` - Manages the clock of the Game Boy by looking at the CPU 
cycles.
* `src/Timer2.java` - While not necessarily part of the CPU, the timer managed
the Game Boy's internal timer, which was continuously updating as a result of
the passed CPU machine cycles.

### The MMU
The MMU was implemented alongside the CPU, providing a clean interface into the
memory unit and its various mapped components. Our implementation was heavily
determined by the Game Boy architecture, being sure to map each range in memory
to the correct components. We decided to store each of the memory components in
separate integer arrays for clarity. The MMU also handles loading the initial
Boot ROM and cartridge ROM into memory. Advanced features such as the Memory
Bank Controller (MBC) are also implemented here to support more complicated 
gameplay.

Notable Files:
* `src/MMU.java` - The actual MMU for our emulator, handling reads and 
writes/ROM loads to memory. 
* `src/Input.java` - The `Input` module updates the input register in memory
whenever a button is pressed, which is thus used by the `MMU` to read/write 
data.

### The PPU
The PPU was the next implemented component. We began by looking into different
graphics libraries in Java that would support the best gameplay. We initially
started with LWJGL, but eventually switched to simple awt after encountering
some compatibility issues with LWJGL. Using awt, we implemented a three buffer
system that would enable smooth animations devoid of flickering. The main logic
of the PPU proved to be quite difficult to perfect. We began by implementing the
step() function to keep consistent with both the GPU and CPU timing schemes.
From here, the graphics control registers and actual rendering logic were added
in. Deciding to store our tiles and sprites within internal structures helped us
encapsulate all the relevant information and avoid unreadable memory indexing. 

Notable Files:
* `src/GPU.java` - The actual PPU of our emulator, computing game updates and 
communicating with the output frame via `src/Display.java`.
* `src/Sprite.java` - Our internal Sprite structure to enacpsulate sprite 
parameters 
* `src/Clock.java` - The PPU has an instance of the clock it checks to switch
between modes and determine when to render to the screen, based off of the CPU's
clock cycles.
* `src/Display.java` - Driver class for screen output. Uses `java.awt` library
to interface with the machine's graphics unit.


## Testing
Proper testing was imperative to ensure the proper functioning of our Game Boy 
emulator. We created numerous testing files that served as our "step debugger,"
regularly dumping the registers and ensuring that our emulator was accurately 
modeling the expected output from a real Game Boy. We did this by using the BGB
debugger, a full Game Boy emulator fitted with its own debugger.

Notable Files:
* `test/BootROMTest.java` - The litmus test for our emulator. We stepped through
all `0x100` bytes of the boot rom to ensure that we had correctly implemented 
CPU instructions and got the Nintendo logo to scroll. It was an especially proud
moment for us at that point.
* `test/TestROMTesting.java` - Used to test different Test ROMs for CPU 
instructions. Running the Test ROMs enabled us to diagnose our GB for any bugs 
in the instructions that we implemented for the CPU.
* `test/InputTest.java` - Features a test we made for input using a 
community-made ROM to play tic-tac-toe.
* `test/TetrisTest.java` - The primary debugger we used to test a variety of 
different ROMs (initially tetris but we got lazy to change the name).
 
## Further Information

We've also provided a `log.txt` detailing our git commit history over the past
couple of weeks, showing our progress and struggles over our time working on the
emulator.
Please also refer to `USAGE.md` for details on running and playing some of the 
games.
As for our future work, we definitely plan on expanding and improving our 
emulator. Our first priority is getting the sound to work, followed by applying
RL to have an AI play a variety of the games for us. We're looking forward to
making improvements over the summer.


## Resources Consulted
* [Pan Docs](https://gbdev.io/pandocs/) - Excellent documentation for GB 
emulator developers detailing every aspect of the GB
* [Detailed Instruction Set](https://rgbds.gbdev.io/docs/gbz80.7) - 
Specifications on all GB instructions
* [Optable](https://gbdev.io/gb-opcodes/optables/) - Opcode table for all GB 
instructions
* [Test ROMs](https://github.com/retrio/gb-test-roms) - Test ROMs to test our 
implementation
* [Boot ROM Disassembly](https://gbdev.gg8.se/wiki/articles/Gameboy_Bootstrap_ROM) - 
Useful for debugging the boot ROM
* [BGB Debugger](https://bgb.bircd.org/) - The holy grail of GB emulation; a GB
emulator fitted with a full debugger