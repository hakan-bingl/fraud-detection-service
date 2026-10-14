package io.softpay.fraud;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.softpay.fraud.controller.FraudDetectionController;
import io.softpay.fraud.controller.GlobalExceptionHandler;
import io.softpay.fraud.dto.RejectionStatus;
import io.softpay.fraud.dto.TransactionEvaluationRequest;
import io.softpay.fraud.dto.TransactionEvaluationResponse;
import io.softpay.fraud.service.FraudDetectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FraudDetectionController.class)
@Import(GlobalExceptionHandler.class)
class FraudDetectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FraudDetectionService fraudDetectionService;

    @Test
    @DisplayName("POST /api/v1/fraud/evaluate - 200 OK with ALLOWED decision")
    void testEvaluateSuccessAllowed() throws Exception {
        TransactionEvaluationResponse response = new TransactionEvaluationResponse(
                RejectionStatus.ALLOWED,
                "Transaction approved",
                0
        );
        when(fraudDetectionService.evaluate(any())).thenReturn(response);

        TransactionEvaluationRequest request = new TransactionEvaluationRequest(
                new BigDecimal("150.00"),
                "EUR",
                "TERM-001",
                10,
                "4532015112831111"
        );

        mockMvc.perform(post("/api/v1/fraud/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "corr-test-123")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "corr-test-123"))
                .andExpect(jsonPath("$.rejectionStatus").value("ALLOWED"))
                .andExpect(jsonPath("$.rejectionMessage").value("Transaction approved"))
                .andExpect(jsonPath("$.fraudScore").value(0))
                .andExpect(jsonPath("$.rejected").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/fraud/evaluate - 400 Bad Request on negative amount")
    void testNegativeAmountValidation() throws Exception {
        TransactionEvaluationRequest request = new TransactionEvaluationRequest(
                new BigDecimal("-50.00"),
                "EUR",
                "TERM-001",
                10,
                "4532015112831111"
        );

        mockMvc.perform(post("/api/v1/fraud/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    @DisplayName("POST /api/v1/fraud/evaluate - 400 Bad Request on invalid threat score")
    void testInvalidThreatScoreValidation() throws Exception {
        TransactionEvaluationRequest request = new TransactionEvaluationRequest(
                new BigDecimal("50.00"),
                "EUR",
                "TERM-001",
                150, // exceeds 100 max
                "4532015112831111"
        );

        mockMvc.perform(post("/api/v1/fraud/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.terminalThreatScore").exists());
    }

    @Test
    @DisplayName("POST /api/v1/fraud/evaluate - 400 Bad Request on invalid card number")
    void testInvalidCardNumberValidation() throws Exception {
        TransactionEvaluationRequest request = new TransactionEvaluationRequest(
                new BigDecimal("50.00"),
                "EUR",
                "TERM-001",
                10,
                "12345" // too short (must be 13 to 19 digits)
        );

        mockMvc.perform(post("/api/v1/fraud/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.cardNumber").exists());
    }
}
