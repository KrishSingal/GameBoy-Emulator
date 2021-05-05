import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;

public class Input implements KeyListener {

    /*
    Standard:

    Enter (Start) - 10
    Space (Select) - 32
    X (B) - 88
    Z (A) - 90

    Directional:

    Left - 37
    Up - 38
    Right - 39
    Down - 40
     */

    enum Button {
        RIGHT(39, 0x01, 0x10), LEFT(37, 0x02, 0x10), UP(38, 0x04, 0x10), DOWN(40, 0x08, 0x10),
        A(90, 0x01, 0x20), B(88, 0x02, 0x20), SELECT(32, 0x04, 0x20), START(10, 0x08, 0x20);

        // 0x10 -> write
        //

        private int code;
        private final int bit;
        private final int line;
        
        Button(int code, int bit, int line) {
            this.code = code;
            this.bit = bit;
            this.line = line;
        }

        public int getBit() {
            return bit;
        }

        public int getLine() {
            return line;
        }
    }

    HashSet<Button> buttons;
    int mask; // 4th bit off -> directional, 5th bit off -> standard

    public Input() {
        buttons = new HashSet<>();
    }

    public void keyPressed(KeyEvent e) {
        for(Button b : Button.values()) {
            if(e.getKeyCode() == b.code) {
                buttons.add(b);
                MMU.interrupt_flags.write(MMU.interrupt_flags.read() | 0x10);
            }
        }
    }

    public void keyReleased(KeyEvent e) {
        for(Button b : Button.values()) {
            if(e.getKeyCode() == b.code) {
                buttons.remove(b);
            }
        }
        if(buttons.isEmpty()) {
            MMU.interrupt_flags.write(MMU.interrupt_flags.read() & (~0x10 & 0xFF));
        }
    }

    public void keyTyped(KeyEvent e) {
        // nothing
    }

    public void writeByte(int address, int value) {
        mask = value & 0x30;
    }

    public int readByte(int address) {
        int res = 0xCF | mask; // 1100 1111
        for(Button b : buttons) {
            // determine if button should be considered
            if((b.getLine() & res) == 0) {
                res &= 0xFF & ~b.getBit(); // only turn off specified bit to indicate it's selected
            }
        }
        return res;
    }
    
    /*
    boolean directional;
    boolean standard;

    int buttons[];

    public Input(){
        directional = false;
        standard = false;

        buttons = new int[] {0x0F, 0x0F};
    }

    public void writeByte(int address, int value){
        directional = ((value & 0x20) == 0x20);
        standard = ((value & 0x10) == 0x10);
        
    }

    public int readByte(int address){
        return  directional && standard ? 0 :
                directional ? buttons[1] :
                standard ? buttons[0] :
                            0x00;
    }

    public void keyPressed(KeyEvent e){
        System.out.println("keyPressed: " + e.getKeyCode());
        switch(e.getKeyCode()){
            case 39: buttons[1] &= 0xE; break;
            case 37: buttons[1] &= 0xD; break;
            case 38: buttons[1] &= 0xB; break;
            case 40: buttons[1] &= 0x7; break;
            case 90: buttons[0] &= 0xE; break;
            case 88: buttons[0] &= 0xD; break;
            case 32: buttons[0] &= 0xB; break;
            case 10: buttons[0] &= 0x7; break;
        }
    }

    public void keyReleased(KeyEvent e){
        System.out.println("keyReleased: " + e.getKeyCode());
        switch(e.getKeyCode()){
            case 39: buttons[1] |= 0x1; break;
            case 37: buttons[1] |= 0x2; break;
            case 38: buttons[1] |= 0x4; break;
            case 40: buttons[1] |= 0x8; break;
            case 90: buttons[0] |= 0x1; break;
            case 88: buttons[0] |= 0x2; break;
            case 32: buttons[0] |= 0x4; break;
            case 10: buttons[0] |= 0x8; break;
        }
    }

    public void keyTyped(KeyEvent e){
        // do nothing
        // keyPressed(e); maybe if there's a difference between the two
    }
    */
    
    

}
