package com.lightps.engine.color;

/**
 * Colour space definition with primaries, whitepoint and gamma.
 * Reference: Krita's KoColorSpace hierarchy (simplified).
 *
 * Supports sRGB, AdobeRGB, Display P3, and linear variants.
 */
public class ColorSpace {

    public static final ColorSpace SRGB = new ColorSpace(
            "sRGB",
            new double[]{0.640, 0.330},  // R
            new double[]{0.300, 0.600},  // G
            new double[]{0.150, 0.060},  // B
            new double[]{0.3127, 0.3290}, // D65
            2.4, true,  // gamma + piecewise
            new float[]{0.0f, 0.0031308f, 0.04045f, 12.92f, 1.0f / 2.4f, 1.055f, -0.055f}
    );

    public static final ColorSpace ADOBE_RGB = new ColorSpace(
            "Adobe RGB",
            new double[]{0.6400, 0.3300},
            new double[]{0.2100, 0.7100},
            new double[]{0.1500, 0.0600},
            new double[]{0.3127, 0.3290}, // D65
            2.2, false, null
    );

    public static final ColorSpace DISPLAY_P3 = new ColorSpace(
            "Display P3",
            new double[]{0.680, 0.320},
            new double[]{0.265, 0.690},
            new double[]{0.150, 0.060},
            new double[]{0.3127, 0.3290}, // D65
            2.4, true, SRGB.gammaParams
    );

    public static final ColorSpace LINEAR_SRGB = new ColorSpace(
            "Linear sRGB",
            SRGB.rx, SRGB.ry, SRGB.gx, SRGB.gy, SRGB.bx, SRGB.by,
            SRGB.wx, SRGB.wy,
            1.0, false, null
    );

    // ── Fields ─────────────────────────────────────────

    public final String name;
    public final double rx, ry;  // R chromaticity
    public final double gx, gy;  // G chromaticity
    public final double bx, by;  // B chromaticity
    public final double wx, wy;  // White point
    public final double gamma;
    public final boolean piecewiseGamma;
    public final float[] gammaParams; // {breakPoint, linearSlope, power, scale, offset}

    // Precomputed XYZ transform matrices
    private double[] toXYZMatrix;  // 3x3 row-major
    private double[] fromXYZMatrix;

    // ── Constructor ────────────────────────────────────

    public ColorSpace(String name,
                      double rx, double ry, double gx, double gy,
                      double bx, double by,
                      double wx, double wy,
                      double gamma, boolean piecewiseGamma, float[] gammaParams) {
        this.name = name;
        this.rx = rx; this.ry = ry;
        this.gx = gx; this.gy = gy;
        this.bx = bx; this.by = by;
        this.wx = wx; this.wy = wy;
        this.gamma = gamma;
        this.piecewiseGamma = piecewiseGamma;
        this.gammaParams = gammaParams;
        computeMatrices();
    }

    // ── Gamma encoding/decoding ────────────────────────

    /** Linear → gamma-encoded (for display). */
    public double toGamma(double linear) {
        if (piecewiseGamma && gammaParams != null) {
            // sRGB-style piecewise
            float br = gammaParams[0], sr = gammaParams[1];
            if (linear <= br) {
                return linear * gammaParams[3];  // linear * slope
            } else {
                return Math.pow(linear, gammaParams[4]) * gammaParams[5] + gammaParams[6];
            }
        }
        return Math.pow(linear, 1.0 / gamma);
    }

    /** Gamma-encoded → linear (for blending computation). */
    public double toLinear(double encoded) {
        if (piecewiseGamma && gammaParams != null) {
            float br = gammaParams[0], sr = gammaParams[1];
            if (encoded <= sr) {
                return encoded / gammaParams[3];
            } else {
                return Math.pow((encoded - gammaParams[6]) / gammaParams[5], gamma);
            }
        }
        return Math.pow(encoded, gamma);
    }

    // ── Colour space conversion ────────────────────────

    /**
     * Convert an RGB pixel from this space to another colour space.
     * Works in linear space.
     */
    public int convert(int argb, ColorSpace target) {
        if (this == target) return argb;
        return xyzToArgb(target.argbToXyz(argb));
    }

    /** ARGB → XYZ (linearised). */
    public double[] argbToXyz(int argb) {
        double r = toLinear(((argb >> 16) & 0xFF) / 255.0);
        double g = toLinear(((argb >> 8) & 0xFF) / 255.0);
        double b = toLinear((argb & 0xFF) / 255.0);

        double x = toXYZMatrix[0] * r + toXYZMatrix[1] * g + toXYZMatrix[2] * b;
        double y = toXYZMatrix[3] * r + toXYZMatrix[4] * g + toXYZMatrix[5] * b;
        double z = toXYZMatrix[6] * r + toXYZMatrix[7] * g + toXYZMatrix[8] * b;
        return new double[]{x, y, z};
    }

    /** XYZ → ARGB (with gamma encoding). */
    public int xyzToArgb(double[] xyz) {
        double x = xyz[0], y = xyz[1], z = xyz[2];
        double r = fromXYZMatrix[0] * x + fromXYZMatrix[1] * y + fromXYZMatrix[2] * z;
        double g = fromXYZMatrix[3] * x + fromXYZMatrix[4] * y + fromXYZMatrix[5] * z;
        double b = fromXYZMatrix[6] * x + fromXYZMatrix[7] * y + fromXYZMatrix[8] * z;

        int ri = clamp((int) (toGamma(Math.max(0, Math.min(1, r))) * 255 + 0.5));
        int gi = clamp((int) (toGamma(Math.max(0, Math.min(1, g))) * 255 + 0.5));
        int bi = clamp((int) (toGamma(Math.max(0, Math.min(1, b))) * 255 + 0.5));
        return (0xFF << 24) | (ri << 16) | (gi << 8) | bi;
    }

    // ── Matrix computation ─────────────────────────────

    private void computeMatrices() {
        // Primaries to XYZ matrix using the standard method
        double Xr = rx / ry;
        double Yr = 1.0;
        double Zr = (1.0 - rx - ry) / ry;

        double Xg = gx / gy;
        double Yg = 1.0;
        double Zg = (1.0 - gx - gy) / gy;

        double Xb = bx / by;
        double Yb = 1.0;
        double Zb = (1.0 - bx - by) / by;

        double Xw = wx / wy;
        double Yw = 1.0;
        double Zw = (1.0 - wx - wy) / wy;

        // solve for RGB→XYZ matrix using white point
        double[][] primaries = {{Xr, Xg, Xb}, {Yr, Yg, Yb}, {Zr, Zg, Zb}};
        double[] white = {Xw, Yw, Zw};
        double[] S = solve(primaries, white);

        toXYZMatrix = new double[]{
                S[0] * Xr, S[1] * Xg, S[2] * Xb,
                S[0] * Yr, S[1] * Yg, S[2] * Yb,
                S[0] * Zr, S[1] * Zg, S[2] * Zb
        };

        // Invert for XYZ→RGB
        fromXYZMatrix = invert3x3(toXYZMatrix);
    }

    /** Solve a 3x3 system via Cramer's rule. */
    private static double[] solve(double[][] A, double[] b) {
        double det = determinant3x3(A);
        double[][] Ax = {
                {b[0], A[0][1], A[0][2]},
                {b[1], A[1][1], A[1][2]},
                {b[2], A[2][1], A[2][2]}
        };
        double[][] Ay = {
                {A[0][0], b[0], A[0][2]},
                {A[1][0], b[1], A[1][2]},
                {A[2][0], b[2], A[2][2]}
        };
        double[][] Az = {
                {A[0][0], A[0][1], b[0]},
                {A[1][0], A[1][1], b[1]},
                {A[2][0], A[2][1], b[2]}
        };
        return new double[]{
                determinant3x3(Ax) / det,
                determinant3x3(Ay) / det,
                determinant3x3(Az) / det
        };
    }

    private static double determinant3x3(double[][] m) {
        return m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
             - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
             + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
    }

    private static double[] invert3x3(double[] m) {
        double det = m[0] * (m[4] * m[8] - m[5] * m[7])
                   - m[1] * (m[3] * m[8] - m[5] * m[6])
                   + m[2] * (m[3] * m[7] - m[4] * m[6]);
        double invDet = 1.0 / det;
        return new double[]{
                 (m[4] * m[8] - m[5] * m[7]) * invDet,
                -(m[1] * m[8] - m[2] * m[7]) * invDet,
                 (m[1] * m[5] - m[2] * m[4]) * invDet,
                -(m[3] * m[8] - m[5] * m[6]) * invDet,
                 (m[0] * m[8] - m[2] * m[6]) * invDet,
                -(m[0] * m[5] - m[2] * m[3]) * invDet,
                 (m[3] * m[7] - m[4] * m[6]) * invDet,
                -(m[0] * m[7] - m[1] * m[6]) * invDet,
                 (m[0] * m[4] - m[1] * m[3]) * invDet
        };
    }

    // ── Builder / lookup ───────────────────────────────

    public static ColorSpace fromName(String name) {
        switch (name.toLowerCase()) {
            case "srgb": case "s rgb": return SRGB;
            case "adobergb": case "adober rgb": case "adobergb (1998)": return ADOBE_RGB;
            case "displayp3": case "p3": case "display p3": return DISPLAY_P3;
            case "linear": case "linear s rgb": return LINEAR_SRGB;
            default: return SRGB;
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
