package org.freeplane.core.ui;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.mode.Controller;

/**
 * The single source of the global UI font scale (Docear): one user
 * configurable factor that makes the whole application (menus, ribbons,
 * dialogs, lists, text fields, tooltips, ...) grow together instead of only
 * single components.
 *
 * <h3>What this class is - and what it is not</h3>
 * This class answers exactly one question: <i>what is the current UI
 * scale?</i> ({@link #getUiFontScale()}). It does <b>not</b> scale anything
 * itself and it does <b>not</b> install or refresh anything.
 * <p>
 * Applying the scale is the job of {@link DocearUiDefaults}, which derives
 * every applied value from {@link DocearUiTokens} through
 * {@link DocearUiMetrics}. Previously this class scanned the look and feel
 * defaults for keys ending in "font" and pushed scaled copies into the
 * UIManager - a bolt on override that worked for fonts only and left insets,
 * dimensions and row heights untouched (the reason why buttons, combo boxes
 * and table rows did not grow with the font). That scan has been removed; the
 * single, complete replacement is {@link DocearUiDefaults#install}.
 * <p>
 * Installing the look and feel with a given scale and re-applying it at
 * runtime is the job of {@link DocearUiInstaller}, which is also the only
 * place the ribbon UI delegate is registered now.
 *
 * Scope: this intentionally does NOT cover the mind map content fonts
 * (node/note text); those are document fonts controlled by the
 * defaultfont/defaultfontsize properties and the view zoom.
 *
 * The scale is applied at startup through
 * {@link DocearUiInstaller#install(String)}. Runtime changes go through
 * {@link DocearUiInstaller#applyFontScale(float)} and take effect
 * immediately - no restart required.
 */
public class UiFontScale {

	/** user configurable global UI font scale (1.0 = original size) */
	public static final String FONT_SCALE_PROPERTY = "docear.ui.fontscale";
	/**
	 * legacy property of the former ribbon-only font scaling; used as
	 * fallback once so an existing user setting carries over
	 */
	public static final String LEGACY_TOP_BAR_FONT_SCALE_PROPERTY = "docear.topbar.fontscale";
	private static final float DEFAULT_FONT_SCALE = 1.5f;

	/**
	 * Global UI font scale factor (1.0 = original size).
	 * <p>
	 * A {@code -Ddocear.ui.fontscale=...} system property wins over the
	 * stored preference; it exists for tests and for a one off start with a
	 * different scale, and mirrors the existing {@code -Dlookandfeel=...}
	 * override.
	 */
	public static float getUiFontScale() {
		final String override = System.getProperty(FONT_SCALE_PROPERTY);
		if (override != null) {
			try {
				return Float.parseFloat(override);
			}
			catch (NumberFormatException e) {
				// fall through to the stored preference
			}
		}
		final Controller controller = Controller.getCurrentController();
		if (controller == null) {
			return DEFAULT_FONT_SCALE;
		}
		final ResourceController resources = controller.getResourceController();
		if (resources == null) {
			return DEFAULT_FONT_SCALE;
		}
		try {
			final String value = resources.getProperty(FONT_SCALE_PROPERTY, null);
			if (value != null) {
				return Float.parseFloat(value);
			}
		}
		catch (NumberFormatException e) {
		}
		try {
			final String legacyValue = resources.getProperty(LEGACY_TOP_BAR_FONT_SCALE_PROPERTY, null);
			if (legacyValue != null) {
				return Float.parseFloat(legacyValue);
			}
		}
		catch (NumberFormatException e) {
		}
		return DEFAULT_FONT_SCALE;
	}
}
