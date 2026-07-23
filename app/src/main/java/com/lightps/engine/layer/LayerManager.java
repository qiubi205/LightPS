package com.lightps.engine.layer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.lightps.engine.blend.BlendEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Manages the layer stack for a single document/image.
 * All layers share the same canvas dimensions.
 * Analogous to Krita's KisLayerManager / KisImage.
 */
public class LayerManager {

    private final int canvasWidth;
    private final int canvasHeight;
    private final List<Layer> layers;
    private int activeIndex;

    public LayerManager(int canvasWidth, int canvasHeight) {
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        this.layers = new ArrayList<>();
        this.activeIndex = -1;
    }

    // ── Dimensions ─────────────────────────────────────

    public int getWidth() { return canvasWidth; }
    public int getHeight() { return canvasHeight; }

    // ── Layer stack operations ─────────────────────────

    public List<Layer> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    public int layerCount() {
        return layers.size();
    }

    /** Bottom layer = index 0, top layer = index count-1. */
    public Layer getLayer(int index) {
        return layers.get(index);
    }

    public int indexOf(Layer layer) {
        return layers.indexOf(layer);
    }

    public void addLayer(Layer layer) {
        layers.add(layer);
        if (activeIndex < 0) activeIndex = 0;
    }

    public void addLayer(int index, Layer layer) {
        layers.add(index, layer);
        adjustActiveIndex();
    }

    public void removeLayer(int index) {
        layers.remove(index);
        adjustActiveIndex();
    }

    public void removeLayer(Layer layer) {
        int idx = layers.indexOf(layer);
        if (idx >= 0) removeLayer(idx);
    }

    /** Move layer from one position to another. */
    public void moveLayer(int from, int to) {
        if (from == to) return;
        Layer layer = layers.remove(from);
        layers.add(to, layer);
        adjustActiveIndex();
    }

    public void moveUp(Layer layer) {
        int idx = layers.indexOf(layer);
        if (idx >= 0 && idx < layers.size() - 1) moveLayer(idx, idx + 1);
    }

    public void moveDown(Layer layer) {
        int idx = layers.indexOf(layer);
        if (idx > 0) moveLayer(idx, idx - 1);
    }

    // ── Active / selected layer ────────────────────────

    public int getActiveIndex() { return activeIndex; }

    public Layer getActiveLayer() {
        return (activeIndex >= 0 && activeIndex < layers.size())
                ? layers.get(activeIndex) : null;
    }

    public void setActiveIndex(int index) {
        if (index >= -1 && index < layers.size()) {
            this.activeIndex = index;
        }
    }

    public void setActiveLayer(Layer layer) {
        int idx = layers.indexOf(layer);
        if (idx >= 0) this.activeIndex = idx;
    }

    // ── Flatten / composite ────────────────────────────

    /**
     * Composite all visible layers from bottom to top into a single bitmap.
     * Uses the BlendEngine for per-pixel blending.
     */
    public Bitmap flatten() {
        Bitmap result = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
        result.eraseColor(0x00000000); // start transparent

        for (int i = 0; i < layers.size(); i++) {
            Layer layer = layers.get(i);
            if (!layer.isVisible() || layer.getOpacity() == 0) continue;

            if (layer.isGroup() && ((GroupLayer) layer).getBlendMode() == BlendMode.PASSTHROUGH) {
                // passthrough group: children render directly and individually,
                // so the group level contributes nothing by itself.
                flattenGroup((GroupLayer) layer, result);
            } else {
                compositeLayer(result, layer);
            }
        }
        return result;
    }

    private void compositeLayer(Bitmap dst, Layer layer) {
        // Render layer content onto a temporary bitmap
        Bitmap layerBuf = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
        layerBuf.eraseColor(0x00000000);
        Canvas c = new Canvas(layerBuf);
        Paint p = new Paint();
        p.setAlpha(layer.getOpacity());
        c.drawBitmap(
                getLayerPixels(layer),
                0, 0, p
        );

        // Blend with dst
        int[] dstPixels = new int[canvasWidth * canvasHeight];
        int[] srcPixels = new int[canvasWidth * canvasHeight];
        dst.getPixels(dstPixels, 0, canvasWidth, 0, 0, canvasWidth, canvasHeight);
        layerBuf.getPixels(srcPixels, 0, canvasWidth, 0, 0, canvasWidth, canvasHeight);

        BlendEngine.blend(dstPixels, srcPixels, layer.getBlendMode(), layer.getOpacity());

        dst.setPixels(dstPixels, 0, canvasWidth, 0, 0, canvasWidth, canvasHeight);
        layerBuf.recycle();
    }

    private void flattenGroup(GroupLayer group, Bitmap result) {
        for (Layer child : group.getChildren()) {
            if (!child.isVisible()) continue;
            compositeLayer(result, child);
        }
    }

    /**
     * Extract a full-canvas Bitmap representation of a layer (with layer mask applied).
     */
    private Bitmap getLayerPixels(Layer layer) {
        if (layer.isPixelLayer()) {
            PixelLayer pl = (PixelLayer) layer;
            Bitmap bmp = pl.getPixels();

            // Scale if layer is smaller than canvas (shouldn't happen normally)
            if (bmp.getWidth() != canvasWidth || bmp.getHeight() != canvasHeight) {
                Bitmap scaled = Bitmap.createScaledBitmap(bmp, canvasWidth, canvasHeight, false);
                return scaled;
            }
            return bmp;
        } else if (layer.isGroup()) {
            GroupLayer gl = (GroupLayer) layer;
            Bitmap buf = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
            buf.eraseColor(0x00000000);
            for (Layer child : gl.getChildren()) {
                if (!child.isVisible()) continue;
                Canvas c = new Canvas(buf);
                Paint p = new Paint();
                p.setAlpha(child.getOpacity());
                c.drawBitmap(getLayerPixels(child), 0, 0, p);
            }
            return buf;
        }
        // adjustment layer — return empty
        return Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
    }

    // ── Helpers ────────────────────────────────────────

    private void adjustActiveIndex() {
        if (layers.isEmpty()) {
            activeIndex = -1;
        } else if (activeIndex >= layers.size()) {
            activeIndex = layers.size() - 1;
        } else if (activeIndex < 0 && !layers.isEmpty()) {
            activeIndex = 0;
        }
    }

    /** Create a duplicate of the entire layer stack. */
    public LayerManager duplicate() {
        LayerManager copy = new LayerManager(canvasWidth, canvasHeight);
        for (Layer layer : layers) {
            copy.addLayer(layer.clone());
        }
        copy.activeIndex = this.activeIndex;
        return copy;
    }
}
