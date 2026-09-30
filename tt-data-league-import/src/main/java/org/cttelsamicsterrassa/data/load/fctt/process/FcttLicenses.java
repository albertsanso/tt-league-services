package org.cttelsamicsterrassa.data.load.fctt.process;

/**
 * Tells FCTT season-registration licences apart from placeholders.
 *
 * <p>Some FCTT actas write {@code "0"} as the licence of every player. That value does not
 * identify anyone: treating it as a licence would resolve every such player to one shared
 * {@code PlayerSeason}. A blank or all-zero licence is therefore handled as missing.</p>
 */
final class FcttLicenses {

    private FcttLicenses() {
    }

    static boolean isUsable(String license) {
        if (license == null) {
            return false;
        }
        String trimmed = license.trim();
        return !trimmed.isEmpty() && !trimmed.chars().allMatch(character -> character == '0');
    }
}
