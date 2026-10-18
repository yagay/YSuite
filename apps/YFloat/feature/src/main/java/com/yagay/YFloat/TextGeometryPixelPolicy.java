package com.yagay.YFloat;

/**
 * Device-independent, bounded colour-mask geometry calibration.
 *
 * Only tightens a completed OCR line where a near-uniform background is
 * detected. A failure is intentionally null: never substitute inferred
 * shapes for the OCR selection, or touch the recognized text itself.
 *
 * Coordinates and input ARGB pixels are local to the small screenshot ROI.
 * No Android classes, OpenCV native state or model downloads are required.
 */
final class TextGeometryPixelPolicy {
    private TextGeometryPixelPolicy() { }

    static int[] refine(int[] argb, int width, int height, int[] seed) {
        if (argb == null || seed == null || seed.length < 4
                || width < 8 || height < 8
                || width > 720 || height > 360 || argb.length < width * height) {
            return null;
        }
        int sl = seed[0], st = seed[1], sr = seed[2], sb = seed[3];
        if (sl < 0 || st < 0 || sr > width || sb > height || sr <= sl || sb <= st)
            return null;

        // Histogram of perimeter pixels: a uniform icon typically has a
        // strong red/dark bucket while anti-aliased text is a small minority.
        // Quantization tolerates JPEG compression and wallpaper dithering.
        int[] histogram = new int[1 << 15];
        int perimeter = 0;
        final int thickness = Math.min(3, Math.max(1, Math.min(width, height) / 14));
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x >= thickness && x < width - thickness
                        && y >= thickness && y < height - thickness) continue;
                int color = argb[y * width + x];
                histogram[bucket(color)]++;
                perimeter++;
            }
        }
        int bestBucket = 0, bestCount = 0;
        for (int i = 0; i < histogram.length; i++) {
            if (histogram[i] > bestCount) { bestCount = histogram[i]; bestBucket = i; }
        }
        if (bestCount < Math.max(16, (int) (perimeter * 0.35f))) return null;

        long rr = 0, gg = 0, bb = 0;
        int bgCount = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x >= thickness && x < width - thickness
                        && y >= thickness && y < height - thickness) continue;
                int c = argb[y * width + x];
                if (bucket(c) != bestBucket) continue;
                rr += (c >>> 16) & 255;
                gg += (c >>> 8) & 255;
                bb += c & 255;
                bgCount++;
            }
        }
        if (bgCount < 1) return null;
        int br = (int) (rr / bgCount), bg = (int) (gg / bgCount);
        int bbv = (int) (bb / bgCount);
        int baseLuma = (br * 299 + bg * 587 + bbv * 114) / 1000;

        int[] rowCount = new int[height], colCount = new int[width];
        int foreground = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int c = argb[y * width + x];
                int r = (c >>> 16) & 255, g = (c >>> 8) & 255, b = c & 255;
                int dr = r - br, dg = g - bg, db = b - bbv;
                int luma = (r * 299 + g * 587 + b * 114) / 1000;
                // Both colour distance and luminance are required. A noisy
                // or colourful wallpaper cannot be treated as white glyphs.
                if (dr * dr + dg * dg + db * db < 65 * 65
                        || Math.abs(luma - baseLuma) < 40) continue;
                // Broad icon stripes and backgrounds tend to fill a whole
                // row. Those rows are rejected during projection below.
                rowCount[y]++;
                colCount[x]++;
                foreground++;
            }
        }
        float area = (float) width * height;
        if (foreground < Math.max(10, (int) (area * 0.018f))
                || foreground > area * 0.48f) return null;

        int left = width, top = height, right = 0, bottom = 0;
        int minCol = Math.max(2, (int) (height * 0.042f));
        int minRow = Math.max(2, (int) (width * 0.042f));
        for (int x = 0; x < width; x++) {
            if (colCount[x] < minCol || colCount[x] > height * 0.87f) continue;
            left = Math.min(left, x);
            right = Math.max(right, x + 1);
        }
        for (int y = 0; y < height; y++) {
            if (rowCount[y] < minRow || rowCount[y] > width * 0.85f) continue;
            top = Math.min(top, y);
            bottom = Math.max(bottom, y + 1);
        }
        if (right <= left || bottom <= top) return null;

        int sw = sr - sl, sh = sb - st, rw = right - left, rh = bottom - top;
        // The refined result must substantially overlap OCR's anchor but
        // can correct clipped character bounds and empty detector padding.
        int overlapW = Math.max(0, Math.min(sr, right) - Math.max(sl, left));
        int overlapH = Math.max(0, Math.min(sb, bottom) - Math.max(st, top));
        if (overlapW < Math.min(sw, rw) * 0.55f
                || overlapH < Math.min(sh, rh) * 0.55f
                || rw < sw * 0.48f || rw > sw * 1.70f
                || rh < sh * 0.35f || rh > sh * 1.45f) return null;

        // Open borders usually mean a neighbouring icon, clipping, or an
        // incorrectly estimated background rather than accurate text edges.
        int edgeHits = (left <= 0 ? 1 : 0) + (top <= 0 ? 1 : 0)
                + (right >= width ? 1 : 0) + (bottom >= height ? 1 : 0);
        if (edgeHits >= 2) return null;
        return new int[]{Math.max(0, left - 1), Math.max(0, top - 1),
                Math.min(width, right + 1), Math.min(height, bottom + 1)};
    }

    private static int bucket(int c) {
        int r = (c >>> 19) & 31, g = (c >>> 11) & 31, b = (c >>> 3) & 31;
        return (r << 10) | (g << 5) | b;
    }
}
