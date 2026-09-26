package io.umce.api.patch;

public final class PatchEvaluation {
    public enum Status { READY, DISABLED, UNSUPPORTED, UNSAFE, UNKNOWN }

    private final Status status;
    private final String reason;

    public PatchEvaluation(Status status, String reason) {
        if (status == null) throw new IllegalArgumentException("status must not be null");
        if (reason == null || reason.trim().isEmpty()) throw new IllegalArgumentException("reason must not be blank");
        this.status = status;
        this.reason = reason.trim();
    }

    public Status getStatus() { return status; }
    public String getReason() { return reason; }
    public boolean isReady() { return status == Status.READY; }

    public static PatchEvaluation ready(String reason) { return new PatchEvaluation(Status.READY, reason); }
    public static PatchEvaluation unknown(String reason) { return new PatchEvaluation(Status.UNKNOWN, reason); }
    public static PatchEvaluation unsafe(String reason) { return new PatchEvaluation(Status.UNSAFE, reason); }
}
