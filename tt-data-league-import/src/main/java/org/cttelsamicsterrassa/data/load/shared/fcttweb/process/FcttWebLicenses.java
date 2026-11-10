package org.cttelsamicsterrassa.data.load.shared.fcttweb.process;

/**
 * Tells fctt.cat season-registration licences apart from placeholders.
 *
 * <p>Some FCTT actas write {@code "0"} as the licence of every player. That value does not
 * identify anyone: treating it as a licence would resolve every such player to one shared
 * {@code PlayerSeason}. A blank or all-zero licence is therefore handled as missing.</p>
 */
public final class FcttWebLicenses {

    private FcttWebLicenses() {
    }

    public static boolean isUsable(String license) {
        if (license == null) {
            return false;
        }
        String trimmed = license.trim();
        return !trimmed.isEmpty() && !trimmed.chars().allMatch(character -> character == '0');
    }
}
