package dev.bladetetra.client.reflection;

/** Structural budgets, not measured FPS promises. Zero is an explicitly approximate compatibility path. */
public record ReflectionQuality(int level,int width,int updatesPerSecond,int range) {
    public static ReflectionQuality of(int level) {
        return switch(level) {
            case 1->new ReflectionQuality(1,128,8,24);
            case 2->new ReflectionQuality(2,256,15,40);
            case 3->new ReflectionQuality(3,512,30,64);
            default->new ReflectionQuality(0,0,0,0);
        };
    }
    public long intervalNanos() { return updatesPerSecond==0?Long.MAX_VALUE:1_000_000_000L/updatesPerSecond; }
    public boolean due(long now,long previous) { return level>0&&(previous==0||now-previous>=intervalNanos()); }
}
