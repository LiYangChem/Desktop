package org.freeplane.core.ui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;

/**
 * Docear UI metrics - the single place where a {@link DocearUiTokens} base
 * value is turned into a value that is actually applied to the UI.
 * <p>
 * The whole application is allowed to know exactly two things about UI
 * measurements:
 * <ol>
 * <li>the unscaled base value, defined in {@link DocearUiTokens};</li>
 * <li>the current global font scale, defined by {@link UiFontScale}.</li>
 * </ol>
 * Everything else - the applied pixel size, paddings and heights - is
 * computed here and nowhere else. Consumers must not repeat the
 * multiplication and must not introduce their own constants.
 * <p>
 * Scaling rule: {@code applied = round(base * fontScale)}. At a font scale of
 * 1.0 every accessor returns the plain token value, so wiring a component to
 * this class does not change the unscaled layout.
 * <p>
 * This class is stateless and holds no cache: it reads the font scale from
 * {@link UiFontScale} on every call, so it can never go stale. The cost is
 * one property lookup per call, the same cost the ribbon UI already pays
 * today.
 *
 * @see DocearUiTokens
 * @see UiFontScale
 */
public final class DocearUiMetrics {

	/**
	 * The global UI font scale currently in effect (1.0 = unscaled).
	 * Delegates to {@link UiFontScale} so there is exactly one definition of
	 * "the scale" in the application.
	 */
	public static float fontScale() {
		return UiFontScale.getUiFontScale();
	}

	// ---------------------------------------------------------------- scaling

	/**
	 * Scales a base pixel value with the current font scale. Zero stays zero
	 * (a zero gap must not become a one pixel gap) and no scaled value
	 * collapses below one pixel.
	 */
	public static int scale(final int baseValue) {
		return scale(baseValue, fontScale());
	}

	/** Scales a base pixel value with an explicitly given scale. */
	public static int scale(final int baseValue, final float scale) {
		if (baseValue == 0) {
			return 0;
		}
		final int scaled = Math.round(baseValue * scale);
		return scaled == 0 ? (baseValue < 0 ? -1 : 1) : scaled;
	}

	/**
	 * Returns {@code base} with its point size multiplied by the current
	 * font scale. {@code null} is passed through.
	 */
	public static Font scaleFont(final Font base) {
		return scaleFont(base, fontScale());
	}

	/** Returns {@code base} with its point size multiplied by {@code scale}. */
	public static Font scaleFont(final Font base, final float scale) {
		if (base == null || scale == 1.0f) {
			return base;
		}
		return base.deriveFont(base.getSize2D() * scale);
	}

	/**
	 * Scales all four edges of {@code base} with the current font scale. The
	 * argument is not modified. {@code null} is passed through.
	 */
	public static Insets scaleInsets(final Insets base) {
		return scaleInsets(base, fontScale());
	}

	/** Scales all four edges of {@code base} with {@code scale}. */
	public static Insets scaleInsets(final Insets base, final float scale) {
		if (base == null) {
			return null;
		}
		return new Insets(scale(base.top, scale), scale(base.left, scale), scale(base.bottom, scale),
		    scale(base.right, scale));
	}

	/**
	 * Scales width and height of {@code base} with the current font scale.
	 * The argument is not modified. {@code null} is passed through.
	 */
	public static Dimension scaleDimension(final Dimension base) {
		return scaleDimension(base, fontScale());
	}

	/** Scales width and height of {@code base} with {@code scale}. */
	public static Dimension scaleDimension(final Dimension base, final float scale) {
		if (base == null) {
			return null;
		}
		return new Dimension(scale(base.width, scale), scale(base.height, scale));
	}

	// ------------------------------------------------------------ token values

	/** Scaled base spacing unit (px). */
	public static int unit() {
		return scale(DocearUiTokens.UNIT);
	}

	/** Scaled extra tight gap (px). */
	public static int gapXS() {
		return scale(DocearUiTokens.GAP_XS);
	}

	/** Scaled small gap (px). */
	public static int gapS() {
		return scale(DocearUiTokens.GAP_S);
	}

	/** Scaled medium gap (px). */
	public static int gapM() {
		return scale(DocearUiTokens.GAP_M);
	}

	/** Scaled large gap (px). */
	public static int gapL() {
		return scale(DocearUiTokens.GAP_L);
	}

	/** Scaled border / divider stroke width (px); stays a hairline. */
	public static int borderWidth() {
		return scale(DocearUiTokens.BORDER_WIDTH);
	}

	/** Scaled preferred height of a standard control (px). */
	public static int controlHeight() {
		return scale(DocearUiTokens.CONTROL_HEIGHT);
	}

	/** Scaled height of a list / table / tree row (px). */
	public static int rowHeight() {
		return scale(DocearUiTokens.ROW_HEIGHT);
	}

	/** Scaled standard icon edge length (px). */
	public static int iconSize() {
		return scale(DocearUiTokens.ICON_SIZE);
	}

	/** Scaled height of the ribbon task toggle row (px). */
	public static int tabHeight() {
		return scale(DocearUiTokens.TAB_HEIGHT);
	}

	/** Scaled height of the ribbon taskbar area (px). */
	public static int taskbarHeight() {
		return scale(DocearUiTokens.TASKBAR_HEIGHT);
	}

	// ------------------------------------------------------------- composers

	/** Uniform, scaled {@link Insets} of {@code all} base pixels. */
	public static Insets insets(final int all) {
		return insets(all, all, all, all);
	}

	/** Scaled {@link Insets} with separate vertical and horizontal base values. */
	public static Insets insets(final int vertical, final int horizontal) {
		return insets(vertical, horizontal, vertical, horizontal);
	}

	/**
	 * Scaled {@link Insets} built from four unscaled base values
	 * (top, left, bottom, right).
	 */
	public static Insets insets(final int top, final int left, final int bottom, final int right) {
		return new Insets(scale(top), scale(left), scale(bottom), scale(right));
	}

	/** Scaled {@link Dimension} built from two unscaled base values. */
	public static Dimension dimension(final int width, final int height) {
		return new Dimension(scale(width), scale(height));
	}

	private DocearUiMetrics() {
	}
}
