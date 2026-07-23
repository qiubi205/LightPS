package com.lightps.engine.layer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A group (folder) layer that contains child layers.
 * Analogous to Krita's KisGroupLayer.
 * Supports pass-through blend mode for non-destructive compositing.
 */
public class GroupLayer extends Layer {

    private final List<Layer> children;

    public GroupLayer(String name, int width, int height) {
        super(name, width, height);
        this.children = new ArrayList<>();
        this.blendMode = BlendMode.PASSTHROUGH;
    }

    // ── Child management ───────────────────────────────

    public List<Layer> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public void addChild(Layer layer) {
        children.add(layer);
    }

    public void addChild(int index, Layer layer) {
        children.add(index, layer);
    }

    public void removeChild(Layer layer) {
        children.remove(layer);
    }

    public void removeChild(int index) {
        if (index >= 0 && index < children.size()) {
            children.remove(index);
        }
    }

    public int indexOf(Layer layer) {
        return children.indexOf(layer);
    }

    public int childCount() {
        return children.size();
    }

    public Layer getChild(int index) {
        return children.get(index);
    }

    public void moveChild(int from, int to) {
        Layer layer = children.remove(from);
        children.add(to, layer);
    }

    // ── Layer overrides ────────────────────────────────

    @Override
    public boolean isPixelLayer() {
        return false;
    }

    @Override
    public boolean isGroup() {
        return true;
    }

    @Override
    public void render(Bitmap dst, int offsetX, int offsetY) {
        if (!visible || opacity == 0) return;

        // In passthrough mode, children render directly into parent context.
        // In non-passthrough mode, children render into a temporary buffer first.
        if (blendMode == BlendMode.PASSTHROUGH) {
            for (Layer child : children) {
                child.render(dst, offsetX, offsetY);
            }
        } else {
            // Render group into temporary buffer, then composite
            Bitmap groupBuf = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            for (Layer child : children) {
                child.render(groupBuf, 0, 0);
            }
            Canvas c = new Canvas(dst);
            Paint p = new Paint();
            p.setAlpha(opacity);
            c.drawBitmap(groupBuf, offsetX, offsetY, p);
            groupBuf.recycle();
        }
    }

    @Override
    public GroupLayer clone() {
        GroupLayer clone = (GroupLayer) super.clone();
        // deep copy children
        for (Layer child : children) {
            clone.children.add(child.clone());
        }
        return clone;
    }
}
