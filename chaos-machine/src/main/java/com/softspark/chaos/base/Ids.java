package com.softspark.chaos.base;

import com.github.f4b6a3.ulid.UlidCreator;

/**
 * Utility class for generating ULID-based identifiers.
 * <p>
 * ULIDs are lexicographically sortable, URL-safe, and provide millisecond timestamp
 * precision. They are used as primary keys for all entities in the chaos machine.
 */
public final class Ids {

    private Ids() {
    }

    /**
     * Generates a new ULID as a string.
     *
     * @return a new ULID string identifier
     */
    public static String generate() {
        return UlidCreator.getMonotonicUlid().toString();
    }
}
