package io.softpay.fraud;

import io.softpay.fraud.model.records.request.TransactionEvaluationRequest;
import io.softpay.fraud.utils.CardSecurityUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardSecurityUtilsTest {

    private static final String TEST_SALT = "test-salt-secret";

    @ParameterizedTest
    @CsvSource({
            "4532015112831111, 453201******1111",
            "4111111111111111, 411111******1111",
            "5412751234567890123, 541275*********0123",
            "1234567890123, 123456***0123"
    })
    @DisplayName("Should mask PAN according to PCI-DSS standards (first 6 and last 4)")
    void testMaskPanStandard(String rawPan, String expectedMasked) {
        String masked = CardSecurityUtils.maskPan(rawPan);
        assertThat(masked).isEqualTo(expectedMasked);
    }

    @Test
    @DisplayName("Should safely handle short or blank PANs")
    void testMaskPanEdgeCases() {
        assertThat(CardSecurityUtils.maskPan(null)).isEqualTo("[NO_CARD]");
        assertThat(CardSecurityUtils.maskPan("")).isEqualTo("[NO_CARD]");
        assertThat(CardSecurityUtils.maskPan("12345678")).isEqualTo("********");
    }

    @Test
    @DisplayName("TransactionEvaluationRequest toString should never expose plain text PAN")
    void testToStringNeverExposesPan() {
        TransactionEvaluationRequest request = new TransactionEvaluationRequest(
                new BigDecimal("150.00"),
                "EUR",
                "TERM-001",
                10,
                "4532015112831111"
        );

        String str = request.toString();
        assertThat(str).doesNotContain("4532015112831111");
        assertThat(str).contains("453201******1111");
    }

    @Test
    @DisplayName("Should compute deterministic salted SHA-256 hash")
    void testDeterministicHash() {
        String hash1 = CardSecurityUtils.hashPan("4532015112831111", TEST_SALT);
        String hash2 = CardSecurityUtils.hashPan("4532015112831111", TEST_SALT);
        String differentSaltHash = CardSecurityUtils.hashPan("4532015112831111", "other-salt");

        assertThat(hash1).isNotNull().hasSize(64);
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isNotEqualTo(differentSaltHash);
    }

    @Test
    @DisplayName("Should throw exception when PAN is null for hashing")
    void testHashNullPanThrows() {
        assertThatThrownBy(() -> CardSecurityUtils.hashPan(null, TEST_SALT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
