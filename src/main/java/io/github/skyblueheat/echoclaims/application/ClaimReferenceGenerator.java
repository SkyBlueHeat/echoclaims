package io.github.skyblueheat.echoclaims.application;

import java.security.SecureRandom;
import java.util.Objects;

/**
 * Generates short, human-readable public references for claims.
 *
 * <p>References use an alphanumeric alphabet (excluding ambiguous characters like
 * 0/O and 1/I/l) and are 8 characters long, giving roughly 2.8 trillion possible
 * values. A {@link SecureRandom} is used to make guessing infeasible.</p>
 */
public final class ClaimReferenceGenerator {

    private static final char[] ALPHABET =
            "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private static final int REFERENCE_LENGTH = 8;
    private static final int MAX_ATTEMPTS = 100;

    private final SecureRandom random;
    private final UniquenessChecker checker;

    /**
     * Functional interface for checking whether a reference is already in use.
     */
    @FunctionalInterface
    public interface UniquenessChecker {
        boolean isUnique(String reference);
    }

    public ClaimReferenceGenerator() {
        this(new SecureRandom(), ref -> true);
    }

    public ClaimReferenceGenerator(UniquenessChecker checker) {
        this(new SecureRandom(), checker);
    }

    public ClaimReferenceGenerator(SecureRandom random, UniquenessChecker checker) {
        this.random = Objects.requireNonNull(random, "random");
        this.checker = Objects.requireNonNull(checker, "checker");
    }

    /**
     * Generates a unique public reference.
     *
     * @return a unique 8-character alphanumeric reference
     * @throws IllegalStateException if a unique reference cannot be generated in {@value #MAX_ATTEMPTS} attempts
     */
    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = generateCandidate();
            if (checker.isUnique(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Failed to generate a unique claim reference after " + MAX_ATTEMPTS + " attempts");
    }

    private String generateCandidate() {
        char[] buffer = new char[REFERENCE_LENGTH];
        for (int i = 0; i < REFERENCE_LENGTH; i++) {
            buffer[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(buffer);
    }
}
