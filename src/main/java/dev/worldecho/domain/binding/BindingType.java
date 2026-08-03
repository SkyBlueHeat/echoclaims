package dev.worldecho.domain.binding;

/**
 * Distinguishes entity bindings from item bindings even when they share the same
 * {@link dev.worldecho.domain.content.ContentKey}.
 */
public enum BindingType {
    ENTITY,
    ITEM
}
