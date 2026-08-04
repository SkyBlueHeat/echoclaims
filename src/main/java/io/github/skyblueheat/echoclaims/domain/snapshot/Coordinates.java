package io.github.skyblueheat.echoclaims.domain.snapshot;

/**
 * Immutable block-level coordinates captured from a Bukkit location.
 *
 * <p>Instances are created on the server thread and safe to hand to asynchronous work.</p>
 */
public record Coordinates(int x, int y, int z) {
}
