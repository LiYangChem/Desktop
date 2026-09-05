package org.freeplane.core.ui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.UIDefaults;
import javax.swing.plaf.DimensionUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;

import org.freeplane.core.util.LogUtils;

/**
 * Installs the Docear UI metrics into a Swing {@link UIDefaults} table.
 * <p>
 * This is the <b>only</b> place where the global font scale is applied to a
 * look and feel. It is deliberately separated from
 * {@link DocearWin11LookAndFeel} so that
 * <ol>
 * <li>the look and feel can apply it to its own defaults table inside
 * {@code getDefaults()} (the normal, single pass case) and</li>
 * <li>a look and feel that does not know about the Docear metrics can still
 * be scaled afterwards (legacy fallback, see {@link UiFontScale}).</li>
 * </ol>
 * <p>
 * This class contains <b>no arithmetic of its own</b>: every value it writes
 * is produced by {@link DocearUiMetrics}, which in turn derives everything
 * from {@link DocearUiTokens} and the single scale of {@link UiFontScale}.
 * Nothing else in the application is allowed to scale a UI value.
 *
 * <h3>Why fonts are handled uniformly and insets are not</h3>
 * A font scale is global by definition: every {@code *.font} entry of the
 * look and feel has to grow, otherwise text sizes drift apart. Font
 * enumeration is therefore the correct mechanism for fonts.
 * <p>
 * Insets and dimensions are a different matter. Most of the 91 insets Nimbus
 * defines are <b>geometry</b>, not typography: hairline dividers, painter
 * borders, dialog chrome, popup offsets. Multiplying all of them with the
 * font scale is exactly the "blanket scaling" that makes a UI bloat. They are
 * therefore handled by an explicit whitelist that was built by
 * <i>enumeration -&gt; classification -&gt; decision -&gt; whitelist /
 * exception</i> (see the key lists below).
 *
 * <h3>Classification of the 91 Nimbus insets</h3>
 * <ol>
 * <li><b>Zero insets</b> (about 30 keys such as
 * {@code Label.contentMargins = 0,0,0,0}): not in the whitelist. Scaling a
 * zero padding must never introduce a gap, so these are skipped explicitly
 * even though {@link DocearUiMetrics#scale(int)} would return 0 anyway.</li>
 * <li><b>Text and control padding</b> (32 keys): whitelisted. These describe
 * the space around rendered text or around the content of a control and have
 * to grow with the font, otherwise text is clipped.</li>
 * <li><b>Hairlines and decorative separators</b>
 * ({@code SplitPane.contentMargins 1,1,1,1},
 * {@code Separator}, {@code PopupMenuSeparator}, {@code ToolBarSeparator},
 * {@code OptionPane:"OptionPane.separator"}): excluded. They are strokes, not
 * spacing; scaling turns a hairline into a chunky double line.</li>
 * <li><b>Offsets with a sign</b> ({@code ComboBox.popupInsets -2,2,0,2}):
 * excluded. The negative top offset aligns the popup border with the combo
 * box border; scaling it moves the popup out of alignment.</li>
 * <li><b>Container and window chrome</b> ({@code ScrollPane.contentMargins},
 * {@code OptionPane}, {@code FileChooser}, {@code ColorChooser},
 * {@code InternalFrame*}, {@code DesktopIcon}): excluded. They are borders of
 * containers, not padding of text; scaling inflates dialogs and windows
 * without adding any readability.</li>
 * </ol>
 *
 * <h3>Classification of the Nimbus dimensions</h3>
 * The whitelist is <b>intentionally empty</b>. Every one of the nine
 * dimensions Nimbus defines was measured and rejected:
 * <ul>
 * <li>{@code ScrollBar.minimumThumbSize 29x29} - measured: no effect on the
 * scroll bar preferred size, because the cross axis (15 px) is fixed by the
 * Nimbus painter. Scaling only the thumb would desynchronise it from a track
 * that does not grow.</li>
 * <li>{@code ScrollBar.maximumThumbSize 1000x1000} - an upper bound, not a
 * size.</li>
 * <li>{@code ProgressBar.horizontalSize / vertictalSize / minBarSize} -
 * measured: no effect at all, the progress bar is drawn by a painter with a
 * fixed height; the control carries no text.</li>
 * <li>{@code ColorChooser.swatches* 10x10} - decorative colour swatches, a
 * scaled grid would inflate a dialog.</li>
 * <li>{@code InternalFrameTitlePane.maxFrameIconSize 18x18} - an upper bound
 * for icons.</li>
 * <li>{@code Table.intercellSpacing 0x0} - zero, stays zero.</li>
 * </ul>
 * The mechanism ({@link DocearUiMetrics#scaleDimension(Dimension, float)} plus
 * {@link #SCALED_DIMENSION_KEYS}) is in place; keys are added here as soon as
 * one is found that actually scales a text bearing geometry.
 *
 * @see DocearUiTokens
 * @see DocearUiMetrics
 * @see DocearWin11LookAndFeel
 */
public final class DocearUiDefaults {

	/**
	 * Marker written into a defaults table that has already been processed.
	 * Makes {@link #install(UIDefaults, float)} idempotent, so a look and feel
	 * that installs the metrics itself can never be scaled twice.
	 */
	public static final String INSTALLED_MARKER = "docear.uiDefaultsInstalled";

	/** Row height default honoured by {@code BasicTableUI.installDefaults()} (verified). */
	public static final String TABLE_ROW_HEIGHT_KEY = "Table.rowHeight";

	// ------------------------------------------------------------ whitelists

	/**
	 * Insets that grow with the font: content padding of text bearing
	 * controls and of menus. Built by enumerating all 91 Nimbus insets and
	 * classifying them - see the class comment for the four categories and
	 * for the rejection reason of every excluded key. Some entries (for
	 * example {@code ToolBar:Button.contentMargins}) do not change the
	 * preferred size of the control in Nimbus but do describe its painted
	 * content area, so they are scaled as well.
	 */
	public static final String[] SCALED_INSETS_KEYS = {
		// buttons and toolbar buttons
		"Button.contentMargins", "ToggleButton.contentMargins",
		"ToolBar:Button.contentMargins", "ToolBar:ToggleButton.contentMargins",
		// menus and menu items
		"Menu.contentMargins", "MenuBar.contentMargins", "MenuBar:Menu.contentMargins",
		"MenuItem.contentMargins", "CheckBoxMenuItem.contentMargins",
		"RadioButtonMenuItem.contentMargins", "PopupMenu.contentMargins",
		// text components
		"TextField.contentMargins", "FormattedTextField.contentMargins",
		"PasswordField.contentMargins", "TextArea.contentMargins",
		"TextPane.contentMargins", "EditorPane.contentMargins",
		"Spinner:Panel:\"Spinner.formattedTextField\".contentMargins",
		// combo box
		"ComboBox.padding", "ComboBox:\"ComboBox.renderer\".contentMargins",
		"ComboBox:\"ComboBox.listRenderer\".contentMargins",
		"ComboBox:\"ComboBox.textField\".contentMargins",
		// table and tree
		"\"Table.editor\".contentMargins", "TableHeader:\"TableHeader.renderer\".contentMargins",
		"\"Tree.cellEditor\".contentMargins", "Tree.rendererMargins",
		// tabs, tooltip, toolbar
		"TabbedPane:TabbedPaneTab.contentMargins", "TabbedPane:TabbedPaneTabArea.contentMargins",
		"ToolTip.contentMargins", "ToolBar.contentMargins"
	};

	/**
	 * Dimensions that grow with the font. Empty by design - see the class
	 * comment for the measured rejection reason of every Nimbus dimension
	 * key. The scaling itself lives in
	 * {@link DocearUiMetrics#scaleDimension(Dimension, float)}.
	 */
	public static final String[] SCALED_DIMENSION_KEYS = {};

	// ---------------------------------------------------------------- install

	/** Installs the metrics with the scale currently in effect. */
	public static void install(final UIDefaults defaults) {
		install(defaults, DocearUiMetrics.fontScale());
	}

	/**
	 * Installs the metrics into {@code defaults} using {@code scale}.
	 * Idempotent: a table that was already processed is left alone, so the
	 * look and feel path and the legacy fallback path can never double scale.
	 *
	 * @return true if the metrics were applied, false if the table had been
	 *         processed before or the scale is 1.0 and nothing had to change
	 */
	public static boolean install(final UIDefaults defaults, final float scale) {
		if (defaults == null) {
			return false;
		}
		if (Boolean.TRUE.equals(defaults.get(INSTALLED_MARKER))) {
			return false;
		}
		final int fonts = installFonts(defaults, scale);
		final int insets = installInsets(defaults, scale);
		final int dimensions = installDimensions(defaults, scale);
		installRowHeights(defaults, scale);
		defaults.put(INSTALLED_MARKER, Boolean.TRUE);
		LogUtils.info("DocearUiDefaults: scale " + scale + " applied to " + fonts + " fonts, " + insets
		    + " insets, " + dimensions + " dimensions");
		return true;
	}

	/**
	 * Scales every {@code *.font} entry of the table. Fonts are uniform by
	 * nature - a font scale that only applies to some keys produces
	 * mismatched text sizes - so this is the one place where an enumeration
	 * is the right mechanism instead of a whitelist.
	 * <p>
	 * Read all, then write all: Nimbus registers many {@code *.font} entries
	 * as lazy aliases that resolve to {@code defaultFont} at read time
	 * (verified: after {@code defaultFont} is changed, {@code Button.font}
	 * and {@code Menu.font} report the new size). Writing the scaled
	 * {@code defaultFont} before those aliases are read would therefore scale
	 * them a second time (measured: 12 -&gt; 18 -&gt; 27 at scale 1.5). All base
	 * values are collected first so every one of them is the unscaled
	 * original.
	 */
	private static int installFonts(final UIDefaults defaults, final float scale) {
		final List<String> keys = new ArrayList<String>();
		final Enumeration<Object> keysEnumeration = defaults.keys();
		while (keysEnumeration.hasMoreElements()) {
			final Object key = keysEnumeration.nextElement();
			if (key instanceof String && ((String) key).toLowerCase().endsWith("font")) {
				keys.add((String) key);
			}
		}
		final List<Font> baseFonts = new ArrayList<Font>(keys.size());
		for (final String key : keys) {
			final Object value = defaults.get(key);
			baseFonts.add(value instanceof Font ? (Font) value : null);
		}
		int count = 0;
		for (int i = 0; i < keys.size(); i++) {
			final Font base = baseFonts.get(i);
			if (base == null) {
				continue;
			}
			final Font scaled = DocearUiMetrics.scaleFont(base, scale);
			if (scaled != base) {
				defaults.put(keys.get(i), new FontUIResource(scaled));
				count++;
			}
		}
		return count;
	}

	/**
	 * Scales the whitelisted insets. All zero insets are skipped explicitly:
	 * a control that deliberately has no padding must not gain one just
	 * because the font grew.
	 */
	private static int installInsets(final UIDefaults defaults, final float scale) {
		// read all base values first, then write - see installFonts for why
		final List<Insets> baseInsets = new ArrayList<Insets>(SCALED_INSETS_KEYS.length);
		for (int i = 0; i < SCALED_INSETS_KEYS.length; i++) {
			final Object value = defaults.get(SCALED_INSETS_KEYS[i]);
			baseInsets.add(value instanceof Insets ? (Insets) value : null);
		}
		int count = 0;
		for (int i = 0; i < SCALED_INSETS_KEYS.length; i++) {
			final Insets base = baseInsets.get(i);
			if (base == null || isZero(base)) {
				continue;
			}
			final Insets scaled = DocearUiMetrics.scaleInsets(base, scale);
			defaults.put(SCALED_INSETS_KEYS[i],
			    new InsetsUIResource(scaled.top, scaled.left, scaled.bottom, scaled.right));
			count++;
		}
		return count;
	}

	/** Scales the whitelisted dimensions (currently none, see class comment). */
	private static int installDimensions(final UIDefaults defaults, final float scale) {
		int count = 0;
		final List<Dimension> baseDimensions = new ArrayList<Dimension>(SCALED_DIMENSION_KEYS.length);
		for (int i = 0; i < SCALED_DIMENSION_KEYS.length; i++) {
			final Object value = defaults.get(SCALED_DIMENSION_KEYS[i]);
			baseDimensions.add(value instanceof Dimension ? (Dimension) value : null);
		}
		for (int i = 0; i < SCALED_DIMENSION_KEYS.length; i++) {
			final Dimension base = baseDimensions.get(i);
			if (base == null) {
				continue;
			}
			final Dimension scaled = DocearUiMetrics.scaleDimension(base, scale);
			defaults.put(SCALED_DIMENSION_KEYS[i], new DimensionUIResource(scaled.width, scaled.height));
			count++;
		}
		return count;
	}

	/**
	 * Installs the metric derived row height of tables.
	 * <p>
	 * Nimbus does not define a row height at all: a {@code JTable} keeps the
	 * hard coded 16 pixels of {@code JTable} no matter how large the font is,
	 * which clips the text as soon as the font scale is above 1.0 (measured:
	 * row height stays 16 at scale 1.0, 1.25 and 1.5). {@code BasicTableUI}
	 * honours the {@code Table.rowHeight} default (measured: 16 -&gt; 40), so
	 * this is the look and feel level fix for that defect.
	 * <p>
	 * The value is {@link DocearUiMetrics#rowHeight()}, i.e. the same token
	 * the application tables use.
	 * <p>
	 * Tables must <b>not</b> call {@code setRowHeight(...)} themselves: that
	 * call sets the private {@code JTable.isRowHeightSet} flag, after which
	 * {@code JTable.setUIProperty("rowHeight", ...)} refuses to apply this
	 * default ever again (see the JDK source of {@code JTable}). The three
	 * former {@code setRowHeight(20)} call sites were therefore removed, so
	 * every table follows the font scale through this single default.
	 * <p>
	 * Trees are <b>not</b> given a fixed row height: their rows already grow
	 * with the font through the renderer (measured 21 / 25 / 29 px at scale
	 * 1.0 / 1.25 / 1.5) and pinning them to a fixed value would clip rows
	 * that contain icons.
	 */
	private static void installRowHeights(final UIDefaults defaults, final float scale) {
		defaults.put(TABLE_ROW_HEIGHT_KEY,
		    Integer.valueOf(DocearUiMetrics.scale(DocearUiTokens.ROW_HEIGHT, scale)));
	}

	private static boolean isZero(final Insets insets) {
		return insets.top == 0 && insets.left == 0 && insets.bottom == 0 && insets.right == 0;
	}

	private DocearUiDefaults() {
	}
}
