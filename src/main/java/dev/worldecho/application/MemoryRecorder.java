package dev.worldecho.application;

import dev.worldecho.domain.memory.StoryMemoryEvent;
import dev.worldecho.persistence.StoryWriteQueue;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Hands immutable memory events to the asynchronous write path.
 *
 * <p>A dropped event is reported instead of being silently discarded so administrators
 * can see that the write queue is saturated.</p>
 */
public final class MemoryRecorder {

    private final StoryWriteQueue writeQueue;
    private final Consumer<StoryMemoryEvent> dropHandler;

    public MemoryRecorder(StoryWriteQueue writeQueue, Consumer<StoryMemoryEvent> dropHandler) {
        this.writeQueue = Objects.requireNonNull(writeQueue, "writeQueue");
        this.dropHandler = Objects.requireNonNull(dropHandler, "dropHandler");
    }

    public boolean record(StoryMemoryEvent event) {
        boolean accepted = writeQueue.submit(event);
        if (!accepted) {
            dropHandler.accept(event);
        }
        return accepted;
    }
}
