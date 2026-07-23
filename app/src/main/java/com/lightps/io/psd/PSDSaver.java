package com.lightps.io.psd;

import com.lightps.engine.layer.BlendMode;
import com.lightps.engine.layer.GroupLayer;
import com.lightps.engine.layer.Layer;
import com.lightps.engine.layer.LayerManager;
import com.lightps.engine.layer.PixelLayer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * PSD file writer.
 * Saves a LayerManager to PSD format.
 * Reference: Krita's psd_saver.cpp
 */
public class PSDSaver {

    /**
     * Save layer manager to PSD output stream.
     */
    public void save(LayerManager lm, OutputStream out) throws IOException {
        int w = lm.getWidth();
        int h = lm.getHeight();

        // Collect visible layers (bottom-to-top)
        List<Layer> flatLayers = flattenLayerList(lm);

        // 1. Header
        PSDHeader header = new PSDHeader();
        header.signature = PSDConstants.SIGNATURE;
        header.version = PSDConstants.VERSION_PSD;
        header.nChannels = 4; // RGBA
        header.height = h;
        header.width = w;
        header.channelDepth = 8;
        header.colorMode = PSDConstants.COLOR_MODE_RGB;
        header.write(out);

        // 2. Color mode data (empty for RGB)
        out.write(new byte[4]); // 0 length

        // 3. Image resources (empty)
        out.write(new byte[4]); // 0 length

        // 4. Layer and mask info
        writeLayerMaskInfo(lm, flatLayers, out, w, h);

        // 5. Composite image data (flat version)
        writeImageData(lm, out, w, h);
    }

    // ── Layer mask info section ───────────────────────

    private void writeLayerMaskInfo(LayerManager lm, List<Layer> layers,
                                     OutputStream out, int w, int h) throws IOException {
        ByteArrayOutputStream layerSectionBuf = new ByteArrayOutputStream();

        // Layer count (positive = bottom-to-top in file; negative = top-to-bottom)
        int layerCount = layers.size();
        writeShort(layerSectionBuf, (short) layerCount);

        // Layer records: for each layer, write its PSDLayerRecord
        List<byte[]> channelDataList = new ArrayList<>();

        for (Layer layer : layers) {
            PSDLayerRecord rec = layerToRecord(layer, w, h, lm);
            rec.write(false); // not PSB
            layerSectionBuf.write(rec.write(false));

            // Channel data
            if (layer.isPixelLayer()) {
                PixelLayer pl = (PixelLayer) layer;
                int[] argb = new int[pl.getWidth() * pl.getHeight()];
                pl.getPixels().getPixels(argb, 0, pl.getWidth(), 0, 0, pl.getWidth(), pl.getHeight());
                byte[][] channels = PSDImageData.argbToChannels(argb, pl.getWidth(), pl.getHeight());

                for (int c = 0; c < 4; c++) {
                    // RLE encode each channel
                    byte[] rle = PSDImageData.encodeRLE(channels[c], 0, channels[c].length);
                    channelDataList.add(rle);
                }
            }
        }

        // Write channel image data
        for (byte[] chanData : channelDataList) {
            layerSectionBuf.write(chanData);
        }

        // Global layer mask info (empty)
        layerSectionBuf.write(new byte[4]); // 0 length

        // Write section length
        byte[] sectionData = layerSectionBuf.toByteArray();
        writeInt(out, sectionData.length);
        out.write(sectionData);
    }

    // ── Composite image data ──────────────────────────

    private void writeImageData(LayerManager lm, OutputStream out, int w, int h) throws IOException {
        android.graphics.Bitmap flat = lm.flatten();
        int[] argb = new int[w * h];
        flat.getPixels(argb, 0, w, 0, 0, w, h);

        byte[][] channels = PSDImageData.argbToChannels(argb, w, h);

        // Compression type: RLE
        writeShort(out, (short) PSDConstants.COMPRESS_RLE);

        // RLE row lengths for each channel
        int scanlineBytes = w;
        for (int c = 0; c < 4; c++) {
            for (int row = 0; row < h; row++) {
                byte[] rowData = new byte[scanlineBytes];
                System.arraycopy(channels[c], row * scanlineBytes, rowData, 0, scanlineBytes);
                byte[] rleRow = PSDImageData.encodeRLE(rowData, 0, scanlineBytes);
                writeShort(out, (short) rleRow.length);
            }
        }

        // RLE data
        for (int c = 0; c < 4; c++) {
            for (int row = 0; row < h; row++) {
                byte[] rowData = new byte[scanlineBytes];
                System.arraycopy(channels[c], row * scanlineBytes, rowData, 0, scanlineBytes);
                byte[] rleRow = PSDImageData.encodeRLE(rowData, 0, scanlineBytes);
                out.write(rleRow);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────

    private List<Layer> flattenLayerList(LayerManager lm) {
        List<Layer> result = new ArrayList<>();
        for (Layer layer : lm.getLayers()) {
            if (layer.isGroup()) {
                GroupLayer gl = (GroupLayer) layer;
                result.addAll(gl.getChildren());
            } else {
                result.add(layer);
            }
        }
        return result;
    }

    private PSDLayerRecord layerToRecord(Layer layer, int canvasW, int canvasH, LayerManager lm) {
        PSDLayerRecord rec = new PSDLayerRecord();
        // Use layer content bounds
        rec.top = 0;
        rec.left = 0;
        rec.bottom = canvasH;
        rec.right = canvasW;
        rec.nChannels = 4;
        rec.channels = new ArrayList<>();
        for (short id : new short[]{0, 1, 2, -1}) {
            PSDLayerRecord.ChannelInfo ci = new PSDLayerRecord.ChannelInfo();
            ci.channelId = id;
            ci.channelDataLength = 0; // filled during write
            rec.channels.add(ci);
        }
        rec.blendModeKey = layer.getBlendMode().psdKey();
        rec.opacity = layer.getOpacity();
        rec.visible = layer.isVisible();
        rec.layerName = layer.getName();
        return rec;
    }

    private static void writeShort(OutputStream out, short v) throws IOException {
        out.write((v >> 8) & 0xFF);
        out.write(v & 0xFF);
    }

    private static void writeInt(OutputStream out, int v) throws IOException {
        out.write((v >> 24) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write(v & 0xFF);
    }
}
