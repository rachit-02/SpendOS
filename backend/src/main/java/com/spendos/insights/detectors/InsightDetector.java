package com.spendos.insights.detectors;

import java.util.List;

/** A deterministic rule that turns a user's own spending history into insights. */
public interface InsightDetector {

    List<InsightCandidate> detect(SpendingContext context);
}
