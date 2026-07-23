package com.lightps.engine.filter;

/**
 * Simple Gaussian blur using two-pass separable kernel.
 */
public class GaussianBlurFilter extends Filter {

    private final float radius;

    public GaussianBlurFilter(float radius) {
        super("Gaussian Blur");
        this.radius = Math.max(0.5f, radius);
    }

    @Override
    public void apply(int[] pixels, int width, int height) {
        int[] tmp = new int[pixels.length];
        int kSize = (int) (radius * 3 + 1);
        if (kSize % 2 == 0) kSize++;
        float[] kernel = buildKernel(kSize);

        // Horizontal pass
        for (int y = 0; y < height; y++) {
            int rowStart = y * width;
            for (int x = 0; x < width; x++) {
                float sumR = 0, sumG = 0, sumB = 0, sumA = 0, sumW = 0;
                for (int k = 0; k < kSize; k++) {
                    int sx = x + k - kSize / 2;
                    if (sx < 0 || sx >= width) continue;
                    int p = pixels[rowStart + sx];
                    float w = kernel[k];
                    sumR += ((p >> 16) & 0xFF) * w;
                    sumG += ((p >> 8) & 0xFF) * w;
                    sumB += (p & 0xFF) * w;
                    sumA += ((p >> 24) & 0xFF) * w;
                    sumW += w;
                }
                int a = clamp((int) (sumA / sumW));
                int r = clamp((int) (sumR / sumW));
                int g = clamp((int) (sumG / sumW));
                int b = clamp((int) (sumB / sumW));
                tmp[rowStart + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }

        // Vertical pass
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                float sumR = 0, sumG = 0, sumB = 0, sumA = 0, sumW = 0;
                for (int k = 0; k < kSize; k++) {
                    int sy = y + k - kSize / 2;
                    if (sy < 0 || sy >= height) continue;
                    int p = tmp[sy * width + x];
                    float w = kernel[k];
                    sumR += ((p >> 16) & 0xFF) * w;
                    sumG += ((p >> 8) & 0xFF) * w;
                    sumB += (p & 0xFF) * w;
                    sumA += ((p >> 24) & 0xFF) * w;
                    sumW += w;
                }
                int a = clamp((int) (sumA / sumW));
                int r = clamp((int) (sumR / sumW));
                int g = clamp((int) (sumG / sumW));
                int b = clamp((int) (sumB / sumW));
                pixels[y * width + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
    }

    private float[] buildKernel(int size) {
        float[] k = new float[size];
        float sigma = radius;
        float sum = 0;
        int mid = size / 2;
        for (int i = 0; i < size; i++) {
            int d = i - mid;
            k[i] = (float) Math.exp(-(d * d) / (2 * sigma * sigma));
            sum += k[i];
        }
        for (int i = 0; i < size; i++) k[i] /= sum;
        return k;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
