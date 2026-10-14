package io.softpay.fraud.model.records.data;

public record RuleEvaluationResult(
    String ruleName,
    int scoreContribution,
    boolean hardReject,
    String reason
) {
    public static RuleEvaluationResult passed(String ruleName) {
        return new RuleEvaluationResult(ruleName, 0, false, null);
    }

    public static RuleEvaluationResult warning(String ruleName, int score, String reason) {
        return new RuleEvaluationResult(ruleName, score, false, reason);
    }

    public static RuleEvaluationResult reject(String ruleName, int score, String reason) {
        return new RuleEvaluationResult(ruleName, score, true, reason);
    }
}
