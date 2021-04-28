class Clock{
    int m;
    int t;

    public Clock(){
        m = 0;
        t = 0;
    }

    public void tick(){
        this.m += CPU.m;
        this.t += CPU.t;
    }

    public void reset(){
        this.m = 0;
        this.t = 0;
    }
}