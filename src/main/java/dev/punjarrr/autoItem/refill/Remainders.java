package dev.punjarrr.autoItem.refill;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The {@code remainders} table: what a used-up item leaves in its slot (SPEC §4.3). Keys and values are
 * material keys; {@link #NONE} means the item leaves nothing.
 */
public final class Remainders {

    /** Value meaning "leaves nothing". Can't collide with a material key, which always contains ':'. */
    public static final String NONE = "none";

    private final Map<String, String> table;
    private final Map<String, Set<String>> inverse = new HashMap<>();

    public Remainders(Map<String, String> table) {
        this.table = Map.copyOf(table);
        this.table.forEach((item, remainder) -> {
            if (!remainder.equals(NONE)) {
                inverse.computeIfAbsent(remainder, k -> new HashSet<>()).add(item);
            }
        });
    }

    public Map<String, String> table() {
        return table;
    }

    /**
     * Materials that may sit in the slot after its last {@code material} was used: its own remainder
     * (stew → bowl), plus every item whose remainder it is (the last empty bucket was filled, so any
     * filled bucket may be there). Lenient on purpose: a remainder is always moved, never deleted.
     */
    public Set<String> allowedFor(String material) {
        Set<String> allowed = new HashSet<>(inverse.getOrDefault(material, Set.of()));
        String forward = table.get(material);
        if (forward != null && !forward.equals(NONE)) {
            allowed.add(forward);
        }
        return Set.copyOf(allowed);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Remainders other && table.equals(other.table);
    }

    @Override
    public int hashCode() {
        return table.hashCode();
    }

    @Override
    public String toString() {
        return "Remainders" + table;
    }
}
