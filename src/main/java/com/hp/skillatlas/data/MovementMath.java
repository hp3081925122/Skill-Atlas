package com.hp.skillatlas.data;

public final class MovementMath {
    private static final double DRAG = 0.98F;

    private MovementMath() {
    }

    public static double jumpVelocity(double original, double extraHeight, double gravity) {
        if (original <= 0 || extraHeight <= 0 || gravity <= 0
                || !Double.isFinite(original) || !Double.isFinite(extraHeight) || !Double.isFinite(gravity)) {
            return original;
        }
        double target = jumpHeight(original, gravity) + extraHeight;
        double lower = original;
        double upper = original + 1;
        while (jumpHeight(upper, gravity) < target) {
            upper *= 2;
        }
        for (int i = 0; i < 40; i++) {
            double middle = (lower + upper) / 2;
            if (jumpHeight(middle, gravity) < target) {
                lower = middle;
            } else {
                upper = middle;
            }
        }
        return (lower + upper) / 2;
    }

    private static double jumpHeight(double velocity, double gravity) {
        double offset = DRAG * gravity / (1 - DRAG);
        double ticks = Math.ceil(Math.log1p(velocity / offset) / -Math.log(DRAG));
        return (velocity + offset) * -Math.expm1(ticks * Math.log(DRAG)) / (1 - DRAG) - offset * ticks;
    }
}
