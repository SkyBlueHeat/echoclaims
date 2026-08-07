package io.github.skyblueheat.echoclaims.application;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded, per-staff, per-claim, expiring evidence selection session.
 *
 * <p>When a staff member runs {@code /ec review evidence <ref>}, the resulting
 * list of evidence items is stored here, bound to both the staff UUID and the
 * claim UUID. When the staff member subsequently references an item by index
 * (e.g. for an item decision), the index is resolved against the stored
 * session — never against a freshly recalculated list — so the index always
 * points to the same evidence item that was displayed.</p>
 *
 * <p>Sessions are keyed by (staffUuid, claimId) so that viewing evidence for a
 * different claim does not overwrite the session for the first claim. A
 * wrong-claim index resolution is rejected.</p>
 *
 * <p>Sessions expire after a configurable TTL. The total number of tracked
 * staff is bounded. Restart clears all sessions because the cache is
 * in-memory only. All operations are thread-safe.</p>
 */
public final class ReviewEvidenceSelectionSession {

    private final Duration ttl;
    private final int maxStaff;
    private final ConcurrentHashMap<SessionKey, Entry> sessions = new ConcurrentHashMap<>();

    public ReviewEvidenceSelectionSession(Duration ttl, int maxStaff) {
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.maxStaff = Math.max(1, maxStaff);
    }

    public void store(UUID staffUuid, UUID claimId, List<String> evidenceItemReferences) {
        Objects.requireNonNull(staffUuid, "staffUuid");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(evidenceItemReferences, "evidenceItemReferences");
        cleanupExpired();
        SessionKey key = new SessionKey(staffUuid, claimId);
        if (countDistinctStaff() >= maxStaff && sessions.keySet().stream().noneMatch(k -> k.staffUuid().equals(staffUuid))) {
            evictOldest();
        }
        sessions.put(key, new Entry(List.copyOf(evidenceItemReferences), System.currentTimeMillis()));
    }

    public Optional<String> resolve(UUID staffUuid, UUID claimId, int index) {
        Objects.requireNonNull(staffUuid, "staffUuid");
        Objects.requireNonNull(claimId, "claimId");
        if (index < 1) {
            return Optional.empty();
        }
        SessionKey key = new SessionKey(staffUuid, claimId);
        Entry entry = sessions.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (isExpired(entry)) {
            sessions.remove(key);
            return Optional.empty();
        }
        if (index > entry.references.size()) {
            return Optional.empty();
        }
        return Optional.of(entry.references.get(index - 1));
    }

    public void clear(UUID staffUuid) {
        sessions.entrySet().removeIf(e -> e.getKey().staffUuid().equals(staffUuid));
    }

    public void clearAll() {
        sessions.clear();
    }

    public int trackedStaffCount() {
        cleanupExpired();
        return countDistinctStaff();
    }

    private int countDistinctStaff() {
        return (int) sessions.keySet().stream().map(SessionKey::staffUuid).distinct().count();
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> isExpired(e.getValue(), now));
    }

    private boolean isExpired(Entry entry) {
        return isExpired(entry, System.currentTimeMillis());
    }

    private boolean isExpired(Entry entry, long now) {
        return now - entry.createdAt > ttl.toMillis();
    }

    private void evictOldest() {
        SessionKey oldestKey = null;
        long oldestTime = Long.MAX_VALUE;
        for (var e : sessions.entrySet()) {
            if (e.getValue().createdAt < oldestTime) {
                oldestTime = e.getValue().createdAt;
                oldestKey = e.getKey();
            }
        }
        if (oldestKey != null) {
            sessions.remove(oldestKey);
        }
    }

    private record SessionKey(UUID staffUuid, UUID claimId) {}

    private record Entry(List<String> references, long createdAt) {}
}
