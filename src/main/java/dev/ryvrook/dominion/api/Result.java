package dev.ryvrook.dominion.api;

/** Outcome of a service call with a player-facing reason when it fails. */
public record Result(boolean success, String message) {
    public static Result pass() {
        return new Result(true, "");
    }

    public static Result pass(String message) {
        return new Result(true, message);
    }

    public static Result fail(String message) {
        return new Result(false, message);
    }

    public boolean failed() {
        return !success;
    }
}
