package dev.worldecho.integration;

import org.bukkit.entity.Entity;

/**
 * Identifies a live entity, for example a MythicMob or a vanilla zombie.
 *
 * <p>Implementations are called on the server thread only.</p>
 */
public interface EntityContentProvider extends ContentProvider<Entity> {
}
