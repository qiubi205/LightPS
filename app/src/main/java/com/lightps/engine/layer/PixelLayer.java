package com.lightps.engine.layer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import com.lightps.engine.brush.Brush;
import com.lightps.engine.filter.Filter;

/**
 * A layer that stores actual pixel data.
 * Analogous to Krita's KisPaintLayer + KisPaintDevice.
 */
public class PixelLayer extends Layer {

    private Bitmap pixels;     // ARGB_8888, the actual pixel buffer

    public PixelLayer(String name, int width, int height) {
        super(name, width, height);
        this.pixels = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        // initialise to transparent
        pixels.eraseColor(0x00000000);
    }

    public PixelLayer(String name, Bitmap bitmap) {
        super(name, bitmap.getWidth(), bitmap.getHeight());
        this.pixels = bitmap.copy(Bitmap.Config.ARGB_8888, true);
    }

    // ── Pixel data access ──────────────────────────────

    public Bitmap getPixels() {
        return pixels;
    }

    /** Swap in a new pixel buffer (replaces current). */
    public void setPixels(Bitmap bitmap) {
        if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        }
        this.pixels = bitmap;
        this.width = bitmap.getWidth();
        this.height = bitmap.getHeight();
    }

    /** Read a single pixel (returns ARGB value). */
    public int getPixel(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0;
        return pixels.getPixel(x, y);
    }

    /** Write a single pixel. */
    public void setPixel(int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        pixels.setPixel(x, y, argb);
    }

    // ── Drawing operations ─────────────────────────────

    /** Draw a brush stroke at the given coordinate. */
    public void drawBrush(float x, float y, Brush brush) {
        Canvas c = new Canvas(pixels);
        brush.apply(c, x, y);
    }

    /** Fill an area with a solid colour. */
    public void fill(Rect area, int color) {
        Rect clamped = new Rect(area);
        clamped.intersect(0, 0, width, height);
        Canvas c = new Canvas(pixels);
        Paint p = new Paint();
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        c.drawRect(clamped, p);
    }

    /** Clear to transparent. */
    public void clear() {
        pixels.eraseColor(0x00000000);
    }

    // ── Filter ─────────────────────────────────────────

    public void applyFilter(Filter filter) {
        filter.apply(pixels);
    }

    // ── Layer overrides ────────────────────────────────

    @Override
    public boolean isPixelLayer() {
        return true;
    }

    @Override
    public boolean isGroup() {
        return false;
    }

    @Override
    public void render(Bitmap dst, int offsetX, int offsetY) {
        if (!visible || opacity == 0) return;

        Canvas c = new Canvas(dst);
        Paint p = new Paint();
        p.setAlpha(opacity);

        // Apply layer mask if present
        // (full mask implementation in render pipeline)

        c.drawBitmap(pixels, offsetX, offsetY, p);
    }

    @Override
    public Rect getContentBounds() {
        return computeContentBounds(pixels);
    }

    @Override
    public PixelLayer clone() {
        PixelLayer clone = (PixelLayer) super.clone();
        clone.pixels = this.pixels.copy(Bitmap.Config.ARGB_8888, true);
        return clone;
    }

    // ── Helpers ────────────────────────────────────────

    /** Crop to minimal bounding box of non-transparent pixels. */
    public static Rect computeContentBounds(Bitmap bmp) {
        int minX = bmp.getWidth(), minY = bmp.getHeight();
        int maxX = 0, maxY = 0;
        boolean hasContent = false;

        for (int y = 0; y < bmp.getHeight(); y++) {
            for (int x = 0; x < bmp.getWidth(); x++) {
                if ((bmp.getPixel(x, y) >>> 24) != 0) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                    hasContent = true;
                }
            }
        }
        return hasContent ? new Rect(minX, minY, maxX + 1, maxY + 1)
                          : new Rect(0, 0, 0, 0);
    }
}
