package com.lightps.io.psd;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * PSD file header (first 26 bytes).
 * Reference: Krita's psd_header.h
 */
public class PSDHeader {

    public String signature;      // "8BPS"
    public int version;           // 1 (PSD) or 2 (PSB)
    public int nChannels;         // 1..56
    public int height;            // pixels
    public int width;             // pixels
    public int channelDepth;      // bits per channel: 1, 8, 16, 32
    public int colorMode;         // see PSDConstants.COLOR_MODE_*
    public boolean tiffStyleLayerBlock;

    public String error;

    /**
     * Read PSD header from input stream.
     */
    public boolean read(InputStream in) throws IOException {
        byte[] sig = new byte[4];
        if (in.read(sig) != 4) {
            error = "Failed to read signature";
            return false;
        }
        signature = new String(sig, StandardCharsets.US_ASCII);
        if (!PSDConstants.SIGNATURE.equals(signature)) {
            error = "Invalid PSD signature: " + signature;
            return false;
        }

        byte[] buf = new byte[22];
        if (in.read(buf) != 22) {
            error = "Failed to read header";
            return false;
        }
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN);

        version = bb.getShort() & 0xFFFF;
        if (version != PSDConstants.VERSION_PSD && version != PSDConstants.VERSION_PSB) {
            error = "Unsupported PSD version: " + version;
            return false;
        }

        // Skip 6 reserved bytes
        bb.position(bb.position() + 6);

        nChannels = bb.getShort() & 0xFFFF;
        height = bb.getInt();
        width = bb.getInt();
        channelDepth = bb.getShort() & 0xFFFF;
        colorMode = bb.getShort() & 0xFFFF;

        // Validate
        if (nChannels < 1 || nChannels > 56) {
            error = "Invalid channel count: " + nChannels;
            return false;
        }

        return true;
    }

    /**
     * Write PSD header to output stream.
     */
    public boolean write(OutputStream out) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(26).order(ByteOrder.BIG_ENDIAN);
        bb.put(signature.getBytes(StandardCharsets.US_ASCII));
        bb.putShort((short) version);
        bb.put(new byte[6]); // reserved
        bb.putShort((short) nChannels);
        bb.putInt(height);
        bb.putInt(width);
        bb.putShort((short) channelDepth);
        bb.putShort((short) colorMode);
        out.write(bb.array());
        return true;
    }

    /** Bits per channel → bytes per channel. */
    public int bytesPerChannel() {
        return channelDepth / 8;
    }

    /** Total row bytes (uncompressed, per channel). */
    public int rowBytes() {
        return width * bytesPerChannel();
    }

    @Override
    public String toString() {
        return String.format(
                "PSDHeader{ver=%d, %dx%d, %d channels, %d bpc, mode=%d}",
                version, width, height, nChannels, channelDepth, colorMode);
    }
}
