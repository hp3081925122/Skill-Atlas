package com.hp.skillatlas.data;

public enum NodeShape {
    RSQUARE("rsquare"),
    SQUARE("square"),
    CIRCLE("circle"),
    DIAMOND("diamond"),
    PENTAGON("pentagon"),
    HEXAGON("hexagon"),
    OCTAGON("octagon"),
    HEART("heart"),
    GEAR("gear"),
    NONE("none");

    public final String key;

    NodeShape(String key) {
        this.key = key;
    }

    public static NodeShape byKey(String key) {
        for (NodeShape s : values()) {
            if (s.key.equals(key)) {
                return s;
            }
        }
        throw new AtlasException("skill_atlas.error.bad_field", "shape");
    }
}
