package com.example.observability_sandbox.evaluation;

import java.util.List;

public final class EvaluationDataset {

    private static final String POSITIVE = "POSITIVE";
    private static final String NEGATIVE = "NEGATIVE";

    private static final List<EvaluationCase> DEFAULT_CASES = List.of(
            new EvaluationCase("pos-1", "I absolutely loved this movie and would watch it again.", POSITIVE),
            new EvaluationCase("pos-2", "The customer support team was incredibly helpful and friendly.", POSITIVE),
            new EvaluationCase("pos-3", "What a fantastic concert, the energy in the crowd was unreal!", POSITIVE),
            new EvaluationCase("pos-4", "This new update makes the app so much easier to use.", POSITIVE),
            new EvaluationCase("pos-5", "Dinner was delicious and the service was impeccable.", POSITIVE),
            new EvaluationCase("pos-6", "My package arrived early and the quality exceeded expectations.", POSITIVE),
            new EvaluationCase("pos-7", "The presentation was inspiring and left me motivated.", POSITIVE),
            new EvaluationCase("pos-8", "I’m grateful for such a supportive team at work.", POSITIVE),
            new EvaluationCase("pos-9", "The tutorial was clear and helped me learn quickly.", POSITIVE),
            new EvaluationCase("pos-10", "Sunset at the beach tonight was breathtaking.", POSITIVE),
            new EvaluationCase("neg-1", "This is the worst product I have ever purchased.", NEGATIVE),
            new EvaluationCase("neg-2", "Customer service ignored me and never resolved the issue.", NEGATIVE),
            new EvaluationCase("neg-3", "The meeting was a waste of time and accomplished nothing.", NEGATIVE),
            new EvaluationCase("neg-4", "Traffic today was unbearable and ruined my mood.", NEGATIVE),
            new EvaluationCase("neg-5", "The software update broke all my favorite features.", NEGATIVE),
            new EvaluationCase("neg-6", "I felt completely let down by their empty promises.", NEGATIVE),
            new EvaluationCase("neg-7", "The hotel room was dirty and smelled terrible.", NEGATIVE),
            new EvaluationCase("neg-8", "Waiting in line for hours was an exhausting experience.", NEGATIVE),
            new EvaluationCase("neg-9", "The instructions were confusing and led to mistakes.", NEGATIVE),
            new EvaluationCase("neg-10", "I regret recommending this to my friends.", NEGATIVE)
    );

    private EvaluationDataset() {
    }

    public static List<EvaluationCase> defaultCases() {
        return DEFAULT_CASES;
    }
}
