import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        GameBoy gb = new GameBoy();

        gb.loadROM("roms/Super Mario Land (World).gb");

        while(true) {
            gb.step();
        }
    }
}
