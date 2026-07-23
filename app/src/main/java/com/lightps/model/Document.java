package com.lightps.model;

import com.lightps.engine.color.ColorSpace;
import com.lightps.engine.layer.LayerManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * A single document: one image with a layer stack, colour space, file path.
 */
public class Document {

    private final LayerManager layerManager;
    private ColorSpace colorSpace;
    private File file;
    private boolean modified;
    private String title;

    public Document(int width, int height) {
        this.layerManager = new LayerManager(width, height);
        this.colorSpace = ColorSpace.SRGB;
        this.modified = false;
        this.title = "Untitled";
    }

    // ── Accessors ──────────────────────────────────────

    public LayerManager getLayerManager() {
        return layerManager;
    }

    public ColorSpace getColorSpace() {
        return colorSpace;
    }

    public void setColorSpace(ColorSpace colorSpace) {
        this.colorSpace = colorSpace;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
        this.title = file != null ? file.getName() : "Untitled";
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    public int getWidth() {
        return layerManager.getWidth();
    }

    public int getHeight() {
        return layerManager.getHeight();
    }

    // ── File I/O ───────────────────────────────────────

    /** Open PSD file. */
    public static Document open(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        try {
            com.lightps.io.psd.PSDLoader loader = new com.lightps.io.psd.PSDLoader();
            LayerManager lm = loader.load(fis);
            Document doc = new Document(lm.getWidth(), lm.getHeight());
            // Copy layers
            for (int i = 0; i < lm.layerCount(); i++) {
                doc.layerManager.addLayer(lm.getLayer(i));
            }
            doc.setFile(file);
            doc.setModified(false);
            return doc;
        } finally {
            fis.close();
        }
    }

    /** Save as PSD. */
    public void save() throws IOException {
        if (file == null) throw new IOException("No file path set");
        FileOutputStream fos = new FileOutputStream(file);
        try {
            com.lightps.io.psd.PSDSaver saver = new com.lightps.io.psd.PSDSaver();
            saver.save(layerManager, fos);
            modified = false;
        } finally {
            fos.close();
        }
    }

    /** Save as PNG. */
    public void saveAsPng(File outFile) throws IOException {
        android.graphics.Bitmap flat = layerManager.flatten();
        FileOutputStream fos = new FileOutputStream(outFile);
        try {
            flat.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos);
        } finally {
            fos.close();
        }
    }
}
