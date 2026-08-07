package io.github.skyblueheat.echoclaims.config;

import io.github.skyblueheat.echoclaims.application.ReviewServiceConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the SettingsLoader correctly loads review-related configuration
 * from config.yml, applies defaults, clamps out-of-range values, and that
 * EchoClaimsSettings correctly converts to ReviewServiceConfig.
 */
class ReviewSettingsLoaderTest {

    @Test
    void emptyConfigurationProducesReviewDefaults() {
        SettingsLoadResult result = SettingsLoader.load(MapConfigurationSource.empty());
        EchoClaimsSettings settings = result.settings();

        assertTrue(settings.reviewsEnabled());
        assertEquals(Duration.ofSeconds(300), settings.reviewEvidenceSessionTtl());
        assertEquals(50, settings.reviewEvidenceSessionMaxStaff());
        assertEquals(20, settings.reviewQueuePageSize());
        assertEquals(1_000, settings.reviewMaxInternalNoteLength());
        assertEquals(1_000, settings.reviewMaxQuestionLength());
        assertEquals(1_000, settings.reviewMaxPlayerResponseLength());
        assertEquals(2_000, settings.reviewMaxFinalSummaryLength());
        assertEquals(500, settings.reviewMaxItemNoteLength());
        assertEquals(100, settings.reviewMaxItemDecisionsPerClaim());
        assertEquals(5, settings.reviewMaxActiveReviewsPerStaff());
        assertEquals(Duration.ZERO, settings.reviewPlayerResponseCooldown());
        assertTrue(settings.reviewRequireAssignmentForMutations());
        assertFalse(settings.reviewAllowPlayerCancelAfterReviewStart());
    }

    @Test
    void reviewSettingsAreReadFromConfiguration() {
        Map<String, Object> reviews = new HashMap<>();
        reviews.put("enabled", false);
        reviews.put("evidence-session-ttl-seconds", 600);
        reviews.put("evidence-session-max-staff", 100);
        reviews.put("queue-page-size", 50);
        reviews.put("max-internal-note-length", 2_000);
        reviews.put("max-question-length", 800);
        reviews.put("max-player-response-length", 1_500);
        reviews.put("max-final-summary-length", 3_000);
        reviews.put("max-item-note-length", 750);
        reviews.put("max-item-decisions-per-claim", 200);
        reviews.put("max-active-reviews-per-staff", 10);
        reviews.put("player-response-cooldown-seconds", 30);
        reviews.put("require-assignment-for-mutations", false);
        reviews.put("allow-player-cancel-after-review-start", true);

        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "reviews", reviews
        )));

        EchoClaimsSettings settings = result.settings();
        assertFalse(settings.reviewsEnabled());
        assertEquals(Duration.ofSeconds(600), settings.reviewEvidenceSessionTtl());
        assertEquals(100, settings.reviewEvidenceSessionMaxStaff());
        assertEquals(50, settings.reviewQueuePageSize());
        assertEquals(2_000, settings.reviewMaxInternalNoteLength());
        assertEquals(800, settings.reviewMaxQuestionLength());
        assertEquals(1_500, settings.reviewMaxPlayerResponseLength());
        assertEquals(3_000, settings.reviewMaxFinalSummaryLength());
        assertEquals(750, settings.reviewMaxItemNoteLength());
        assertEquals(200, settings.reviewMaxItemDecisionsPerClaim());
        assertEquals(10, settings.reviewMaxActiveReviewsPerStaff());
        assertEquals(Duration.ofSeconds(30), settings.reviewPlayerResponseCooldown());
        assertFalse(settings.reviewRequireAssignmentForMutations());
        assertTrue(settings.reviewAllowPlayerCancelAfterReviewStart());
    }

    @Test
    void outOfRangeReviewSettingsAreClampedAndWarn() {
        Map<String, Object> reviews = new HashMap<>();
        reviews.put("evidence-session-ttl-seconds", 999_999);
        reviews.put("evidence-session-max-staff", 999_999);
        reviews.put("queue-page-size", 999_999);
        reviews.put("max-internal-note-length", 999_999);
        reviews.put("max-item-decisions-per-claim", 999_999);
        reviews.put("max-active-reviews-per-staff", 999_999);

        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "reviews", reviews
        )));

        EchoClaimsSettings settings = result.settings();
        assertEquals(Duration.ofSeconds(3_600), settings.reviewEvidenceSessionTtl());
        assertEquals(200, settings.reviewEvidenceSessionMaxStaff());
        assertEquals(100, settings.reviewQueuePageSize());
        assertEquals(10_000, settings.reviewMaxInternalNoteLength());
        assertEquals(1_000, settings.reviewMaxItemDecisionsPerClaim());
        assertEquals(100, settings.reviewMaxActiveReviewsPerStaff());
        assertTrue(result.hasWarnings());
    }

    @Test
    void reviewServiceConfigConversionIsCorrect() {
        Map<String, Object> reviews = new HashMap<>();
        reviews.put("max-internal-note-length", 500);
        reviews.put("max-question-length", 600);
        reviews.put("max-player-response-length", 700);
        reviews.put("max-final-summary-length", 800);
        reviews.put("max-item-note-length", 300);
        reviews.put("max-item-decisions-per-claim", 50);
        reviews.put("require-assignment-for-mutations", true);

        SettingsLoadResult result = SettingsLoader.load(new MapConfigurationSource(Map.of(
                "reviews", reviews
        )));
        EchoClaimsSettings settings = result.settings();
        ReviewServiceConfig config = settings.reviewServiceConfig();

        assertTrue(config.requireAssignmentForMutations());
        assertEquals(500, config.maxInternalNoteLength());
        assertEquals(600, config.maxQuestionLength());
        assertEquals(700, config.maxPlayerResponseLength());
        assertEquals(800, config.maxFinalSummaryLength());
        assertEquals(300, config.maxItemNoteLength());
        assertEquals(50, config.maxItemDecisionsPerClaim());
    }

    @Test
    void reviewServiceConfigDefaultsMatchSettingsDefaults() {
        EchoClaimsSettings settings = EchoClaimsSettings.defaults();
        ReviewServiceConfig config = settings.reviewServiceConfig();
        ReviewServiceConfig expected = ReviewServiceConfig.defaults();

        assertEquals(expected.requireAssignmentForMutations(), config.requireAssignmentForMutations());
        assertEquals(expected.maxInternalNoteLength(), config.maxInternalNoteLength());
        assertEquals(expected.maxQuestionLength(), config.maxQuestionLength());
        assertEquals(expected.maxPlayerResponseLength(), config.maxPlayerResponseLength());
        assertEquals(expected.maxFinalSummaryLength(), config.maxFinalSummaryLength());
        assertEquals(expected.maxItemNoteLength(), config.maxItemNoteLength());
        assertEquals(expected.maxItemDecisionsPerClaim(), config.maxItemDecisionsPerClaim());
    }
}
