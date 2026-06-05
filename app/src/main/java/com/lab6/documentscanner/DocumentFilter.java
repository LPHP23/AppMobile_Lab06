package com.lab6.documentscanner;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DocumentFilter {
    private static final int MAX_SIDE = 1400;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public void removeShadow(Bitmap source, FilterCallback callback) {
        runFilter(source, callback, this::removeShadowSync);
    }

    public void toGray(Bitmap source, FilterCallback callback) {
        runFilter(source, callback, this::graySync);
    }

    public void toBlackWhite(Bitmap source, FilterCallback callback) {
        runFilter(source, callback, this::blackWhiteSync);
    }

    private void runFilter(Bitmap source, FilterCallback callback, BitmapTask task) {
        if (source == null) {
            return;
        }
        executor.execute(() -> {
            Bitmap prepared = downScaleIfNeeded(source);
            Bitmap result = task.apply(prepared);
            handler.post(() -> callback.onComplete(result));
        });
    }

    private Bitmap removeShadowSync(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);

        float[] h = new float[pixels.length];
        float[] s = new float[pixels.length];
        float[] v = new float[pixels.length];
        float[] hsv = new float[3];

        for (int i = 0; i < pixels.length; i++) {
            Color.colorToHSV(pixels[i], hsv);
            h[i] = hsv[0];
            s[i] = hsv[1];
            v[i] = hsv[2] * 255f;
        }

        float[] dilated = maxFilter(v, width, height, 3);
        float[] background = boxBlur(dilated, width, height, 10);
        float[] diff = new float[pixels.length];
        float min = 255f;
        float max = 0f;

        for (int i = 0; i < pixels.length; i++) {
            diff[i] = 255f - Math.abs(v[i] - background[i]);
            min = Math.min(min, diff[i]);
            max = Math.max(max, diff[i]);
        }

        int[] output = new int[pixels.length];
        float range = Math.max(1f, max - min);
        for (int i = 0; i < output.length; i++) {
            hsv[0] = h[i];
            hsv[1] = Math.min(s[i] * 0.85f, 1f);
            hsv[2] = clamp((diff[i] - min) * 255f / range) / 255f;
            output[i] = Color.HSVToColor(Color.alpha(pixels[i]), hsv);
        }

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(output, 0, width, 0, 0, width, height);
        return result;
    }

    private Bitmap graySync(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int i = 0; i < pixels.length; i++) {
            int gray = luminance(pixels[i]);
            pixels[i] = Color.argb(Color.alpha(pixels[i]), gray, gray, gray);
        }

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
    }

    private Bitmap blackWhiteSync(Bitmap source) {
        Bitmap noShadow = removeShadowSync(source);
        int width = noShadow.getWidth();
        int height = noShadow.getHeight();
        int[] pixels = new int[width * height];
        int[] gray = new int[pixels.length];
        noShadow.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int i = 0; i < pixels.length; i++) {
            gray[i] = luminance(pixels[i]);
        }

        int[] output = new int[pixels.length];
        int radius = 12;
        for (int y = 0; y < height; y++) {
            int top = Math.max(0, y - radius);
            int bottom = Math.min(height - 1, y + radius);
            for (int x = 0; x < width; x++) {
                int left = Math.max(0, x - radius);
                int right = Math.min(width - 1, x + radius);
                int sum = 0;
                int count = 0;
                for (int yy = top; yy <= bottom; yy++) {
                    int row = yy * width;
                    for (int xx = left; xx <= right; xx++) {
                        sum += gray[row + xx];
                        count++;
                    }
                }
                int threshold = (sum / count) - 9;
                int value = gray[y * width + x] < threshold ? 0 : 255;
                output[y * width + x] = Color.argb(255, value, value, value);
            }
        }

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(output, 0, width, 0, 0, width, height);
        return result;
    }

    private float[] maxFilter(float[] src, int width, int height, int radius) {
        float[] out = new float[src.length];
        for (int y = 0; y < height; y++) {
            int top = Math.max(0, y - radius);
            int bottom = Math.min(height - 1, y + radius);
            for (int x = 0; x < width; x++) {
                int left = Math.max(0, x - radius);
                int right = Math.min(width - 1, x + radius);
                float max = 0f;
                for (int yy = top; yy <= bottom; yy++) {
                    int row = yy * width;
                    for (int xx = left; xx <= right; xx++) {
                        max = Math.max(max, src[row + xx]);
                    }
                }
                out[y * width + x] = max;
            }
        }
        return out;
    }

    private float[] boxBlur(float[] src, int width, int height, int radius) {
        float[] horizontal = new float[src.length];
        float[] out = new float[src.length];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int left = Math.max(0, x - radius);
                int right = Math.min(width - 1, x + radius);
                float sum = 0f;
                for (int xx = left; xx <= right; xx++) {
                    sum += src[y * width + xx];
                }
                horizontal[y * width + x] = sum / (right - left + 1);
            }
        }

        for (int y = 0; y < height; y++) {
            int top = Math.max(0, y - radius);
            int bottom = Math.min(height - 1, y + radius);
            for (int x = 0; x < width; x++) {
                float sum = 0f;
                for (int yy = top; yy <= bottom; yy++) {
                    sum += horizontal[yy * width + x];
                }
                out[y * width + x] = sum / (bottom - top + 1);
            }
        }

        return out;
    }

    private Bitmap downScaleIfNeeded(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int max = Math.max(width, height);
        if (max <= MAX_SIDE) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, false);
        }
        float ratio = MAX_SIDE / (float) max;
        int newWidth = Math.round(width * ratio);
        int newHeight = Math.round(height * ratio);
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }

    private int luminance(int color) {
        return clamp((int) (Color.red(color) * 0.299f
                + Color.green(color) * 0.587f
                + Color.blue(color) * 0.114f));
    }

    private int clamp(float value) {
        return (int) Math.max(0, Math.min(255, value));
    }

    private interface BitmapTask {
        Bitmap apply(Bitmap bitmap);
    }
}
