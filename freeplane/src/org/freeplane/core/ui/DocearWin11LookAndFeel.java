package org.freeplane.core.ui;

import java.awt.Color;

import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;

/**
 * Docear "Win11" look and feel: a light skin matching the Windows 11
 * design language (mica-grey surfaces, white content cards, neutral
 * strokes, accent blue #0067C0), based on Nimbus.
 *
 * Implementation notes (verified against the sources):
 * - Extends {@link NimbusLookAndFeel}: all Nimbus painters stay intact.
 *   Only a curated whitelist of color keys is put into the defaults table
 *   <b>after</b> {@code super.getDefaults()}, so plain values shadow the
 *   Nimbus entries without replacing any painter.
 * - Nimbus painters resolve their colors lazily through the "nimbus*"
 *   global anchors ("nimbusBase", "nimbusBlueGrey", ...), so overriding
 *   those anchors recolors the gradient painters (buttons, tabs, ...)
 *   without touching painter instances.
 * - Flamingo 6.3 (the ribbon) resolves its colors through UIManager keys:
 *   "Panel.background" (6 usages), "Button.foreground", "RibbonBand.background",
 *   "ControlPanel.background", "PopupPanel.background", ... (see
 *   flamingo-6.3-sources, FlamingoUtilities.getColor / BasicRibbonUI /
 *   BasicRibbonBandUI). These keys are part of the whitelist.
 * - Fonts are deliberately NOT changed in their family: on Java 8 a physical
 *   font ("Segoe UI") has no CJK glyph fallback (verified:
 *   new Font("Segoe UI").canDisplayUpTo("\u4e2d\u6587") == 0), while the
 *   logical font Nimbus uses ("Dialog") maps to Segoe UI for latin text
 *   on Windows 10/11 and falls back to a CJK font for Chinese.
 * - Sizes are the business of {@link DocearUiMetrics}, not of this class:
 *   after the colours are installed this look and feel hands its defaults
 *   table to {@link DocearUiDefaults}, which scales every font and the
 *   whitelisted insets with the single global font scale of
 *   {@link UiFontScale}. Nothing here multiplies a number on its own.
 * - Loading: UIManager.setLookAndFeel(String) resolves class names through
 *   the system class loader, which cannot see classes of the OSGi bundles.
 *   FrameController.setLookAndFeel therefore special-cases this class and
 *   instantiates it directly.
 */
public class DocearWin11LookAndFeel extends NimbusLookAndFeel {

	/** display name of the look and feel (also its value in the lookandfeel property) */
	public static final String LAF_NAME = "Docear Win11";
	/** values that used to be the shipped/persisted default before this skin existed */
	public static final String LEGACY_NIMBUS_NAME = "nimbus";
	public static final String LEGACY_NIMBUS_CLASS = "javax.swing.plaf.nimbus.NimbusLookAndFeel";

	// ---- Windows 11 light theme tokens (same palette as the approved mockup) ----
	private static final Color ACCENT         = new Color(0x0067C0); // accent fill (light)
	private static final Color BG_WINDOW      = new Color(0xF3F3F3); // mica / layer surface
	private static final Color BG_CARD        = new Color(0xFFFFFF); // content card
	private static final Color BG_MENU        = new Color(0xF9F9F9); // flyout / menu surface
	private static final Color BG_DISABLED    = new Color(0xF7F7F7);
	private static final Color STROKE         = new Color(0xD6D6D6); // control stroke
	private static final Color DIVIDER        = new Color(0xE5E5E5); // divider stroke
	private static final Color TEXT_PRIMARY   = new Color(0x1A1A1A);
	private static final Color TEXT_SECONDARY = new Color(0x5C5C5C);
	private static final Color TEXT_DISABLED  = new Color(0x8A8A8A);
	private static final Color SELECTION_BG   = new Color(0xCCE4FF); // list/table/tree selection (light accent)
	private static final Color SELECTION_FG   = new Color(0x1A1A1A);
	/** neutral grey ramp anchor that replaces Nimbus' blue-grey for control painters */
	private static final Color CONTROL_GREY   = new Color(0xC8C8C8);

	public DocearWin11LookAndFeel() {
		super();
	}

	@Override
	public String getID() {
		return "DocearWin11";
	}

	@Override
	public String getName() {
		return LAF_NAME;
	}

	@Override
	public String getDescription() {
		return "Docear skin matching the Windows 11 light theme (based on Nimbus)";
	}

	/** @return true if the value is one of the legacy default look-and-feel values. */
	public static boolean isLegacyDefault(final String value) {
		return LEGACY_NIMBUS_NAME.equalsIgnoreCase(value) || LEGACY_NIMBUS_CLASS.equals(value);
	}

	/**
	 * Registers this look and feel under the UIManager so that it appears in
	 * the preferences drop-down (which enumerates UIManager.getInstalledLookAndFeels()).
	 * Pure registration - no class loading happens through this entry
	 * (see the loading note in the class comment).
	 */
	public static void install() {
		final String className = DocearWin11LookAndFeel.class.getName();
		for (final UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
			if (className.equals(info.getClassName())) {
				return;
			}
		}
		UIManager.installLookAndFeel(LAF_NAME, className);
	}

	@Override
	public UIDefaults getDefaults() {
		final UIDefaults defaults = super.getDefaults();
		applyWin11Defaults(defaults);
		// Docear: scale fonts, insets and dimensions with the single global
		// font scale. Runs here - inside getDefaults() - so the values are
		// part of the look and feel table from the very beginning and every
		// component (and Nimbus' own style cache) picks them up unchanged.
		// Idempotent, so UiFontScale cannot scale the same table twice.
		DocearUiDefaults.install(defaults);
		return defaults;
	}

	private static void applyWin11Defaults(final UIDefaults d) {
		// --- Nimbus global painter anchors (recolor all gradient painters) ---
		color(d, "nimbusBase", ACCENT);
		color(d, "nimbusBlueGrey", CONTROL_GREY);
		color(d, "nimbusBorder", STROKE);
		color(d, "nimbusFocus", ACCENT);
		color(d, "nimbusSelectionBackground", SELECTION_BG);
		color(d, "nimbusSelectedText", SELECTION_FG);
		// --- basic color scheme keys (also read by Flamingo and custom components) ---
		color(d, "control", BG_WINDOW);
		color(d, "controlHighlight", BG_CARD);
		color(d, "controlShadow", STROKE);
		color(d, "controlDarkShadow", TEXT_DISABLED);
		color(d, "controlText", TEXT_PRIMARY);
		color(d, "text", TEXT_PRIMARY);
		color(d, "textInactiveText", TEXT_DISABLED);
		color(d, "textHighlight", ACCENT);
		color(d, "textHighlightText", BG_CARD);
		color(d, "info", BG_CARD);
		color(d, "infoText", TEXT_PRIMARY);
		// --- window/menu surfaces ---
		color(d, "Panel.background", BG_WINDOW);
		color(d, "OptionPane.background", BG_WINDOW);
		color(d, "OptionPane.messageForeground", TEXT_PRIMARY);
		color(d, "MenuBar.background", BG_WINDOW);
		color(d, "Menu.background", BG_MENU);
		color(d, "MenuItem.background", BG_MENU);
		color(d, "PopupMenu.background", BG_MENU);
		color(d, "ToolBar.background", BG_WINDOW);
		color(d, "Separator.foreground", DIVIDER);
		color(d, "SplitPane.background", BG_WINDOW);
		color(d, "ToolTip.background", BG_CARD);
		color(d, "ToolTip.foreground", TEXT_PRIMARY);
		// --- text components: white fields, accent text selection ---
		color(d, "TextField.background", BG_CARD);
		color(d, "TextField.foreground", TEXT_PRIMARY);
		color(d, "TextField.inactiveBackground", BG_DISABLED);
		color(d, "TextField.inactiveForeground", TEXT_DISABLED);
		color(d, "TextArea.background", BG_CARD);
		color(d, "TextArea.foreground", TEXT_PRIMARY);
		color(d, "TextArea.inactiveBackground", BG_DISABLED);
		color(d, "TextArea.inactiveForeground", TEXT_DISABLED);
		color(d, "TextPane.background", BG_CARD);
		color(d, "TextPane.foreground", TEXT_PRIMARY);
		color(d, "EditorPane.background", BG_CARD);
		color(d, "EditorPane.foreground", TEXT_PRIMARY);
		color(d, "FormattedTextField.background", BG_CARD);
		color(d, "FormattedTextField.foreground", TEXT_PRIMARY);
		color(d, "PasswordField.background", BG_CARD);
		color(d, "PasswordField.foreground", TEXT_PRIMARY);
		color(d, "Spinner.background", BG_CARD);
		color(d, "ComboBox.background", BG_CARD);
		color(d, "ComboBox.foreground", TEXT_PRIMARY);
		final String[] textSelectionKeys = { "TextField", "TextArea", "TextPane", "EditorPane",
		                                     "FormattedTextField", "PasswordField" };
		for (final String prefix : textSelectionKeys) {
			color(d, prefix + ".selectionBackground", ACCENT);
			color(d, prefix + ".selectionForeground", BG_CARD);
		}
		// --- lists / tables / trees: white content, light-accent selection ---
		color(d, "List.background", BG_CARD);
		color(d, "List.foreground", TEXT_PRIMARY);
		color(d, "List.selectionBackground", SELECTION_BG);
		color(d, "List.selectionForeground", SELECTION_FG);
		color(d, "Table.background", BG_CARD);
		color(d, "Table.foreground", TEXT_PRIMARY);
		color(d, "Table.selectionBackground", SELECTION_BG);
		color(d, "Table.selectionForeground", SELECTION_FG);
		color(d, "Table.gridColor", DIVIDER);
		color(d, "Table.alternateRowColor", new Color(0xF5F5F5));
		color(d, "TableHeader.background", BG_WINDOW);
		color(d, "TableHeader.foreground", TEXT_SECONDARY);
		color(d, "Tree.background", BG_CARD);
		color(d, "Tree.foreground", TEXT_PRIMARY);
		color(d, "Tree.textBackground", BG_CARD);
		color(d, "Tree.textForeground", TEXT_PRIMARY);
		color(d, "Tree.selectionBackground", SELECTION_BG);
		color(d, "Tree.selectionForeground", SELECTION_FG);
		color(d, "Tree.selectionBorderColor", ACCENT);
		color(d, "Tree.hash", DIVIDER);
		// --- button-like foregrounds (Flamingo reads "Button.foreground") ---
		final String[] foregroundKeys = { "Button", "ToggleButton", "RadioButton", "CheckBox",
		                                  "ComboBox", "Label", "ProgressBar", "Slider", "TabbedPane" };
		for (final String prefix : foregroundKeys) {
			color(d, prefix + ".foreground", TEXT_PRIMARY);
		}
		// --- tabs ---
		color(d, "TabbedPane.background", BG_WINDOW);
		// --- scrollbars ---
		color(d, "ScrollBar.thumb", CONTROL_GREY);
		color(d, "ScrollBar.track", BG_WINDOW);
		// --- Flamingo ribbon keys (flamingo-6.3 FlamingoUtilities.getColor usages) ---
		color(d, "RibbonBand.background", BG_CARD);
		color(d, "ControlPanel.background", BG_WINDOW);
		color(d, "PopupPanel.background", BG_MENU);
	}

	private static void color(final UIDefaults d, final String key, final Color value) {
		d.put(key, new ColorUIResource(value));
	}
}
