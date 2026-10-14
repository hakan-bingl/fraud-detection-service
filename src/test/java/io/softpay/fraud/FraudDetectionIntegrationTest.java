package io.softpay.fraud;

import io.softpay.fraud.dto.RejectionStatus;
import io.softpay.fraud.dto.TransactionEvaluationRequest;
import io.softpay.fraud.dto.TransactionEvaluationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FraudDetectionIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("End-to-end evaluation: Valid transaction on known active terminal is ALLOWED")
    void testEndToEndAllowedTransaction() {
        TransactionEvaluationRequest request = new TransactionEvaluationRequest(
                new BigDecimal("120.00"),
                "EUR",
                "TERM-001",
                5,
                "4532015112831111"
        );

        ResponseEntity<TransactionEvaluationResponse> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/fraud/evaluate",
                request,
                TransactionEvaluationResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().rejectionStatus()).isEqualTo(RejectionStatus.ALLOWED);
        assertThat(response.getBody().rejectionMessage()).isEqualTo("Transaction approved");
        assertThat(response.getBody().fraudScore()).isLessThan(70);
    }

    @Test
    @DisplayName("End-to-end evaluation: Unknown terminal triggers REJECTED with score 100")
    void testEndToEndUnknownTerminalRejected() {
        TransactionEvaluationRequest request = new TransactionEvaluationRequest(
                new BigDecimal("50.00"),
                "EUR",
                "UNKNOWN-UNREGISTERED-TERMINAL",
                0,
                "4532015112831111"
        );

        ResponseEntity<TransactionEvaluationResponse> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/fraud/evaluate",
                request,
                TransactionEvaluationResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().rejectionStatus()).isEqualTo(RejectionStatus.REJECTED);
        assertThat(response.getBody().rejectionMessage()).contains("Unknown or unregistered terminal ID");
        assertThat(response.getBody().fraudScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("Actuator health and metrics endpoints respond successfully")
    void testActuatorEndpoints() {
        ResponseEntity<String> health = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health",
                String.class
        );
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("UP");

        ResponseEntity<String> metrics = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/metrics",
                String.class
        );
        assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
