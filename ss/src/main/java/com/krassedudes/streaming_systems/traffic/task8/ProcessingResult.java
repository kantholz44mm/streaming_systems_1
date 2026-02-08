package com.krassedudes.streaming_systems.traffic.task8;

import java.util.Optional;

public final class ProcessingResult {
    private final boolean ok;
    private final String outValue;
    private final String badValue;

    private ProcessingResult(boolean ok, String outValue, String badValue) {
        this.ok = ok;
        this.outValue = outValue;
        this.badValue = badValue;
    }

    public static ProcessingResult ok(String outValue) {
        return new ProcessingResult(true, outValue, null);
    }

    public static ProcessingResult bad(String badValue) {
        return new ProcessingResult(false, null, badValue);
    }

    public boolean isOk() {
        return ok;
    }

    public Optional<String> outValue() {
        return Optional.ofNullable(outValue);
    }

    public Optional<String> badValue() {
        return Optional.ofNullable(badValue);
    }
}