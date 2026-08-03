package dev.worldecho.application.memory;

import dev.worldecho.domain.content.IdentifiedContent;
import dev.worldecho.domain.item.ItemDescriptor;
import dev.worldecho.domain.item.ItemScore;
import dev.worldecho.domain.item.TrackedItemId;

import java.util.Objects;
import java.util.Optional;

/**
 * The single dropped item WorldEcho considers worth remembering for a death.
 */
public record LootCandidate(
        IdentifiedContent content,
        ItemDescriptor descriptor,
        ItemScore score,
        TrackedItemId trackedItemId
) {

    public LootCandidate(
            IdentifiedContent content,
            ItemDescriptor descriptor,
            ItemScore score
    ) {
        this(content, descriptor, score, null);
    }

    public LootCandidate {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(score, "score");
    }

    public Optional<TrackedItemId> optionalTrackedItemId() {
        return Optional.ofNullable(trackedItemId);
    }
}
