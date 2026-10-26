package dev.serve.riseautomation.core;

/** Resolution independent coordinates in live window bounds, including nonzero insets. */
public final class RelativePoint {
    private RelativePoint() { }
    public static float coordinate(float ratio, int start, int size) {
        if (!Float.isFinite(ratio) || ratio < 0 || ratio > 1 || size <= 0) {
            throw new IllegalArgumentException("Invalid relative coordinate or empty window");
        }
        return start + ratio * (size - 1);
    }
}
