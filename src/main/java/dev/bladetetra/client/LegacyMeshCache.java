package dev.bladetetra.client;

import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** LRU with independent key and geometry budgets. Also caches unsuccessful resolutions. */
final class LegacyMeshCache<K, V> {
    private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>(16, .75F, true);
    private final int maxEntries;
    private final int maxWeight;
    private final ToIntFunction<V> weigh;
    private int weight;

    LegacyMeshCache(int maxEntries, int maxWeight, ToIntFunction<V> weigh) {
        this.maxEntries = maxEntries;
        this.maxWeight = maxWeight;
        this.weigh = weigh;
    }

    V get(K key, Function<K, V> resolve) {
        var entry = entries.get(key);
        if (entry != null) return entry.value();
        V value = resolve.apply(key);
        int cost = value == null ? 0 : weigh.applyAsInt(value);
        if (cost > maxWeight) return value;
        entries.put(key, new Entry<>(value, cost));
        weight += cost;
        while (weight > maxWeight || entries.size() > maxEntries) {
            var oldest = entries.entrySet().iterator();
            weight -= oldest.next().getValue().weight();
            oldest.remove();
        }
        return value;
    }

    void clear() {
        entries.clear();
        weight = 0;
    }

    private record Entry<V>(V value, int weight) {}
}
