package io.softpay.fraud.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * PCI-DSS compliant utility for PAN masking and deterministic salted SHA-256 tokenization.
 */
public final class CardSecurityUtils {

    private CardSecurityUtils() {
        // utility class
    }

    /**
     * Masks the PAN according to PCI-DSS v4.0 requirement 3.4.
     * Displays at most the first 6 (BIN) and last 4 digits (e.g. 411111******1111).
     */
    public static String maskPan(String pan) {
        if (pan == null || pan.isBlank()) {
            return "[NO_CARD]";
        }
        String clean = pan.replaceAll("\\s+", "");
        if (clean.length() <= 10) {
            return "*".repeat(clean.length());
        }
        String bin = clean.substring(0, 6);
        String last4 = clean.substring(clean.length() - 4);
        int maskedCount = clean.length() - 10;
        return bin + "*".repeat(maskedCount) + last4;
    }

    /**
     * Computes a deterministic SHA-256 hash using an application-level secret salt.
     * Prevents rainbow table reversibility of tiny PAN keyspaces.
     */
    public static String hashPan(String pan, String salt) {
        if (pan == null) {
            throw new IllegalArgumentException("Card number cannot be null for hashing");
        }
        String effectiveSalt = (salt == null) ? "" : salt;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest((effectiveSalt + pan.trim()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encoded);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in runtime", e);
        }
    }
}
