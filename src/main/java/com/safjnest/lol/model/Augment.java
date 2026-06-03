package com.safjnest.lol.model;

import java.util.Map;

import com.safjnest.lol.utils.AugmentUtils;

/**
 * Static metadata describing an Arena augment.
 *
 * @param id              numeric augment identifier (kept as {@code String} to
 *                        match the Riot data dump)
 * @param name            display name of the augment
 * @param tooltip         raw, untransformed tooltip string with inline tags
 *                        and placeholders
 * @param spellDataValues placeholder name → resolved value map used by
 *                        {@link #formattedTooltip()} to render the tooltip
 */
public record Augment(String id, String name, String tooltip, Map<String, String> spellDataValues) {

    /**
     * Returns the tooltip with inline tags stripped and placeholders resolved.
     *
     * @return the human-readable tooltip
     */
    public String formattedTooltip() {
        return AugmentUtils.format(tooltip, spellDataValues);
    }
}
