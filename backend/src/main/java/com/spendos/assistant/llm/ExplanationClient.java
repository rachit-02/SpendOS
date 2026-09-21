package com.spendos.assistant.llm;

/**
 * Rewords an already-computed answer. Implementations receive only the question, the structured
 * findings and the deterministic draft; they never see raw database rows and are never the source
 * of any figure. Any exception means "use the draft".
 */
public interface ExplanationClient {

    String rewrite(String question, String factsJson, String draftAnswer);
}
