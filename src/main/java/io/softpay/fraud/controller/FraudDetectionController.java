package io.softpay.fraud.controller;

import io.softpay.fraud.dto.TransactionEvaluationRequest;
import io.softpay.fraud.dto.TransactionEvaluationResponse;
import io.softpay.fraud.service.FraudDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fraud")
@RequiredArgsConstructor
@Tag(name = "Fraud Detection", description = "Real-Time Payment Fraud Evaluation Engine")
public class FraudDetectionController {

    private final FraudDetectionService fraudDetectionService;

    @Operation(
            summary = "Evaluate Inflight Transaction",
            description = "Performs real-time sub-10ms fraud checks and determines if the transaction is ALLOWED or REJECTED."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Evaluation completed successfully (ALLOWED or REJECTED)",
                    content = @Content(schema = @Schema(implementation = TransactionEvaluationResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE)
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request payload or currency code",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class), mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)
            )
    })
    @PostMapping(value = "/evaluate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TransactionEvaluationResponse> evaluate(@Valid @RequestBody TransactionEvaluationRequest request) {
        TransactionEvaluationResponse response = fraudDetectionService.evaluate(request);
        return ResponseEntity.ok(response);
    }
}
