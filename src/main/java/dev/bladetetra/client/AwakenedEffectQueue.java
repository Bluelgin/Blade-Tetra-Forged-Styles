package dev.bladetetra.client;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** One render queue with separate bounded admission for server-owned static anchors. */
final class AwakenedEffectQueue<T> extends AbstractCollection<T> {
    // Matches the hard server anchor maximum, independent of transient quality settings.
    static final int MAX_ANCHORS=512;
    private record Entry<T>(T value,Integer anchor) {}
    private final List<Entry<T>> entries=new ArrayList<>();

    void offer(T value,Integer anchor,int transientLimit) {
        if(anchor!=null) {
            entries.removeIf(e->anchor.equals(e.anchor));
            if(entries.stream().filter(e->e.anchor!=null).count()>=MAX_ANCHORS) return;
        } else {
            int count=(int)entries.stream().filter(e->e.anchor==null).count();
            Iterator<Entry<T>> it=entries.iterator();
            while(count>=Math.max(1,transientLimit) && it.hasNext()) {
                if(it.next().anchor==null) { it.remove();count--; }
            }
        }
        entries.add(new Entry<>(value,anchor));
    }
    @Override public int size() { return entries.size(); }
    @Override public void clear() { entries.clear(); }
    @Override public Iterator<T> iterator() {
        Iterator<Entry<T>> it=entries.iterator();
        return new Iterator<>() {
            public boolean hasNext() { return it.hasNext(); }
            public T next() { return it.next().value; }
            public void remove() { it.remove(); }
        };
    }
}
