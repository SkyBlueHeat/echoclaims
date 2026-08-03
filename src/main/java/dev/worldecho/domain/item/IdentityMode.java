package dev.worldecho.domain.item;

/**
 * Classification of how an item should be identified by WorldEcho.
 *
 * <ul>
 *   <li>{@link #UNIQUE} — one stable UUID per individually meaningful item (weapons, tools, armor, named items, etc.)</li>
 *   <li>{@link #LOT} — a fungible stack identity for ordinary stackable resources (cobblestone, dirt, iron ingots, etc.)</li>
 * </ul>
 */
public enum IdentityMode {
    UNIQUE,
    LOT
}
