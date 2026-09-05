package org.freeplane.core.ui.ribbon;

import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;

import org.freeplane.core.ui.DocearUiMetrics;
import org.freeplane.core.ui.DocearUiTokens;
import org.pushingpixels.flamingo.internal.ui.ribbon.BasicRibbonUI;

/**
 * Ribbon UI delegate that scales the two hard coded heights of
 * {@link BasicRibbonUI} with the global UI font scale (see
 * {@link org.freeplane.core.ui.UiFontScale}).
 * <p>
 * {@code BasicRibbonUI#getTaskToggleButtonHeight()} (the row with the
 * Home/Notes/Project task tabs) returns a constant 22 and
 * {@code BasicRibbonUI#getTaskbarHeight()} a constant 24. They are added to
 * the preferred height of the whole ribbon, so while every band grows with
 * the scaled fonts these two rows stay at their original pixel height and the
 * task tabs end up cramped / clipped at larger font scales.
 * <p>
 * Flamingo resolves its UI delegate through
 * {@code BasicRibbonUI.createUI()} which is hard coded to
 * {@code new BasicRibbonUI()} and cannot be replaced by a UIManager key, so
 * this subclass is installed explicitly by {@link RibbonBuilder}.
 * <p>
 * At scale 1.0 the returned values are identical to the Flamingo defaults,
 * i.e. no behaviour change for unscaled setups.
 */
public class DocearRibbonUI extends BasicRibbonUI {

	/**
	 * Flamingo default height of the task toggle button row, unscaled. Taken
	 * from {@link DocearUiTokens#TAB_HEIGHT} (22) instead of repeating the
	 * literal, so the Flamingo default and the rest of the UI cannot drift
	 * apart.
	 */
	public static final int BASE_TASK_TOGGLE_BUTTON_HEIGHT = DocearUiTokens.TAB_HEIGHT;
	/**
	 * Flamingo default height of the taskbar area, unscaled. Taken from
	 * {@link DocearUiTokens#TASKBAR_HEIGHT} (24).
	 */
	public static final int BASE_TASKBAR_HEIGHT = DocearUiTokens.TASKBAR_HEIGHT;

	@Override
	public int getTaskToggleButtonHeight() {
		return DocearUiMetrics.scale(BASE_TASK_TOGGLE_BUTTON_HEIGHT);
	}

	@Override
	public int getTaskbarHeight() {
		// The taskbar buttons request the user configured top bar icon size
		// (RibbonActionContributorFactory.getScaledTopBarIconSize(), default
		// 32 px), but BasicRibbonUI$TaskbarLayout.layoutContainer forces every
		// taskbar component to exactly this row height - a 32 px icon inside a
		// 24 px row is clipped top and bottom and the icon size setting looks
		// like it "does nothing". So the row grows with the configured icon
		// size (+2 x 4 px vertical padding) and never shrinks below the
		// Flamingo default.
		final int needed = RibbonActionContributorFactory.getScaledTopBarIconSize() + 8;
		return Math.max(DocearUiMetrics.scale(BASE_TASKBAR_HEIGHT), needed);
	}

	/**
	 * Required by {@code UIDefaults.getUI()} which instantiates UI delegates
	 * reflectively through this static factory (and which would otherwise
	 * inherit {@code BasicRibbonUI.createUI()} and create a plain
	 * {@code BasicRibbonUI}).
	 */
	public static ComponentUI createUI(final JComponent c) {
		return new DocearRibbonUI();
	}
}
