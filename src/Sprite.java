public class Sprite {
    int y;
    int x;
    int tile;
    int [] palette;
    boolean xflip;
    boolean yflip;
    boolean priority;
    int index;

    public Sprite(int index){
        y = -16;
        x = -8;
        tile = 0;
        palette = new int[4];
        xflip = false;
        yflip = false;
        priority = false;
        this.index = index;
    }


}
