package booking;

public enum Outcome {
    INVALID,
    UNAVAILABLE,
    HELD,
    REJECTED_HOLD_EXPIRED,
    REJECTED_DUPLICATE,
    CONFIRMED,
    RETRY
}