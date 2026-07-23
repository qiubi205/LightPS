package com.lightps.engine.blend;

import com.lightps.engine.layer.BlendMode;

/**
 * Per-pixel blending engine implementing all PSD-compatible blend modes.
 * Reference: Krita's KisBlendOps and the Adobe Photoshop math spec.
 *
 * Both dstPixels and srcPixels arrays are ARGB_8888 int arrays.
 * Each pixel is packed as: 0xAARRGGBB (alpha in bits 24-31).
 */
public class BlendEngine {

    /**
     * Blend src onto dst in-place using the given mode and opacity (0..255).
     * dst array is modified with the composited result.
     */
    public static void blend(int[] dst, int[] src, BlendMode mode, int opacity) {
        for (int i = 0; i < dst.length; i++) {
            dst[i] = blendPixel(dst[i], src[i], mode, opacity);
        }
    }

    /** Single pixel blend. */
    public static int blendPixel(int dstArgb, int srcArgb, BlendMode mode, int opacity) {
        int sa = (srcArgb >>> 24) & 0xFF;
        int sr = (srcArgb >>> 16) & 0xFF;
        int sg = (srcArgb >>> 8) & 0xFF;
        int sb = srcArgb & 0xFF;

        int da = (dstArgb >>> 24) & 0xFF;
        int dr = (dstArgb >>> 16) & 0xFF;
        int dg = (dstArgb >>> 8) & 0xFF;
        int db = dstArgb & 0xFF;

        // Apply layer opacity to source alpha
        sa = sa * opacity / 255;
        if (sa == 0) return dstArgb;

        // Compute blended color (full opacity result)
        int br, bg, bb;
        switch (mode) {
            case NORMAL:     br = sr; bg = sg; bb = sb; break;
            case DISSOLVE:
                if (opacity < 255 && Math.random() * 255 > sa) {
                    br = dr; bg = dg; bb = db;
                } else {
                    br = sr; bg = sg; bb = sb;
                }
                break;
            case MULTIPLY:
                br = sr * dr / 255; bg = sg * dg / 255; bb = sb * db / 255; break;
            case SCREEN:
                br = 255 - (255 - sr) * (255 - dr) / 255;
                bg = 255 - (255 - sg) * (255 - dg) / 255;
                bb = 255 - (255 - sb) * (255 - db) / 255;
                break;
            case OVERLAY:
                br = overlay(sr, dr); bg = overlay(sg, dg); bb = overlay(sb, db); break;
            case DARKEN:
                br = Math.min(sr, dr); bg = Math.min(sg, dg); bb = Math.min(sb, db); break;
            case LIGHTEN:
                br = Math.max(sr, dr); bg = Math.max(sg, dg); bb = Math.max(sb, db); break;
            case COLOR_DODGE:
                br = colorDodge(sr, dr); bg = colorDodge(sg, dg); bb = colorDodge(sb, db); break;
            case COLOR_BURN:
                br = colorBurn(sr, dr); bg = colorBurn(sg, dg); bb = colorBurn(sb, db); break;
            case HARD_LIGHT:
                br = hardLight(sr, dr); bg = hardLight(sg, dg); bb = hardLight(sb, db); break;
            case SOFT_LIGHT:
                br = softLight(sr, dr); bg = softLight(sg, dg); bb = softLight(sb, db); break;
            case DIFFERENCE:
                br = Math.abs(dr - sr); bg = Math.abs(dg - sg); bb = Math.abs(db - sb); break;
            case EXCLUSION:
                br = sr + dr - 2 * sr * dr / 255;
                bg = sg + dg - 2 * sg * dg / 255;
                bb = sb + db - 2 * sb * db / 255;
                break;
            case LINEAR_BURN:
                br = Math.max(0, sr + dr - 255);
                bg = Math.max(0, sg + dg - 255);
                bb = Math.max(0, sb + db - 255);
                break;
            case LINEAR_DODGE:
                br = Math.min(255, sr + dr);
                bg = Math.min(255, sg + dg);
                bb = Math.min(255, sb + db);
                break;
            case VIVID_LIGHT:
                br = vividLight(sr, dr); bg = vividLight(sg, dg); bb = vividLight(sb, db); break;
            case LINEAR_LIGHT:
                br = linearLight(sr, dr); bg = linearLight(sg, dg); bb = linearLight(sb, db); break;
            case PIN_LIGHT:
                br = pinLight(sr, dr); bg = pinLight(sg, dg); bb = pinLight(sb, db); break;
            case HARD_MIX:
                int hmR = hardLight(sr, dr) >= 128 ? 255 : 0;
                int hmG = hardLight(sg, dg) >= 128 ? 255 : 0;
                int hmB = hardLight(sb, db) >= 128 ? 255 : 0;
                br = hmR; bg = hmG; bb = hmB;
                break;
            case SUBTRACT:
                br = Math.max(0, dr - sr); bg = Math.max(0, dg - sg); bb = Math.max(0, db - sb); break;
            case DIVIDE:
                br = Math.min(255, dr * 256 / Math.max(1, sr + 1));
                bg = Math.min(255, dg * 256 / Math.max(1, sg + 1));
                bb = Math.min(255, db * 256 / Math.max(1, sb + 1));
                break;
            case HUE:
            case SATURATION:
            case COLOR:
            case LUMINOSITY:
                // HSL-based modes — use the generic HSL function
                int[] hslResult = hslBlend(dr, dg, db, sr, sg, sb, mode);
                br = hslResult[0]; bg = hslResult[1]; bb = hslResult[2]; break;
            default:
                br = sr; bg = sg; bb = sb;
        }

        // Standard alpha compositing: result = src * sa + dst * (1 - sa)
        int outA = sa + da * (255 - sa) / 255;
        int outR = (br * sa + dr * da * (255 - sa) / 255) / Math.max(1, outA);
        int outG = (bg * sa + dg * da * (255 - sa) / 255) / Math.max(1, outA);
        int outB = (bb * sa + db * da * (255 - sa) / 255) / Math.max(1, outA);

        return (clamp(outA) << 24) | (clamp(outR) << 16) | (clamp(outG) << 8) | clamp(outB);
    }

    // ── Individual mode formulas ───────────────────────

    private static int overlay(int s, int d) {
        return d < 128 ? (2 * s * d / 255) : (255 - 2 * (255 - s) * (255 - d) / 255);
    }

    private static int colorDodge(int s, int d) {
        if (s == 255) return 255;
        return Math.min(255, d * 255 / (255 - s));
    }

    private static int colorBurn(int s, int d) {
        if (s == 0) return 0;
        return Math.max(0, 255 - (255 - d) * 255 / s);
    }

    private static int hardLight(int s, int d) {
        return s < 128 ? (2 * s * d / 255) : (255 - 2 * (255 - s) * (255 - d) / 255);
    }

    private static int softLight(int s, int d) {
        if (s < 128) {
            return d - (255 - 2 * s) * d * (255 - d) / (255 * 255);
        } else {
            int d4 = d * d / 255;
            return d + (2 * s - 255) * (d4 - d) / 255;
        }
    }

    private static int vividLight(int s, int d) {
        return s < 128 ? colorBurn(s * 2, d) : colorDodge((s - 128) * 2, d);
    }

    private static int linearLight(int s, int d) {
        return s < 128 ? Math.max(0, d + 2 * s - 255) : Math.min(255, d + 2 * (s - 128));
    }

    private static int pinLight(int s, int d) {
        return s < 128 ? Math.min(d, 2 * s) : Math.max(d, 2 * (s - 128));
    }

    // ── HSL blend modes (Hue, Saturation, Color, Luminosity) ──

    private static int[] hslBlend(int dr, int dg, int db, int sr, int sg, int sb, BlendMode mode) {
        float[] dhsl = rgbToHsl(dr, dg, db);
        float[] shsl = rgbToHsl(sr, sg, sb);

        float h, s, l;
        switch (mode) {
            case HUE:
                h = shsl[0]; s = dhsl[1]; l = dhsl[2]; break;
            case SATURATION:
                h = dhsl[0]; s = shsl[1]; l = dhsl[2]; break;
            case COLOR:
                h = shsl[0]; s = shsl[1]; l = dhsl[2]; break;
            case LUMINOSITY:
                h = dhsl[0]; s = dhsl[1]; l = shsl[2]; break;
            default:
                return new int[]{sr, sg, sb};
        }
        return hslToRgb(h, s, l);
    }

    /** RGB 0..255 → HSL (h 0..360, s 0..1, l 0..1). */
    private static float[] rgbToHsl(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float h = 0, s, l = (max + min) / 2f;

        if (max == min) {
            s = 0;
        } else {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == rf) {
                h = (gf - bf) / d + (gf < bf ? 6f : 0f);
            } else if (max == gf) {
                h = (bf - rf) / d + 2f;
            } else {
                h = (rf - gf) / d + 4f;
            }
            h *= 60f;
        }
        return new float[]{h, s, l};
    }

    /** HSL → RGB 0..255. */
    private static int[] hslToRgb(float h, float s, float l) {
        if (s == 0) {
            int v = (int) (l * 255f + 0.5f);
            return new int[]{v, v, v};
        }
        float h2 = h / 360f;
        float q = l < 0.5f ? l * (1f + s) : l + s - l * s;
        float p = 2f * l - q;
        float r = hue2rgb(p, q, h2 + 1f / 3f);
        float g = hue2rgb(p, q, h2);
        float b = hue2rgb(p, q, h2 - 1f / 3f);
        return new int[]{
                clamp((int) (r * 255f + 0.5f)),
                clamp((int) (g * 255f + 0.5f)),
                clamp((int) (b * 255f + 0.5f))
        };
    }

    private static float hue2rgb(float p, float q, float t) {
        if (t < 0) t += 1f;
        if (t > 1) t -= 1f;
        if (t < 1f / 6f) return p + (q - p) * 6f * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f;
        return p;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
