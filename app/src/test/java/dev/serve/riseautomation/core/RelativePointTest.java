package dev.serve.riseautomation.core;

import org.junit.Test;
import static org.junit.Assert.*;

public final class RelativePointTest {
    @Test public void mapsInsetsAndEdgesOnPhoneAndTablet() {
        assertEquals(24, RelativePoint.coordinate(0, 24, 1080), 0);
        assertEquals(1103, RelativePoint.coordinate(1, 24, 1080), 0);
        assertEquals(899.5f, RelativePoint.coordinate(0.5f, 100, 1600), 0);
        assertEquals(7, RelativePoint.coordinate(1, 7, 1), 0);
    }
    @Test public void rejectsNonFiniteOutsideAndEmptyBounds() {
        for (float ratio : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -0.1f, 1.1f}) {
            try { RelativePoint.coordinate(ratio, 0, 100); fail("Invalid coordinate accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        try { RelativePoint.coordinate(0.5f, 0, 0); fail("Empty bounds accepted"); }
        catch (IllegalArgumentException expected) { }
    }
}
