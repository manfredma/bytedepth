package manfred.bytedepth.domain.annotation;

public enum PlacementReason {
    EXACT,
    MISSING_REVISION,
    MODEL_MISMATCH,
    QUOTE_CHANGED,
    TARGET_MISSING,
    DUPLICATE_TARGET,
    BUDGET_EXCEEDED,
    NO_VISIBLE_SOURCE,
    LEGACY_EVIDENCE_MISSING
}
