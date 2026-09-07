package org.freeplane.features.clipboard;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import junit.framework.Assert;

import org.freeplane.features.clipboard.mindmapmode.MClipboardController;
import org.junit.Test;

/**
 * Regression test for the "transparent clipboard image becomes black" bug.
 *
 * <p>Verifies that {@link MClipboardController#toARGB(BufferedImage)} and
 * {@link MClipboardController#readImageFromClipboard(Transferable)} preserve
 * the alpha channel: transparent areas stay transparent, black lines/fills of
 * chemical structures are NOT erased, and plain RGB images keep working.</p>
 */
public class ClipboardImageTransparencyTest {

	// ---- helpers ----

	private static BufferedImage argbImage(final int w, final int h) {
		return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
	}

	private static BufferedImage rgbImage(final int w, final int h) {
		return new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
	}

	/** A black horizontal line on an otherwise fully transparent canvas. */
	private static BufferedImage blackLineOnTransparent() {
		final BufferedImage img = argbImage(20, 20);
		final Graphics2D g = img.createGraphics();
		g.setColor(Color.BLACK);
		g.drawLine(0, 10, 19, 10);
		g.dispose();
		return img;
	}

	/** A red square on an otherwise fully transparent canvas. */
	private static BufferedImage coloredShapeOnTransparent() {
		final BufferedImage img = argbImage(20, 20);
		final Graphics2D g = img.createGraphics();
		g.setColor(Color.RED);
		g.fillRect(5, 5, 10, 10);
		g.dispose();
		return img;
	}

	private static int pixelAlpha(final BufferedImage img, final int x, final int y) {
		return (img.getRGB(x, y) >>> 24) & 0xFF;
	}

	private static int pixelRGB(final BufferedImage img, final int x, final int y) {
		return img.getRGB(x, y) & 0xFFFFFF;
	}

	// ---- tests ----

	@Test
	public void testTransparentBackgroundPngKeepsAlpha() {
		final BufferedImage src = blackLineOnTransparent();
		final BufferedImage out = MClipboardController.toARGB(src);
		Assert.assertEquals(BufferedImage.TYPE_INT_ARGB, out.getType());
		// corners are transparent
		Assert.assertEquals(0, pixelAlpha(out, 0, 0));
		Assert.assertEquals(0, pixelAlpha(out, 19, 0));
		// the line pixel is opaque black
		Assert.assertEquals(255, pixelAlpha(out, 10, 10));
		Assert.assertEquals(0x000000, pixelRGB(out, 10, 10));
	}

	@Test
	public void testWhiteBackgroundStaysWhiteAndOpaque() {
		final BufferedImage src = rgbImage(10, 10);
		final Graphics2D g = src.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, 10, 10);
		g.dispose();
		final BufferedImage out = MClipboardController.toARGB(src);
		Assert.assertEquals(BufferedImage.TYPE_INT_ARGB, out.getType());
		Assert.assertEquals(0xFFFFFF, pixelRGB(out, 5, 5));
		Assert.assertEquals(255, pixelAlpha(out, 5, 5));
	}

	@Test
	public void testPlainRgbImageSurvives() {
		final BufferedImage src = rgbImage(10, 10);
		final Graphics2D g = src.createGraphics();
		g.setColor(new Color(12, 34, 56));
		g.fillRect(0, 0, 10, 10);
		g.dispose();
		final BufferedImage out = MClipboardController.toARGB(src);
		Assert.assertEquals(0x0C2238, pixelRGB(out, 5, 5));
		Assert.assertEquals(255, pixelAlpha(out, 5, 5));
	}

	@Test
	public void testBlackLineOnTransparentIsNotErased() {
		final BufferedImage src = blackLineOnTransparent();
		final BufferedImage out = MClipboardController.toARGB(src);
		// black line must be preserved (opaque black), not replaced by transparent
		Assert.assertEquals(255, pixelAlpha(out, 10, 10));
		Assert.assertEquals(0x000000, pixelRGB(out, 10, 10));
		// background is still transparent
		Assert.assertEquals(0, pixelAlpha(out, 2, 2));
	}

	@Test
	public void testColoredStructureOnTransparentKeepsAlphaAndColor() {
		final BufferedImage src = coloredShapeOnTransparent();
		final BufferedImage out = MClipboardController.toARGB(src);
		Assert.assertEquals(0xFF0000, pixelRGB(out, 10, 10));
		Assert.assertEquals(255, pixelAlpha(out, 10, 10));
		Assert.assertEquals(0, pixelAlpha(out, 0, 0));
	}

	@Test
	public void testReadImagePrefersPngWithAlpha() throws Exception {
		// A transferable offering BOTH an alpha-carrying PNG and a lossy
		// (black-background) image flavor, like ChemDraw does on Windows.
		final BufferedImage transparent = blackLineOnTransparent();
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ImageIO.write(transparent, "png", bos);
		final byte[] pngBytes = bos.toByteArray();

		final BufferedImage lossy = rgbImage(20, 20);
		final Graphics2D g = lossy.createGraphics();
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, 20, 20);
		g.setColor(Color.WHITE);
		g.drawLine(0, 10, 19, 10); // simulate a black background with a light line
		g.dispose();

		final FakeTransferable t = new FakeTransferable();
		t.put(new DataFlavor("image/png"), pngBytes);
		t.put(DataFlavor.imageFlavor, lossy);

		final BufferedImage out = MClipboardController.readImageFromClipboard(t);
		Assert.assertNotNull(out);
		// PNG flavor was used: corner transparent, line black opaque
		Assert.assertEquals(0, pixelAlpha(out, 0, 0));
		Assert.assertEquals(255, pixelAlpha(out, 10, 10));
		Assert.assertEquals(0x000000, pixelRGB(out, 10, 10));
	}

	@Test
	public void testReadImageFallsBackToImageFlavor() throws Exception {
		final BufferedImage lossy = rgbImage(10, 10);
		final Graphics2D g = lossy.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, 10, 10);
		g.dispose();
		final FakeTransferable t = new FakeTransferable();
		t.put(DataFlavor.imageFlavor, lossy);
		final BufferedImage out = MClipboardController.readImageFromClipboard(t);
		Assert.assertNotNull(out);
		Assert.assertEquals(0xFFFFFF, pixelRGB(out, 5, 5));
	}

	@Test
	public void testNullSafe() {
		Assert.assertNull(MClipboardController.toARGB(null));
		Assert.assertNull(MClipboardController.readImageFromClipboard(null));
	}

	// ---- fake transferable ----

	private static class FakeTransferable implements Transferable {
		private final Map<DataFlavor, Object> data = new LinkedHashMap<DataFlavor, Object>();

		void put(final DataFlavor flavor, final Object value) {
			data.put(flavor, value);
		}

		public DataFlavor[] getTransferDataFlavors() {
			return data.keySet().toArray(new DataFlavor[data.size()]);
		}

		public boolean isDataFlavorSupported(final DataFlavor flavor) {
			return data.containsKey(flavor);
		}

		public Object getTransferData(final DataFlavor flavor) throws UnsupportedFlavorException, IOException {
			if (!data.containsKey(flavor)) {
				throw new UnsupportedFlavorException(flavor);
			}
			return data.get(flavor);
		}
	}
}
