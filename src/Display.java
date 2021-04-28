import java.awt.*;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.*;

public class Display extends Canvas implements Runnable {
    int width = 160;
    int height = 144;
    int scale = 5;
    int count = 0;

    private Thread thread;
    boolean running = false;
    public JFrame frame;

    //public Screen screen;

    private BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    private int[] pixels = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();

    int buffer[] = new int[pixels.length];

    public Display(){
        Dimension size = new Dimension(scale * width, scale * height);
        setPreferredSize(size);

        // screen = new Screen(width, height);
        frame = new JFrame();

        /*for(int i=0; i< width; i++){
            for(int j = 0; j< height; j++){
                image.setRGB(i,j,0xFFFFFF);
            }
        }*/
    }

    public synchronized void start(){
        running = true;
        thread = new Thread(this, "Display");
        thread.start();
    }

    public void run(){
        while(running){
            // System.out.println("Running...");
            /* try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }*/

            update();
            try {
                render();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void update(){

    }

    public void scanline(int offset, int color){
        buffer[offset] = color;
    }

    public void putImageData(){
        for(int i = 0; i < pixels.length; i++){
            pixels[i] = buffer[i];
        }
    }

    public void render() throws IOException {
        BufferStrategy bs = getBufferStrategy();

        if(bs == null){
            createBufferStrategy(3);
            return;
        }

//        screen.clear();

//        screen.render();

        /* for(int i=0; i<pixels.length; i++){
            pixels[i] = screen.pixels[i];
            //image.setRGB()
        } */ 

        Graphics g = bs.getDrawGraphics();
        // do all graphics here
        // g.setColor(Color.BLACK);
        // g.fillRect(0,0,scale * width, scale * height);
        g.drawImage(image,0,0, scale * width, scale * height, null);
        //image.
        g.dispose();
        bs.show();
    }


    public synchronized void stop() throws InterruptedException {
        thread.join();
        running = false;
    }

    public static void main(String args[]){
        /*
        Display game = new Display();
        game.frame.setResizable(false); // should be first thing
        game.frame.setTitle("GameBoy");
        game.frame.add(game); // adds canvas display-able object
        game.frame.pack(); // makes size same as frame?
        game.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // ends process when window is closed
        game.frame.setLocationRelativeTo(null);
        game.frame.setVisible(true);

        game.start();
        */
    }
}
