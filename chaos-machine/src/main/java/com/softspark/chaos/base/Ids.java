package com.softspark.chaos.base;

import com.github.f4b6a3.ulid.UlidCreator;
import java.util.UUID;

/**
 * Utility class for generating identifiers (ULID/UUID).
 * <p>
 * ULIDs are lexicographically sortable, URL-safe, and provide millisecond timestamp
 * precision. They are used as primary keys for all entities in the chaos machine.
 */
public final class Ids {

  private Ids() {}

  /**
   * Generates a new ULID as a string.
   *
   * @return a new ULID string identifier
   */
  public static String generate() {
    return UUID.randomUUID().toString();
  }

  public static String generateUUID() {
    return UUID.randomUUID().toString();
  }

  public static String generateULID() {
    return UlidCreator.getMonotonicUlid().toString();
  }
}
