package com.lightps.io.psd;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single layer record from the PSD file.
 * Reference: Krita's psd_layer_record.h and .cpp
 *
 * Structure (per layer):
 *   top, left, bottom, right (4 x 4 bytes int32)
 *   nChannels (2 bytes)
 *   ChannelInfo[] (6 bytes each: channelID[2] + channelLen[4])
 *   blendModeSignature (4 bytes "8BIM")
 *   blendModeKey (4 bytes, e.g. "norm", "mul ")
 *   opacity (1 byte)
 *   clipping (1 byte)
 *   flags (1 byte)
 *   filler (1 byte)
 *   extraDataLength (4 bytes)
 *   └─ layer mask data
 *   └─ layer blending ranges
 *   └─ layer name (Pascal string, padded to 4)
 */
public class PSDLayerRecord {

    /** Channel info struct. */
    public static class ChannelInfo {
        public short channelId;   // 0=R,1=G,2=B,-1=alpha,-2=user mask
        public long channelDataLength; // 4 bytes for PSD, 8 for PSB
        public boolean hasData;

        ChannelInfo() {}

        boolean read(ByteBuffer bb, boolean psb) {
            channelId = bb.getShort();
            channelDataLength = psb ? bb.getLong() : (bb.getInt() & 0xFFFFFFFFL);
            hasData = channelDataLength > 0;
            return true;
        }
    }

    // ── Constants ──────────────────────────────────────

    /** Section types for layer group dividers. */
    public static final int SECTION_NORMAL = 0;
    public static final int SECTION_GROUP_OPEN = 1;
    public static final int SECTION_GROUP_CLOSED = 2;
    public static final int SECTION_DIVIDER = 3;

    // ── Fields ─────────────────────────────────────────

    public int top, left, bottom, right;  // pixel bounds
    public int nChannels;
    public List<ChannelInfo> channels;
    public String blendModeKey;           // 4-char key
    public int opacity;                   // 0..255
    public boolean clipping;
    public boolean visible;
    public boolean locked;
    public String layerName;

    // Section type for group layers
    public int sectionType; // 0=normal, 1=group open, 2=group closed, 3=divider

    // ── Reading ────────────────────────────────────────

    /**
     * Read a single layer record from the raw data buffer.
     * @param data     raw byte array containing this layer's record
     * @param psb      true if PSB (64-bit lengths), false if PSD
     */
    public boolean read(byte[] data, boolean psb) {
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        return read(bb, psb);
    }

    public boolean read(ByteBuffer bb, boolean psb) {
        top = bb.getInt();
        left = bb.getInt();
        bottom = bb.getInt();
        right = bb.getInt();

        nChannels = bb.getShort() & 0xFFFF;
        channels = new ArrayList<>(nChannels);
        for (int i = 0; i < nChannels; i++) {
            ChannelInfo ci = new ChannelInfo();
            ci.read(bb, psb);
            channels.add(ci);
        }

        // Blend mode
        String sig = readStr(bb, 4); // "8BIM"
        blendModeKey = readStr(bb, 4);

        opacity = bb.get() & 0xFF;
        clipping = bb.get() != 0;
        byte flags = bb.get();
        visible = (flags & 0x02) == 0;
        locked = (flags & 0x08) != 0;
        bb.get(); // filler

        // Extra data
        int extraLen = bb.getInt();
        int extraStart = bb.position();
        int extraEnd = extraStart + extraLen;

        // Layer mask data (skipped for now — handled at higher level)
        // Layer blending ranges (skipped)

        // Layer name: Pascal string (length byte + name) padded to 4 bytes
        int nameLen = bb.get() & 0xFF;
        if (nameLen > 0 && bb.position() + nameLen <= extraEnd) {
            byte[] nameBytes = new byte[nameLen];
            bb.get(nameBytes);
            layerName = new String(nameBytes, StandardCharsets.UTF_8);
        } else {
            layerName = "Layer";
        }

        // Skip remaining extra data
        bb.position(Math.max(bb.position(), extraEnd));

        return true;
    }

    // ── Writing ────────────────────────────────────────

    /**
     * Serialize this layer record to a ByteBuffer.
     */
    public byte[] write(boolean psb) {
        // Estimate size: 4*4 + 2 + nCh*chanSize + 4+4 + 1+1+1+1 + 4 + extra
        int chanSize = psb ? 10 : 6; // channel ID(2) + length(4 or 8)
        int extraEst = 4 + 4 + 4 + 4 + (layerName.length() + 1); // mask + ranges + name
        int extraPad = (4 - (extraEst % 4)) % 4;
        int total = 16 + 2 + nChannels * chanSize + 8 + 4 + 4 + extraEst + extraPad;

        ByteBuffer bb = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN);

        bb.putInt(top);
        bb.putInt(left);
        bb.putInt(bottom);
        bb.putInt(right);
        bb.putShort((short) nChannels);

        for (ChannelInfo ci : channels) {
            bb.putShort(ci.channelId);
            if (psb) {
                bb.putLong(ci.channelDataLength);
            } else {
                bb.putInt((int) ci.channelDataLength);
            }
        }

        bb.put("8BIM".getBytes(StandardCharsets.US_ASCII));
        bb.put(padKey(blendModeKey));
        bb.put((byte) opacity);
        bb.put((byte) (clipping ? 1 : 0));
        byte flags = 0;
        if (!visible) flags |= 0x02;
        if (locked) flags |= 0x08;
        bb.put(flags);
        bb.put((byte) 0); // filler

        // Extra data placeholder
        int extraLenOff = bb.position();
        bb.putInt(0); // placeholder

        // Layer mask data
        bb.putInt(0); // mask size = 0

        // Blending ranges
        bb.putInt(0);

        // Layer name (Pascal string)
        byte[] nameBytes = layerName.getBytes(StandardCharsets.UTF_8);
        int nameMax = Math.min(255, nameBytes.length);
        bb.put((byte) nameMax);
        bb.put(nameBytes, 0, nameMax);
        int namePad = (4 - ((nameMax + 1) % 4)) % 4;
        for (int i = 0; i < namePad; i++) bb.put((byte) 0);

        // Write extra length
        int extraLen = bb.position() - (extraLenOff + 4);
        bb.putInt(extraLenOff, extraLen);

        return bb.array();
    }

    // ── Getters ────────────────────────────────────────

    public int width() {
        return Math.max(0, right - left);
    }

    public int height() {
        return Math.max(0, bottom - top);
    }

    // ── Helpers ────────────────────────────────────────

    private static String readStr(ByteBuffer bb, int len) {
        byte[] b = new byte[len];
        bb.get(b);
        return new String(b, StandardCharsets.US_ASCII);
    }

    /** Ensure blend mode key is exactly 4 chars with trailing spaces. */
    private static byte[] padKey(String key) {
        byte[] b = new byte[4];
        byte[] src = key.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < 4; i++) {
            b[i] = (i < src.length) ? src[i] : (byte) ' ';
        }
        return b;
    }
}
