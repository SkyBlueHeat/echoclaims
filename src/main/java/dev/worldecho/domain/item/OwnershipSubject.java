package dev.worldecho.domain.item;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable, provider-neutral representation of an entity that holds or owns a tracked item.
 *
 * <p>Stable identifier is a UUID for PLAYER and ENTITY types, a location string for
 * CONTAINER and WORLD_DROP types, or a stable token for SYSTEM.  Display name is
 * optional and does not affect equality.
 */
public record OwnershipSubject(
        OwnershipSubjectType type,
        String stableId,
        String displayName
) {

    public OwnershipSubject {
        Objects.requireNonNull(type, "type");
        stableId = stableId == null ? "" : stableId.trim().toLowerCase(Locale.ROOT);
        displayName = displayName == null ? "" : displayName.strip();
    }

    public static OwnershipSubject player(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return new OwnershipSubject(OwnershipSubjectType.PLAYER, playerUuid.toString(), "");
    }

    public static OwnershipSubject player(UUID playerUuid, String displayName) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return new OwnershipSubject(OwnershipSubjectType.PLAYER, playerUuid.toString(), displayName);
    }

    public static OwnershipSubject entity(UUID entityUuid) {
        Objects.requireNonNull(entityUuid, "entityUuid");
        return new OwnershipSubject(OwnershipSubjectType.ENTITY, entityUuid.toString(), "");
    }

    public static OwnershipSubject entity(UUID entityUuid, String displayName) {
        Objects.requireNonNull(entityUuid, "entityUuid");
        return new OwnershipSubject(OwnershipSubjectType.ENTITY, entityUuid.toString(), displayName);
    }

    public static OwnershipSubject container(String worldId, int x, int y, int z) {
        String id = worldId.trim().toLowerCase(Locale.ROOT) + ":" + x + ":" + y + ":" + z;
        return new OwnershipSubject(OwnershipSubjectType.CONTAINER, id, "");
    }

    public static OwnershipSubject worldDrop(String worldId, int x, int y, int z) {
        String id = worldId.trim().toLowerCase(Locale.ROOT) + ":" + x + ":" + y + ":" + z;
        return new OwnershipSubject(OwnershipSubjectType.WORLD_DROP, id, "");
    }

    public static OwnershipSubject system(String token) {
        Objects.requireNonNull(token, "token");
        if (token.isBlank()) {
            throw new IllegalArgumentException("system token cannot be blank");
        }
        return new OwnershipSubject(OwnershipSubjectType.SYSTEM, token.trim().toLowerCase(Locale.ROOT), "");
    }

    public static OwnershipSubject unknown() {
        return new OwnershipSubject(OwnershipSubjectType.UNKNOWN, "", "");
    }

    public Optional<String> optionalDisplayName() {
        return displayName.isEmpty() ? Optional.empty() : Optional.of(displayName);
    }

    public boolean isValid() {
        return switch (type) {
            case PLAYER, ENTITY -> {
                try {
                    UUID.fromString(stableId);
                    yield true;
                } catch (IllegalArgumentException exception) {
                    yield false;
                }
            }
            case CONTAINER, WORLD_DROP -> !stableId.isEmpty();
            case SYSTEM -> !stableId.isEmpty();
            case UNKNOWN -> true;
        };
    }

    public String describe() {
        return type.token() + ":" + stableId;
    }
}
