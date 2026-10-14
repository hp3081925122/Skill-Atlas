package com.hp.skillatlas.data;

import java.util.Arrays;
import java.util.List;

public final class AtlasException extends RuntimeException {
    private final String key;
    private final String[] args;

    public AtlasException(String key, String... args) {
        super(key + " " + String.join(", ", args));
        this.key = key;
        this.args = args;
    }

    public String key() {
        return key;
    }

    public List<String> args() {
        return Arrays.asList(args);
    }
}
