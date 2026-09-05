package org.freeplane.core.ui;

/**
 * Docear UI design tokens - the single, unscaled source of every UI
 * measurement of the application.
 * <p>
 * This class is deliberately dumb: it contains nothing but constants and
 * their mutual derivation, no look and feel access, no
 * {@link javax.swing.UIManager} access and no dependency on any other class.
 * That makes it safe to load at any point of the start up sequence and safe
 * to use from every OSGi bundle that imports {@code org.freeplane.core.ui}.
 * <p>
 * All values are expressed in pixels and describe the <b>unscaled</b> base
 * layout, i.e. the layout at a global UI font scale of 1.0. Only
 * {@link DocearUiMetrics} turns them into the values that are actually
 * applied - by multiplying them with the font scale. No other class is
 * supposed to do that arithmetic.
 * <p>
 * Derivation rule: every token is either the base unit or an integer
 * multiple / difference of it, so changing {@link #UNIT} moves the whole
 * layout coherently. Adding a number here that is not derived from
 * {@link #UNIT} or from another token is what this class exists to prevent.
 *
 * @see DocearUiMetrics
 */
public final class DocearUiTokens {

	/** Base spacing unit (px). Every other token is derived from it. */
	public static final int UNIT = 4;

	/** Extra tight gap (px): icon/text hairline spacing, separator padding. */
	public static final int GAP_XS = UNIT / 2;
	/** Small gap (px): default inner padding of a control. */
	public static final int GAP_S = UNIT;
	/** Medium gap (px): spacing between two controls. */
	public static final int GAP_M = 2 * UNIT;
	/** Large gap (px): spacing between logical groups of controls. */
	public static final int GAP_L = 3 * UNIT;

	/** Hairline border / divider stroke width (px). */
	public static final int BORDER_WIDTH = 1;

	/** Preferred height of a standard control (button, combo box, ...) (px). */
	public static final int CONTROL_HEIGHT = 6 * UNIT;
	/** Height of one row of a list / table / tree (px). */
	public static final int ROW_HEIGHT = 5 * UNIT;
	/** Standard icon edge length of toolbar and menu icons (px). */
	public static final int ICON_SIZE = 4 * UNIT;

	/**
	 * Height of the ribbon task toggle row (the row holding the Home / Notes
	 * / Project tabs) (px). Flamingo hard codes 22 in
	 * {@code BasicRibbonUI#getTaskToggleButtonHeight()}; it is one extra
	 * tight gap below a standard control so the tabs optically belong to the
	 * band below them.
	 */
	public static final int TAB_HEIGHT = CONTROL_HEIGHT - GAP_XS;
	/**
	 * Height of the ribbon taskbar area (px). Flamingo hard codes 24 in
	 * {@code BasicRibbonUI#getTaskbarHeight()}.
	 */
	public static final int TASKBAR_HEIGHT = CONTROL_HEIGHT;

	private DocearUiTokens() {
	}
}
