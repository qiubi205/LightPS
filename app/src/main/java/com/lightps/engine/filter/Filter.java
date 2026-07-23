package com.lightps.engine.filter;

import android.graphics.Bitmap;

/**
 * Base class for image filters.
 * Analogous to Krita's KisFilter.
 */
public abstract class Filter {

    private final String name;

    protected Filter(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * Apply this filter to a pixel buffer (in-place or creating a new one).
     * Default implementation calls apply(int[], w, h).
     */
    public void apply(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        apply(pixels, w, h);
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
    }

    /** Apply pixel transformation. Subclass must implement. */
    public abstract void apply(int[] pixels, int width, int height);
}
