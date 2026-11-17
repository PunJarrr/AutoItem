package dev.punjarrr.autoItem.refill;

/** A group of actions that can use up the last item in a slot. Each has a switch under {@code triggers}. */
public enum Trigger {
    BLOCK_PLACE("block-place"),
    CONSUME("consume"),
    BUCKETS("buckets"),
    THROWABLES("throwables"),
    RIGHT_CLICK("right-click"),
    BOWS("bows"),
    TOOL_BREAK("tool-break");

    private final String configKey;

    Trigger(String configKey) {
        this.configKey = configKey;
    }

    /** The key under {@code triggers} in config.yml. */
    public String configKey() {
        return configKey;
    }
}
