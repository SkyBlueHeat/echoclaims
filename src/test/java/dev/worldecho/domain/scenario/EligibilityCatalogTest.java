package dev.worldecho.domain.scenario;

import dev.worldecho.domain.binding.BindingType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EligibilityCatalogTest {

    @Test
    void allRequiredBuiltinProfilesExist() {
        EligibilityCatalog catalog = EligibilityCatalog.builtin();

        assertTrue(catalog.find("item-carrier").isPresent());
        assertTrue(catalog.find("promotion-candidate").isPresent());
        assertTrue(catalog.find("transferable-story-item").isPresent());
        assertTrue(catalog.find("combat-story-actor").isPresent());
    }

    @Test
    void idsAreUnique() {
        assertThrows(IllegalArgumentException.class, () ->
                EligibilityCatalog.of(
                        EligibilityProfile.entity("test").build(),
                        EligibilityProfile.entity("test").build()
                ));
    }

    @Test
    void entityAndItemProfilesAreSeparated() {
        EligibilityCatalog catalog = EligibilityCatalog.builtin();

        List<EligibilityProfile> entityProfiles = catalog.profilesFor(BindingType.ENTITY);
        List<EligibilityProfile> itemProfiles = catalog.profilesFor(BindingType.ITEM);

        assertTrue(entityProfiles.stream().allMatch(p -> p.bindingType() == BindingType.ENTITY));
        assertTrue(itemProfiles.stream().allMatch(p -> p.bindingType() == BindingType.ITEM));
        assertEquals(3, entityProfiles.size());
        assertEquals(1, itemProfiles.size());
    }

    @Test
    void unknownLookupReturnsEmpty() {
        EligibilityCatalog catalog = EligibilityCatalog.builtin();
        assertTrue(catalog.find("nonexistent").isEmpty());
        assertTrue(catalog.find(null).isEmpty());
    }

    @Test
    void catalogCollectionsAreImmutable() {
        EligibilityCatalog catalog = EligibilityCatalog.builtin();

        assertThrows(UnsupportedOperationException.class, () -> catalog.allProfiles().add(null));
        assertThrows(UnsupportedOperationException.class, () -> catalog.allIds().add("x"));
    }

    @Test
    void deterministicProfileOrdering() {
        EligibilityCatalog c1 = EligibilityCatalog.builtin();
        EligibilityCatalog c2 = EligibilityCatalog.builtin();

        assertEquals(c1.allProfiles(), c2.allProfiles());
    }

    @Test
    void duplicateProfileRegistrationIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                EligibilityCatalog.of(
                        EligibilityProfile.entity("dup").build(),
                        EligibilityProfile.item("dup").build()
                ));
    }

    @Test
    void profileIdIsCaseInsensitive() {
        EligibilityCatalog catalog = EligibilityCatalog.builtin();

        assertTrue(catalog.find("ITEM-CARRIER").isPresent());
        assertTrue(catalog.find("Item-Carrier").isPresent());
        assertTrue(catalog.find("item-carrier").isPresent());
    }

    @Test
    void sizeAndCountsAreCorrect() {
        EligibilityCatalog catalog = EligibilityCatalog.builtin();

        assertEquals(4, catalog.size());
        assertEquals(3, catalog.entityProfileCount());
        assertEquals(1, catalog.itemProfileCount());
    }
}
