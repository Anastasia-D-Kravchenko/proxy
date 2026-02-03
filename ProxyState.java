import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProxyState {

    public static class RoutingEntry {
        public final ServerLink nextHop;
        public final int hopCount;

        public RoutingEntry(ServerLink nextHop, int hopCount) {
            this.nextHop = nextHop;
            this.hopCount = hopCount;
        }
    }

    private final ConcurrentHashMap<String, RoutingEntry> keyRoutes = new ConcurrentHashMap<>();

    private final Set<String> allKeys = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, ServerLink> neighbors = Collections.synchronizedMap(new HashMap<>());
    private final AtomicBoolean running = new AtomicBoolean(true);

    public boolean isRunning() {
        return running.get();
    }

    public void stop() {
        running.set(false);
    }

    public void addNeighbor(String addressPort, ServerLink link) {
        neighbors.put(addressPort, link);
    }

    public ServerLink getRouteForKey(String keyName) {
        RoutingEntry entry = keyRoutes.get(keyName);
        return entry != null ? entry.nextHop : null;
    }

    public Set<String> getAllKeys() {
        return allKeys;
    }

    public Map<String, ServerLink> getNeighbors() {
        return neighbors;
    }

    public int getHopCount(String keyName) {
        RoutingEntry entry = keyRoutes.get(keyName);
        return entry != null ? entry.hopCount : Integer.MAX_VALUE;
    }

    public synchronized boolean updateRoutes(Set<String> keys, ServerLink source, int hopCountFromSource) {
        boolean propagationNeeded = false;

        int actualHopCount = hopCountFromSource + 1;

        for (String key : keys) {
            if (!allKeys.contains(key)) {
                allKeys.add(key);
                propagationNeeded = true;
            }
        }

        for (String key : keys) {
            RoutingEntry existingEntry = keyRoutes.get(key);
            int finalHopCount = actualHopCount;
            if (source.isProxy() && hopCountFromSource == 0 && actualHopCount == 1) {
                finalHopCount = 2;
            }


            if (existingEntry == null || finalHopCount < existingEntry.hopCount) {
                keyRoutes.put(key, new RoutingEntry(source, finalHopCount));
                propagationNeeded = true;
            } else if (existingEntry != null && existingEntry.nextHop == source && finalHopCount > existingEntry.hopCount) {
                keyRoutes.put(key, new RoutingEntry(source, finalHopCount));
                propagationNeeded = true;
            } else if (existingEntry != null && finalHopCount <= existingEntry.hopCount) {
                if (!existingEntry.nextHop.isProxy() && source.isProxy()) {
                    continue;
                }
            }
        }

        return propagationNeeded;
    }
}