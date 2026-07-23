package com.lightps.engine.layer;

import android.graphics.Bitmap;
import android.graphics.Rect;

import com.lightps.engine.selection.Selection;

import java.util.Objects;

/**
 * Base class for all layer types.
 * Analogous to Krita's KisNode + KisLayer.
 */
public abstract class Layer implements Cloneable {

    protected String name;
    protected int width;
    protected int height;
    protected int opacity;            // 0..255
    protected BlendMode blendMode;
    protected boolean visible;
    protected boolean locked;
    protected Selection mask;         // optional layer mask

    protected Layer(String name, int width, int height) {
        this.name = Objects.requireNonNullElse(name, "Layer");
        this.width = width;
        this.height = height;
        this.opacity = 255;
        this.blendMode = BlendMode.NORMAL;
        this.visible = true;
        this.locked = false;
        this.mask = null;
    }

    // ── Getters / Setters ──────────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public int getOpacity() { return opacity; }
    public void setOpacity(int opacity) {
        this.opacity = Math.max(0, Math.min(255, opacity));
    }

    public BlendMode getBlendMode() { return blendMode; }
    public void setBlendMode(BlendMode blendMode) { this.blendMode = blendMode; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public Selection getMask() { return mask; }
    public void setMask(Selection mask) { this.mask = mask; }

    // ── Subclass hooks ─────────────────────────────────

    /** Whether this layer holds pixels directly. */
    public abstract boolean isPixelLayer();

    /** Whether this is a group (folder) layer. */
    public abstract boolean isGroup();

    /**
     * Render this layer onto destination bitmap at given global offset.
     * @param dst       destination ARGB_8888 bitmap (pre-allocated canvas-sized)
     * @param offsetX   global X offset (layer left edge on canvas)
     * @param offsetY   global Y offset (layer top edge on canvas)
     */
    public abstract void render(Bitmap dst, int offsetX, int offsetY);

    /**
     * The effective bounds of pixel content (crop region).
     * Default is full size; override for sparse layers.
     */
    public Rect getContentBounds() {
        return new Rect(0, 0, width, height);
    }

    @Override
    public Layer clone() {
        try {
            Layer copy = (Layer) super.clone();
            if (mask != null) copy.mask = mask.clone();
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}
