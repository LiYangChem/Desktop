package org.freeplane.core.ui.ribbon;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;

import org.pushingpixels.flamingo.api.common.icon.ResizableIcon;

/**
 * A {@link ResizableIcon} that scales its source image to <b>any</b> requested
 * size, up <i>and</i> down, with smooth interpolation.
 * <p>
 * It exists because Flamingo's {@code ImageWrapperIcon} only scales
 * <b>down</b>: its {@code renderImage()} computes
 * {@code scale = max(scaleX, scaleY)} and resizes the source image only when
 * {@code scale > 1.0f}. A 16 px source icon that is asked for 32 px keeps
 * rendering the original 16 px image, which {@code paintIcon()} then centers
 * inside the 32 px box ({@code dx = (32 - 16) / 2}). The result: a button that
 * is laid out for a 32 px icon (correct preferred size, correct
 * {@code getIconWidth()}) but paints a small 16 px image with empty space
 * around it - which is why the user configured ribbon icon size seemed to
 * have no effect.
 * <p>
 * This implementation draws the source image scaled to exactly the requested
 * dimension, so {@link #paintIcon} always fills the box that the button
 * layout reserved for the icon. Sources range from legacy 16 px PNGs to the
 * 48 px taskbar icons introduced 2026-09-05; downscaling is the common path
 * (48 -> 32 taskbar / 16 strip) and uses high-quality interpolation, and
 * upscaling (e.g. 16 px legacy sources at large configured sizes) uses
 * bilinear interpolation which is fine for the flat geometric icon style.
 * <p>
 * The class is deliberately state-per-instance: every call of
 * {@code RibbonActionContributorFactory#getActionIcon} creates its own icon,
 * so buttons can request different dimensions for the same source image
 * (taskbar 32 px, strip 16 px, ...).
 */
public class SmoothScalingResizableIcon implements ResizableIcon {

	/** the (usually small) source image; never modified */
	private final Image source;
	/** currently requested icon box; paint scales the source into it */
	private int width;
	private int height;

	public SmoothScalingResizableIcon(final Image source, final int initialWidth, final int initialHeight) {
		if (source == null) {
			throw new IllegalArgumentException("source image must not be null");
		}
		this.source = source;
		this.width = Math.max(1, initialWidth);
		this.height = Math.max(1, initialHeight);
		/* make sure the image is fully loaded before the first paint, so
		 * getWidth(null) / getHeight(null) are valid immediately */
		source.getWidth(null);
		source.getHeight(null);
	}

	@Override
	public void setDimension(final Dimension newDimension) {
		if (newDimension != null && newDimension.width > 0 && newDimension.height > 0) {
			this.width = newDimension.width;
			this.height = newDimension.height;
		}
	}

	@Override
	public int getIconWidth() {
		return width;
	}

	@Override
	public int getIconHeight() {
		return height;
	}

	@Override
	public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
		final Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g2.drawImage(source, x, y, width, height, null);
		}
		finally {
			g2.dispose();
		}
	}
}
