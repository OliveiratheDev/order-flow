package com.start.overflow.shared.idempotency;

public record IdempotencyRecord(
        State state,
        String requestHash,
        int statusCode,
        String responseBody,
        String location
) {
    public static IdempotencyRecord inProgress(String requestHash) {
        return new IdempotencyRecord(State.IN_PROGRESS, requestHash, 0, null, null);
    }

    public static IdempotencyRecord done(String requestHash, int statusCode,
                                         String responseBody, String location) {
        return new IdempotencyRecord(State.DONE, requestHash, statusCode, responseBody, location);
    }

    public enum State {
        IN_PROGRESS,
        DONE
    }
}
