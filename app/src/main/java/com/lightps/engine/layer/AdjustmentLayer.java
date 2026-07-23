package com.lightps.engine.layer;

import android.graphics.Bitmap;

/**
 * An adjustment layer that applies a colour/tonal transformation
 * to all layers below it (within its group).
 * Analogous to Krita's KisAdjustmentLayer.
 */
public class AdjustmentLayer extends Layer {

    public enum Type {
        BRIGHTNESS_CONTRAST,
        LEVELS,
        CURVES,
        HUE_SATURATION,
        COLOR_BALANCE,
        BLACK_WHITE,
        INVERT,
        THRESHOLD,
        GRADIENT_MAP,
        PHOTO_FILTER,
        CHANNEL_MIXER,
        EXPOSURE,
        VIBRANCE,
    }

    private Type type;
    private float[] params;          // opaque parameter array; interpretation depends on type

    public AdjustmentLayer(String name, int width, int height, Type type) {
        super(name, width, height);
        this.type = type;
        this.params = defaultParams(type);
    }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public float[] getParams() { return params; }
    public void setParams(float[] params) { this.params = params; }

    public float getParam(int index) {
        return (params != null && index < params.length) ? params[index] : 0f;
    }

    public void setParam(int index, float value) {
        if (params != null && index < params.length) {
            params[index] = value;
        }
    }

    /**
     * Apply the adjustment to the given source pixel buffer,
     * writing results in-place or to dst.
     */
    public void apply(Bitmap src, Bitmap dst) {
        if (src == null || dst == null) return;
        int w = Math.min(src.getWidth(), dst.getWidth());
        int h = Math.min(src.getHeight(), dst.getHeight());
        int[] pixels = new int[w * h];
        src.getPixels(pixels, 0, w, 0, 0, w, h);

        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = applyPixel(pixels[i]);
        }

        dst.setPixels(pixels, 0, w, 0, 0, w, h);
    }

    /** Transform a single ARGB pixel according to the adjustment type. */
    private int applyPixel(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        switch (type) {
            case INVERT:
                r = 255 - r;
                g = 255 - g;
                b = 255 - b;
                break;
            case BRIGHTNESS_CONTRAST:
                float br = getParam(0);   // -255..255
                float ct = getParam(1);   // -255..255
                float factor = (259f * (ct + 255f)) / (255f * (259f - ct));
                r = clamp((int) (factor * (r - 128) + 128 + br));
                g = clamp((int) (factor * (g - 128) + 128 + br));
                b = clamp((int) (factor * (b - 128) + 128 + br));
                break;
            case THRESHOLD:
                int level = clamp((int) getParam(0));
                r = (r >= level) ? 255 : 0;
                g = (g >= level) ? 255 : 0;
                b = (b >= level) ? 255 : 0;
                break;
            default:
                break;
        }

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ── Layer overrides ────────────────────────────────

    @Override
    public boolean isPixelLayer() {
        return false;
    }

    @Override
    public boolean isGroup() {
        return false;
    }

    @Override
    public void render(Bitmap dst, int offsetX, int offsetY) {
        // Adjustment layers are applied during the render pipeline,
        // not rendered as direct bitmaps.
    }

    // ── Defaults ───────────────────────────────────────

    private static float[] defaultParams(Type t) {
        switch (t) {
            case BRIGHTNESS_CONTRAST: return new float[]{0f, 0f};
            case LEVELS:              return new float[]{0, 1.0f, 255};
            case THRESHOLD:           return new float[]{128};
            case HUE_SATURATION:      return new float[]{0f, 0f, 0f};
            case EXPOSURE:            return new float[]{0f};
            case VIBRANCE:            return new float[]{0f};
            default:                  return new float[]{0};
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
