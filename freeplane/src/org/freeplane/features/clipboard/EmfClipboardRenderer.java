package org.freeplane.features.clipboard;

import java.awt.image.BufferedImage;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Memory;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * Reads an EMF (CF_ENHMETAFILE) directly from the Windows clipboard via JNA and
 * rasterizes it onto a WHITE background, bypassing the JVM's imageFlavor path
 * (CF_DIB) that fills the background with black and loses the structure.
 *
 * The white background is drawn by initializing the 32-bit DIB to 0xFF before
 * PlayEnhMetaFile, so ChemDraw's vector structure (black bonds/text) is
 * rendered on a clean white canvas - no per-pixel color-threshold hack.
 */
public class EmfClipboardRenderer {

    /**
     * Target rasterization DPI for the vector EMF. ChemDraw emits a
     * resolution-independent vector EMF, so we rasterize at print quality
     * (600 DPI) to keep bonds/text crisp. The 4096px cap (see readWindowsEmf)
     * bounds memory use for very large selections.
     */
    private static final int TARGET_DPI = 600;

    /** Minimum rendered extent (px). Vector EMF scales losslessly, so a larger
     *  floor keeps bonds/text crisp on high-DPI displays and in print. */
    private static final int MIN_EXTENT = 800;

    /** Maximum rendered extent (px) to bound memory for very large selections. */
    private static final int MAX_EXTENT = 4096;

    /**
     * Display size (width) of the last successfully rendered EMF, expressed in
     * the EMF's own device units ({@code rclBounds}). This is the 1:1 on-screen
     * size the image would occupy if the map displayed it pixel-for-pixel (the
     * pre-existing behaviour). Because we now rasterize at a much higher pixel
     * density, the caller uses this to scale the node back down so the image
     * keeps its original on-screen size while remaining high-resolution.
     * {@code -1} means no EMF was rendered (e.g. a plain bitmap paste).
     */
    private static volatile int lastDisplayWidth = -1;
    private static volatile int lastDisplayHeight = -1;

    public static int getLastDisplayWidth() { return lastDisplayWidth; }
    public static int getLastDisplayHeight() { return lastDisplayHeight; }

    public static BufferedImage tryReadWindowsEmf() {
        lastDisplayWidth = -1;
        lastDisplayHeight = -1;
        try {
            return readWindowsEmf();
        } catch (Throwable t) {
            return null;
        }
    }

    private static BufferedImage readWindowsEmf() {
        final User32 user32 = User32Holder.INSTANCE;
        if (user32 == null) return null;
        if (!user32.OpenClipboard(null)) return null;
        try {
            final int CF_ENHMETAFILE = 14;
            if (!user32.IsClipboardFormatAvailable(CF_ENHMETAFILE)) return null;
            final Pointer hemf = user32.GetClipboardData(CF_ENHMETAFILE);
            if (hemf == null) return null;

            final Gdi32 gdi32 = Gdi32Holder.INSTANCE;
            if (gdi32 == null) return null;

            final Memory hdr = new Memory(128);
            final int hdrLen = gdi32.GetEnhMetaFileHeader(hemf, 128, hdr);
            if (hdrLen < 40) return null;
            final int bLeft = hdr.getInt(8);
            final int bTop = hdr.getInt(12);
            final int bRight = hdr.getInt(16);
            final int bBottom = hdr.getInt(20);
            final int boundsW = bRight - bLeft;
            final int boundsH = bBottom - bTop;
            if (boundsW <= 0 || boundsH <= 0) return null;

            // Remember the EMF's intrinsic on-screen size (device units) so the
            // paste step can keep the node's display size unchanged while we
            // rasterize at a higher pixel density below.
            lastDisplayWidth = boundsW;
            lastDisplayHeight = boundsH;

            // Physical extent recorded in 0.01 mm units (rclFrame).
            final int fLeft = hdr.getInt(24);
            final int fTop = hdr.getInt(28);
            final int fRight = hdr.getInt(32);
            final int fBottom = hdr.getInt(36);
            final int frameW = fRight - fLeft;
            final int frameH = fBottom - fTop;

            int w, h;
            if (frameW > 0 && frameH > 0 && frameW < 1000000 && frameH < 1000000) {
                // Vector EMF: rasterize at TARGET_DPI using the recorded physical size.
                final double mmW = frameW / 100.0;
                final double mmH = frameH / 100.0;
                w = (int) Math.round(mmW * TARGET_DPI / 25.4);
                h = (int) Math.round(mmH * TARGET_DPI / 25.4);
            } else {
                // Fallback: 3x the reference device bounds.
                w = boundsW * 3;
                h = boundsH * 3;
            }

            // Keep aspect ratio while enforcing minimum and maximum extent.
            if (w < MIN_EXTENT || h < MIN_EXTENT) {
                final double s = Math.max((double) MIN_EXTENT / w, (double) MIN_EXTENT / h);
                w = (int) Math.round(w * s);
                h = (int) Math.round(h * s);
            }
            if (w > MAX_EXTENT || h > MAX_EXTENT) {
                final double s = Math.min((double) MAX_EXTENT / w, (double) MAX_EXTENT / h);
                w = (int) Math.round(w * s);
                h = (int) Math.round(h * s);
            }
            if (w <= 0 || h <= 0) return null;

            return render(gdi32, hemf, w, h);
        } finally {
            try { user32.CloseClipboard(); } catch (Throwable ignore) {}
        }
    }

    private static BufferedImage render(final Gdi32 gdi32, final Pointer hemf, final int w, final int h) {
        final Pointer hdc = gdi32.CreateCompatibleDC(null);
        if (hdc == null) return null;
        try {
            final Memory bmi = new Memory(40);
            bmi.clear();
            bmi.setInt(0, 40);
            bmi.setInt(4, w);
            bmi.setInt(8, -h);
            bmi.setShort(12, (short) 1);
            bmi.setShort(14, (short) 32);
            bmi.setInt(16, 0);

            final Pointer[] pBits = new Pointer[1];
            final Pointer hbmp = gdi32.CreateDIBSection(hdc, bmi, 0, pBits, null, 0);
            if (hbmp == null || pBits[0] == null) return null;
            try {
                pBits[0].setMemory(0, (long) w * h * 4, (byte) 0xFF); // white background
                final Pointer old = gdi32.SelectObject(hdc, hbmp);
                final Memory rect = new Memory(16);
                rect.setInt(0, 0); rect.setInt(4, 0); rect.setInt(8, w); rect.setInt(12, h);
                gdi32.PlayEnhMetaFile(hdc, hemf, rect);
                gdi32.SelectObject(hdc, old);

                final byte[] px = pBits[0].getByteArray(0, w * h * 4);
                final BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        final int off = (y * w + x) * 4;
                        final int b = px[off] & 0xFF;
                        final int g = px[off + 1] & 0xFF;
                        final int r = px[off + 2] & 0xFF;
                        img.setRGB(x, y, (r << 16) | (g << 8) | b);
                    }
                }
                return img;
            } finally {
                gdi32.DeleteObject(hbmp);
            }
        } finally {
            gdi32.DeleteDC(hdc);
        }
    }

    interface User32 extends StdCallLibrary {
        boolean OpenClipboard(Pointer hWndNewOwner);
        boolean CloseClipboard();
        Pointer GetClipboardData(int uFormat);
        boolean IsClipboardFormatAvailable(int format);
    }

    interface Gdi32 extends StdCallLibrary {
        int GetEnhMetaFileHeader(Pointer hemf, int cbBuffer, Pointer lpEnhMetaHeader);
        Pointer CreateCompatibleDC(Pointer hdc);
        boolean DeleteDC(Pointer hdc);
        Pointer CreateDIBSection(Pointer hdc, Pointer pBMI, int iUsage, Pointer[] ppvBits, Pointer hSection, int dwOffset);
        Pointer SelectObject(Pointer hdc, Pointer hObject);
        boolean PlayEnhMetaFile(Pointer hdc, Pointer hemf, Pointer lpRect);
        boolean DeleteObject(Pointer hObject);
    }

    private static final class User32Holder {
        static final User32 INSTANCE;
        static {
            User32 inst = null;
            try { inst = (User32) Native.loadLibrary("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS); } catch (Throwable ignore) {}
            INSTANCE = inst;
        }
    }

    private static final class Gdi32Holder {
        static final Gdi32 INSTANCE;
        static {
            Gdi32 inst = null;
            try { inst = (Gdi32) Native.loadLibrary("gdi32", Gdi32.class, W32APIOptions.DEFAULT_OPTIONS); } catch (Throwable ignore) {}
            INSTANCE = inst;
        }
    }
}