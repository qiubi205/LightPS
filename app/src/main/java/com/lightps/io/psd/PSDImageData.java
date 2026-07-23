package com.lightps.io.psd;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * PSD image data section: compressed pixel data.
 * Supports RLE and RAW modes.
 * Reference: Krita's psd_pixel_utils and psd_image_data.
 */
public class PSDImageData {

    /**
     * Read the composite (merged) image data section.
     *
     * @param in        input stream positioned at the image data header
     * @param width     image width in pixels
     * @param height    image height in pixels
     * @param channels  number of channels (3=RGB, 4=RGBA)
     * @param depth     bits per channel (8 or 16)
     * @param psb       true for PSB format
     * @return decoded channel data as byte[channels][width*height*depth/8]
     */
    public static byte[][] readImageData(InputStream in, int width, int height,
                                          int channels, int depth, boolean psb)
            throws IOException {

        int compression = readShort(in);
        int bytesPerChan = depth / 8;
        int scanlineBytes = width * bytesPerChan;

        byte[][] result = new byte[channels][height * scanlineBytes];

        switch (compression) {
            case PSDConstants.COMPRESS_RAW:
                for (int c = 0; c < channels; c++) {
                    readFully(in, result[c]);
                }
                break;

            case PSDConstants.COMPRESS_RLE: {
                // RLE: each channel has height row-length entries (2 bytes each)
                int[] rowLengths = new int[channels * height];
                for (int i = 0; i < rowLengths.length; i++) {
                    rowLengths[i] = psb ? readInt(in) : readShort(in);
                }

                for (int c = 0; c < channels; c++) {
                    byte[] chanData = result[c];
                    for (int row = 0; row < height; row++) {
                        int rleLen = rowLengths[c * height + row];
                        byte[] rleBuf = new byte[rleLen];
                        readFully(in, rleBuf);
                        decodeRLE(rleBuf, chanData, row * scanlineBytes, scanlineBytes);
                    }
                }
                break;
            }

            case PSDConstants.COMPRESS_ZIP:
            case PSDConstants.COMPRESS_ZIP_PREDICT: {
                // Read all remaining data and decompress
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                byte[] compressed = baos.toByteArray();
                byte[] decompressed = decompressZlib(compressed);

                // Parse per-channel
                int totalBytes = height * scanlineBytes;
                for (int c = 0; c < channels; c++) {
                    System.arraycopy(decompressed, c * totalBytes,
                            result[c], 0, totalBytes);
                }

                if (compression == PSDConstants.COMPRESS_ZIP_PREDICT) {
                    for (int c = 0; c < channels; c++) {
                        deltaDecodePrediction(result[c], width, height, bytesPerChan);
                    }
                }
                break;
            }

            default:
                throw new IOException("Unsupported compression type: " + compression);
        }

        return result;
    }

    // ── Pack channel data into ARGB ────────────────────

    /**
     * Convert 3- or 4-channel 8-bit byte arrays to ARGB int[].
     */
    public static int[] channelsToArgb(byte[][] channels, int width, int height, int nChannels) {
        int[] result = new int[width * height];
        byte[] red   = nChannels > 0 ? channels[0] : null;
        byte[] green = nChannels > 1 ? channels[1] : null;
        byte[] blue  = nChannels > 2 ? channels[2] : null;
        byte[] alpha = nChannels > 3 ? channels[3] : null;

        for (int i = 0; i < result.length; i++) {
            int r = red   != null ? (red[i] & 0xFF)   : 0;
            int g = green != null ? (green[i] & 0xFF)  : 0;
            int b = blue  != null ? (blue[i] & 0xFF)   : 0;
            int a = alpha != null ? (alpha[i] & 0xFF)  : 255;
            result[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        return result;
    }

    /**
     * Convert ARGB int[] to per-channel byte arrays.
     */
    public static byte[][] argbToChannels(int[] argb, int width, int height) {
        int count = argb.length;
        byte[] r = new byte[count];
        byte[] g = new byte[count];
        byte[] b = new byte[count];
        byte[] a = new byte[count];
        for (int i = 0; i < count; i++) {
            r[i] = (byte) ((argb[i] >> 16) & 0xFF);
            g[i] = (byte) ((argb[i] >> 8) & 0xFF);
            b[i] = (byte) (argb[i] & 0xFF);
            a[i] = (byte) ((argb[i] >> 24) & 0xFF);
        }
        return new byte[][]{r, g, b, a};
    }

    // ── RLE encode/decode ─────────────────────────────

    /** Decode one row of PSD/PSB packed-bit RLE data. */
    public static void decodeRLE(byte[] rleData, byte[] output, int offset, int expectedLen) {
        int rlePos = 0;
        int outPos = offset;
        int outEnd = offset + expectedLen;

        while (rlePos < rleData.length && outPos < outEnd) {
            int header = rleData[rlePos++];
            if (header >= 0) {
                // Literal run: copy next (header+1) bytes
                int count = header + 1;
                for (int i = 0; i < count && rlePos < rleData.length && outPos < outEnd; i++) {
                    output[outPos++] = rleData[rlePos++];
                }
            } else {
                // Replicated run: repeat next byte (1-header) times
                int count = 1 - header;
                byte val = rleData[rlePos++];
                for (int i = 0; i < count && outPos < outEnd; i++) {
                    output[outPos++] = val;
                }
            }
        }
    }

    /** Encode one row of data using PSD packed-bit RLE. */
    public static byte[] encodeRLE(byte[] data, int offset, int length) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(length * 2);
        int pos = offset;
        int end = offset + length;

        while (pos < end) {
            // Find literal or repeat run
            int rleStart = pos;
            if (pos + 1 < end && data[pos] == data[pos + 1]) {
                // Repeat run
                while (pos < end - 1 && data[pos] == data[pos + 1]
                        && (pos - rleStart) < 127) {
                    pos++;
                }
                int count = pos - rleStart + 1;
                out.write(1 - count); // header: negative
                out.write(data[rleStart]);
                pos++;
            } else {
                // Literal run
                while (pos < end - 1 && data[pos] != data[pos + 1]
                        && (pos - rleStart) < 127) {
                    pos++;
                }
                int count = pos - rleStart + 1;
                out.write(count - 1); // header: non-negative
                out.write(data, rleStart, count);
                pos++;
            }
        }
        return out.toByteArray();
    }

    // ── ZIP prediction (delta-decode) ──────────────────

    private static void deltaDecodePrediction(byte[] data, int width, int height, int bytesPerChan) {
        int scanlineBytes = width * bytesPerChan;
        for (int y = 1; y < height; y++) {
            int rowOff = y * scanlineBytes;
            for (int x = 0; x < scanlineBytes; x++) {
                data[rowOff + x] += data[rowOff + x - scanlineBytes];
            }
        }
    }

    // ── IO helpers ─────────────────────────────────────

    private static short readShort(InputStream in) throws IOException {
        return (short) ((in.read() << 8) | in.read());
    }

    private static int readInt(InputStream in) throws IOException {
        return (in.read() << 24) | (in.read() << 16)
             | (in.read() << 8) | in.read();
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) throw new IOException("Unexpected EOF");
            off += n;
        }
    }

    private static byte[] decompressZlib(byte[] data) throws IOException {
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 3);
        byte[] buf = new byte[8192];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n == 0) break;
                out.write(buf, 0, n);
            }
        } catch (DataFormatException e) {
            throw new IOException("Zlib decompression failed", e);
        } finally {
            inflater.end();
        }
        return out.toByteArray();
    }
}
