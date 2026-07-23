package com.lightps.engine.selection;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;

/**
 * A selection mask stored as a single-channel 8-bit bitmap.
 * 0 = fully unselected, 255 = fully selected.
 * Analogous to Krita's KisSelection.
 */
public class Selection implements Cloneable {

    private Bitmap mask;       // ALPHA_8 bitmap; value = selection amount
    private int width;
    private int height;
    private boolean inverse;

    public Selection(int width, int height) {
        this.width = width;
        this.height = height;
        this.mask = Bitmap.createBitmap(width, height, Config.ALPHA_8);
        this.inverse = false;
        // Default: fully selected
        clearTo(255);
    }

    // ── Pixel-level access ─────────────────────────────

    /** Get selection value (0..255) at (x,y). */
    public int getValue(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0;
        int px = mask.getPixel(x, y);
        return Color.alpha(px);
    }

    /** Set selection value (0..255) at (x,y). */
    public void setValue(int x, int y, int value) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        mask.setPixel(x, y, Color.argb(clamp(value), 0, 0, 0));
    }

    /** Fill entire mask to a constant value. */
    public void clearTo(int value) {
        int v = clamp(value);
        mask.eraseColor(Color.argb(v, 0, 0, 0));
    }

    // ── Geometric selection operations ─────────────────

    /** Add a rectangular region to the selection. */
    public void addRect(Rect rect) {
        Rect r = new Rect(rect);
        r.intersect(0, 0, width, height);
        for (int y = r.top; y < r.bottom; y++) {
            for (int x = r.left; x < r.right; x++) {
                setValue(x, y, 255);
            }
        }
    }

    /** Subtract a rectangular region from the selection. */
    public void subtractRect(Rect rect) {
        Rect r = new Rect(rect);
        r.intersect(0, 0, width, height);
        for (int y = r.top; y < r.bottom; y++) {
            for (int x = r.left; x < r.right; x++) {
                setValue(x, y, 0);
            }
        }
    }

    /** Add an elliptical region to the selection. */
    public void addEllipse(int cx, int cy, int rx, int ry) {
        float rx2 = rx * rx;
        float ry2 = ry * ry;
        for (int y = Math.max(0, cy - ry); y <= Math.min(height - 1, cy + ry); y++) {
            for (int x = Math.max(0, cx - rx); x <= Math.min(width - 1, cx + rx); x++) {
                float dx = x - cx;
                float dy = y - cy;
                if ((dx * dx) / rx2 + (dy * dy) / ry2 <= 1f) {
                    setValue(x, y, 255);
                }
            }
        }
    }

    /** Add a polygonal/lasso region using the given points. */
    public void addPolygon(Point[] points) {
        if (points.length < 3) return;
        Path path = new Path();
        path.moveTo(points[0].x, points[0].y);
        for (int i = 1; i < points.length; i++) {
            path.lineTo(points[i].x, points[i].y);
        }
        path.close();
        // Rasterise the Path onto the mask
        Bitmap tmp = Bitmap.createBitmap(width, height, Config.ALPHA_8);
        Canvas c = new Canvas(tmp);
        Paint p = new Paint();
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.FILL);
        c.drawPath(path, p);
        // Merge tmp into mask
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int v = Color.alpha(tmp.getPixel(x, y));
                if (v > 0) setValue(x, y, (byte) Math.max(getValue(x, y), v));
            }
        }
        tmp.recycle();
    }

    // ── Magic wand ─────────────────────────────────────

    /**
     * Flood-fill select based on a source bitmap and a tolerance (0..255).
     * This is a simple 4-directional flood fill.
     */
    public void magicWand(Bitmap source, int startX, int startY, int tolerance) {
        if (startX < 0 || startY < 0 || startX >= source.getWidth() || startY >= source.getHeight())
            return;

        int[] pixels = new int[source.getWidth() * source.getHeight()];
        source.getPixels(pixels, 0, source.getWidth(), 0, 0, source.getWidth(), source.getHeight());
        int targetColor = pixels[startY * source.getWidth() + startX] & 0x00FFFFFF;
        boolean[] visited = new boolean[pixels.length];

        // BFS flood fill
        int[] queueX = new int[pixels.length];
        int[] queueY = new int[pixels.length];
        int head = 0, tail = 0;
        queueX[tail] = startX;
        queueY[tail] = startY;
        tail++;
        visited[startY * source.getWidth() + startX] = true;

        int w = source.getWidth();
        int h = source.getHeight();

        while (head < tail) {
            int cx = queueX[head];
            int cy = queueY[head];
            head++;
            setValue(cx, cy, 255);

            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : dirs) {
                int nx = cx + d[0];
                int ny = cy + d[1];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                int idx = ny * w + nx;
                if (visited[idx]) continue;
                visited[idx] = true;
                int nc = pixels[idx] & 0x00FFFFFF;
                int diff = Math.abs(((nc >> 16) & 0xFF) - ((targetColor >> 16) & 0xFF))
                         + Math.abs(((nc >> 8) & 0xFF) - ((targetColor >> 8) & 0xFF))
                         + Math.abs((nc & 0xFF) - (targetColor & 0xFF));
                if (diff / 3 <= tolerance) {
                    queueX[tail] = nx;
                    queueY[tail] = ny;
                    tail++;
                }
            }
        }
    }

    // ── Transform ──────────────────────────────────────

    public void feather(int radius) {
        if (radius <= 0) return;
        // Simple box blur over the mask
        int[] src = new int[width * height];
        int[] dst = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                src[y * width + x] = getValue(x, y);
            }
        }
        // Horizontal pass
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int sum = 0, count = 0;
                for (int dx = -radius; dx <= radius; dx++) {
                    int sx = x + dx;
                    if (sx >= 0 && sx < width) {
                        sum += src[y * width + sx];
                        count++;
                    }
                }
                dst[y * width + x] = sum / count;
            }
        }
        // Vertical pass
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int sum = 0, count = 0;
                for (int dy = -radius; dy <= radius; dy++) {
                    int sy = y + dy;
                    if (sy >= 0 && sy < height) {
                        sum += dst[sy * width + x];
                        count++;
                    }
                }
                setValue(x, y, sum / count);
            }
        }
    }

    public void expand(int radius) {
        morph(true, radius);
    }

    public void contract(int radius) {
        morph(false, radius);
    }

    private void morph(boolean expand, int radius) {
        int[] src = new int[width * height];
        int[] result = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                src[y * width + x] = getValue(x, y);
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean found = false;
                for (int dy = -radius; dy <= radius && !found; dy++) {
                    for (int dx = -radius; dx <= radius && !found; dx++) {
                        int sx = x + dx, sy = y + dy;
                        if (sx >= 0 && sy >= 0 && sx < width && sy < height) {
                            int v = src[sy * width + sx];
                            if ((expand && v >= 128) || (!expand && v < 128)) {
                                result[y * width + x] = expand ? 255 : 0;
                                found = true;
                            }
                        }
                    }
                }
                if (!found) result[y * width + x] = expand ? 0 : 255;
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                setValue(x, y, result[y * width + x]);
            }
        }
    }

    // ── Boolean operations ─────────────────────────────

    public Selection union(Selection other) {
        Selection result = new Selection(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                result.setValue(x, y, Math.max(getValue(x, y), other.getValue(x, y)));
            }
        }
        return result;
    }

    public Selection intersect(Selection other) {
        Selection result = new Selection(width, height);
        result.clearTo(0);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                result.setValue(x, y, Math.min(getValue(x, y), other.getValue(x, y)));
            }
        }
        return result;
    }

    public Selection subtract(Selection other) {
        Selection result = new Selection(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int v = getValue(x, y) - other.getValue(x, y);
                result.setValue(x, y, Math.max(0, v));
            }
        }
        return result;
    }

    // ── Inverse ────────────────────────────────────────

    public boolean isInverse() { return inverse; }
    public void setInverse(boolean inverse) { this.inverse = inverse; }
    public void invert() { this.inverse = !this.inverse; }

    // ── Utility ────────────────────────────────────────

    public Bitmap getMaskBitmap() { return mask; }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    /** Return bounding rectangle of the selected region. */
    public Rect getBounds() {
        int minX = width, minY = height, maxX = 0, maxY = 0;
        boolean hasSelection = false;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (getValue(x, y) >= 128) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                    hasSelection = true;
                }
            }
        }
        return hasSelection ? new Rect(minX, minY, maxX + 1, maxY + 1) : new Rect(0, 0, 0, 0);
    }

    /** Whether the selection is empty (no pixel >= 128). */
    public boolean isEmpty() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (getValue(x, y) >= 128) return false;
            }
        }
        return true;
    }

    @Override
    public Selection clone() {
        Selection s = new Selection(width, height);
        s.mask = this.mask.copy(Config.ALPHA_8, true);
        s.inverse = this.inverse;
        return s;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
