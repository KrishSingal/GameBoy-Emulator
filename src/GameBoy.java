public class GameBoy {
    CPU cpu;
    GPU gpu;

    public GameBoy(CPU cpu, GPU gpu) {
        this.cpu = cpu;
        this.gpu = gpu;
    }

    public void step() {
        cpu.tick();
        gpu.step();
    }
}
