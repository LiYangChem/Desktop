package org.freeplane.core.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.ribbon.DocearRibbonUI;
import org.freeplane.core.ui.ribbon.RibbonActionContributorFactory;
import org.freeplane.core.ui.ribbon.RibbonBuilder;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.ui.FrameController;
import org.pushingpixels.flamingo.api.common.JCommandButtonStrip;

/**
 * The single entry point for everything that changes the look of the
 * application: installing the UI at startup and re-applying it at runtime.
 * <p>
 * Before this class existed the application had four unrelated places that
 * each did <i>part</i> of the job:
 * <ul>
 * <li>{@code FreeplaneGUIStarter} ran the startup sequence
 * {@code DocearWin11LookAndFeel.install() -&gt; FrameController.setLookAndFeel()
 * -&gt; UiFontScale.applyGlobalFontScale()} inline, without a name;</li>
 * <li>{@code RibbonAppearanceDialog} persisted a new font scale and then
 * refreshed the ribbon <b>icons only</b>, telling the user to restart for the
 * rest;</li>
 * <li>{@code RibbonBuilder.reapplyTopBarScaling()} was that icon only
 * refresh, and was the only refresh path that existed at all;</li>
 * <li>switching the look and feel in the preferences only stored a property
 * and never refreshed anything.</li>
 * </ul>
 * Each of them is now routed through this class, so there is exactly one
 * install path and exactly one refresh path.
 *
 * <h3>The install chain</h3>
 * {@link #install(String)} runs the complete chain in this order:
 * <ol>
 * <li><b>LAF</b> - {@link FrameController#setLookAndFeel(String)} resolves
 * the configured value, instantiates the look and feel and makes it
 * current.</li>
 * <li><b>UI defaults / metrics</b> - {@link DocearWin11LookAndFeel} hands its
 * own defaults table to {@link DocearUiDefaults} inside
 * {@code getDefaults()}, so the scaled fonts, insets and row heights exist
 * before the first component is created. Any other look and feel is scaled
 * here, through the very same {@link DocearUiDefaults}, so there is still
 * only one scaling implementation.</li>
 * <li><b>Ribbon UI</b> - the Flamingo delegate key {@code "RibbonUI"} is
 * registered. It has to be present before the first {@code JRibbon} is
 * created, because Flamingo hard codes its delegate in
 * {@code BasicRibbonUI.createUI()}.</li>
 * </ol>
 * The chain deliberately does <b>not</b> touch components: at startup no
 * window exists yet, so there is nothing to refresh.
 *
 * <h3>The refresh chain</h3>
 * {@link #reapply()} is the runtime counterpart and runs, in this order:
 * <ol>
 * <li><b>Metrics recalculation</b> - the current look and feel is
 * re-installed. This is not a workaround: a look and feel produces its
 * defaults table in {@code getDefaults()}, so re-installing it is the only
 * way to get a table built from the <i>unscaled</i> base values again.
 * Overwriting the already scaled values in place would scale them a second
 * time (measured in Phase 1: 12 -&gt; 18 -&gt; 27 at scale 1.5).</li>
 * <li><b>Component refresh</b> - {@link #refreshAll()} walks the existing
 * windows (never a global component scan) and calls
 * {@link SwingUtilities#updateComponentTreeUI(Component)} on each of
 * them.</li>
 * <li><b>Ribbon icons</b> - last, because {@code updateComponentTreeUI}
 * rebuilds the ribbon's UI delegates and would otherwise drop the
 * user configured icon size.</li>
 * </ol>
 *
 * <h3>Component state</h3>
 * Measured with a probe window that carries state (see the Phase 3 report):
 * table selection, tree selection and expansion, combo box selection,
 * scroll position, split pane divider, tab selection and the keyboard focus
 * all survive {@code updateComponentTreeUI}. The <b>only</b> state that is
 * reset is the <b>caret position of text components</b>, because installing a
 * text UI re-creates the caret. {@link #refresh(Window)} therefore saves and
 * restores caret positions around the refresh - one small, explicit step
 * instead of scattered workarounds.
 *
 * @see DocearUiTokens
 * @see DocearUiMetrics
 * @see DocearUiDefaults
 * @see UiFontScale
 */
public final class DocearUiInstaller {

	/** the look and feel preference key, also read by MModeController's combo box */
	private static final String LOOKANDFEEL_PROPERTY = "lookandfeel";

	// --------------------------------------------------------------- install

	/**
	 * Installs the UI with the look and feel stored in the user properties.
	 * Only equivalent to {@link #install(String)} once that property holds the
	 * value that should be used; the startup path passes its (possibly
	 * migrated) value explicitly.
	 */
	public static void install() {
		install(ResourceController.getResourceController().getProperty(LOOKANDFEEL_PROPERTY));
	}

	/**
	 * Runs the complete install chain: look and feel -&gt; UI defaults /
	 * metrics -&gt; ribbon UI.
	 *
	 * @param lookAndFeelSpec the configured {@code lookandfeel} value: a
	 *        display name, a class name, the LAF name of the Docear skin or
	 *        {@code "default"}
	 */
	public static void install(final String lookAndFeelSpec) {
		// 1a - make the bundled Docear skin known to UIManager, so the
		// preferences drop-down and the lookup by name below can find it
		DocearWin11LookAndFeel.install();
		// 1b - resolve the configured value and make it current
		FrameController.setLookAndFeel(lookAndFeelSpec);
		installMetrics();
		installRibbonUi();
		LogUtils.info("DocearUiInstaller: installed \"" + lookAndFeelSpec + "\" as "
		    + UIManager.getLookAndFeel().getName() + " with scale " + DocearUiMetrics.fontScale());
	}

	/**
	 * Makes sure the metrics are part of the current defaults table. A look
	 * and feel that installs them itself (currently
	 * {@link DocearWin11LookAndFeel}) is already scaled and
	 * {@link DocearUiDefaults} is idempotent, so calling this again is free.
	 */
	private static void installMetrics() {
		final LookAndFeel lookAndFeel = UIManager.getLookAndFeel();
		if (lookAndFeel == null || lookAndFeel instanceof DocearWin11LookAndFeel) {
			return;
		}
		DocearUiDefaults.install(UIManager.getDefaults());
	}

	/**
	 * Registers the metric aware ribbon UI delegate. Kept here (and not in
	 * {@link UiFontScale}) because it belongs to the install chain, not to the
	 * question "what is the current scale".
	 */
	private static void installRibbonUi() {
		UIManager.put("RibbonUI", DocearRibbonUI.class.getName());
	}

	// ------------------------------------------------------- runtime changes

	/**
	 * Switches to another look and feel and refreshes the whole UI.
	 * <p>
	 * This is the only place where a look and feel may be switched at
	 * runtime; it goes through the full install chain (including the look and
	 * feel's own startup work such as the file chooser probe) and then through
	 * the single refresh path.
	 */
	public static void applyLookAndFeel(final String lookAndFeelSpec) {
		if (SwingUtilities.isEventDispatchThread()) {
			install(lookAndFeelSpec);
			reapplyRibbonIcons();
			refreshAll();
			return;
		}
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				install(lookAndFeelSpec);
				reapplyRibbonIcons();
				refreshAll();
			}
		});
	}

	/**
	 * Stores a new global UI font scale and applies it immediately.
	 * <p>
	 * The {@code -Ddocear.ui.fontscale=...} system property is updated as
	 * well: {@link UiFontScale#getUiFontScale()} prefers it over the stored
	 * preference, so leaving it in place would silently swallow the new value.
	 */
	public static void applyFontScale(final float scale) {
		ResourceController.getResourceController().setProperty(UiFontScale.FONT_SCALE_PROPERTY,
		    String.valueOf(scale));
		System.setProperty(UiFontScale.FONT_SCALE_PROPERTY, String.valueOf(scale));
		reapply();
	}

	/**
	 * Stores a new ribbon / top bar icon size and applies it immediately.
	 * Goes through the same refresh path as a font scale change, so an icon
	 * change can never leave the rest of the UI behind.
	 */
	public static void applyTopBarIconSize(final int iconSize) {
		ResourceController.getResourceController().setProperty(
		    RibbonActionContributorFactory.TOP_BAR_ICON_SIZE_PROPERTY, String.valueOf(iconSize));
		reapply();
	}

	/**
	 * Applies both appearance settings in one pass - one persist, one
	 * re-install, one refresh. This is what the "界面外观" dialog uses, so its
	 * two sliders can no longer end up in two different refresh paths.
	 */
	public static void applyAppearance(final float fontScale, final int iconSize) {
		ResourceController.getResourceController().setProperty(UiFontScale.FONT_SCALE_PROPERTY,
		    String.valueOf(fontScale));
		System.setProperty(UiFontScale.FONT_SCALE_PROPERTY, String.valueOf(fontScale));
		ResourceController.getResourceController().setProperty(
		    RibbonActionContributorFactory.TOP_BAR_ICON_SIZE_PROPERTY, String.valueOf(iconSize));
		reapply();
	}

	/**
	 * Re-installs the current look and feel (so the metrics are recalculated)
	 * and refreshes every existing window.
	 */
	public static void reapply() {
		if (SwingUtilities.isEventDispatchThread()) {
			reapplyOnEdt();
			return;
		}
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				reapplyOnEdt();
			}
		});
	}

	private static void reapplyOnEdt() {
		final long started = System.nanoTime();
		reinstallCurrentLookAndFeel();
		refreshAll();
		reapplyRibbonIcons();
		reapplyRibbonStripSizes();
		LogUtils.info("DocearUiInstaller: re-applied appearance with scale "
		    + DocearUiMetrics.fontScale() + " in " + (System.nanoTime() - started) / 1000000 + " ms");
	}

	/**
	 * Re-creates the current look and feel instance so that its
	 * {@code getDefaults()} runs again and produces a defaults table built
	 * from the unscaled base values.
	 * <p>
	 * Deliberately does <b>not</b> go through
	 * {@link FrameController#setLookAndFeel(String)}: that method also
	 * persists properties and starts the file chooser probe thread, none of
	 * which is wanted for a change that does not switch the look and feel.
	 */
	private static void reinstallCurrentLookAndFeel() {
		final LookAndFeel current = UIManager.getLookAndFeel();
		if (current == null) {
			return;
		}
		try {
			if (current instanceof DocearWin11LookAndFeel) {
				// cannot be resolved by class name: the bundle class loader is
				// not the one UIManager.setLookAndFeel(String) uses
				UIManager.setLookAndFeel(new DocearWin11LookAndFeel());
			}
			else {
				UIManager.setLookAndFeel(current.getClass().getName());
			}
			installMetrics();
			installRibbonUi();
		}
		catch (final Exception e) {
			LogUtils.warn("DocearUiInstaller: could not re-install " + current.getName(), e);
		}
	}

	/**
	 * Re-asserts the user configured ribbon icon size. Called after the
	 * component refresh, because {@code updateComponentTreeUI} rebuilds the
	 * ribbon's UI delegates and drops icon sizes that were set after
	 * creation.
	 */
	private static void reapplyRibbonIcons() {
		try {
			final Controller controller = Controller.getCurrentController();
			if (controller == null || controller.getModeController() == null) {
				return;
			}
			final RibbonBuilder ribbonBuilder = controller.getModeController().getUserInputListenerFactory()
			    .getRibbonBuilder();
			if (ribbonBuilder != null) {
				ribbonBuilder.reapplyTopBarScaling();
			}
		}
		catch (final RuntimeException e) {
			// no ribbon in the current mode (e.g. browse/file mode):
			// the setting stays persisted and is applied on the next rebuild
			LogUtils.warn("DocearUiInstaller: ribbon not available, icon size not re-applied", e);
		}
	}

	/**
	 * Re-asserts the strip icon size on every command button sitting in a
	 * {@link JCommandButtonStrip}, after the layout has run.
	 * <p>
	 * {@link RibbonBuilder#reapplyTopBarScaling()} already applies the strip
	 * size, but it is limited to the main ribbon and it cannot know the
	 * height the band finally granted. Walking the strips again here covers
	 * strips outside the ribbon (collapsed band popups) and lets
	 * {@link RibbonActionContributorFactory#applyTopBarStripScaling(AbstractCommandButton)}
	 * clamp the icon to the <i>actual</i> row height, which is only known
	 * once the component tree has been laid out.
	 * <p>
	 * Walks the component tree of every window that currently exists,
	 * including owned dialogs. The walk is bounded by the type check
	 * ({@code JCommandButtonStrip}); non-strip containers are recursed into
	 * only when they actually hold children. Runs on the EDT - same as the
	 * other refresh steps.
	 */
	private static void reapplyRibbonStripSizes() {
		for (final Window window : Window.getWindows()) {
			if (window != null && window.isDisplayable()) {
				refreshRibbonStripsIn(window);
			}
		}
	}

	private static void refreshRibbonStripsIn(final Container root) {
		for (final Component child : root.getComponents()) {
			if (child instanceof JCommandButtonStrip) {
				final JCommandButtonStrip strip = (JCommandButtonStrip) child;
				for (int i = 0; i < strip.getButtonCount(); i++) {
					RibbonActionContributorFactory.applyTopBarStripScaling(strip.getButton(i));
				}
				strip.invalidate();
				strip.validate();
				strip.repaint();
			}
			else if (child instanceof Container) {
				refreshRibbonStripsIn((Container) child);
			}
		}
	}

	// --------------------------------------------------------------- refresh

	/**
	 * Refreshes one window: rebuilds the UI delegates of its whole component
	 * tree, then revalidates and repaints it.
	 * <p>
	 * Always targets a single window. There is no "refresh everything by
	 * scanning components" call and no per component refresh: a partial
	 * refresh is what produced the mixed state this class removes.
	 *
	 * @param window the window to refresh, may be null
	 */
	public static void refresh(final Window window) {
		if (window == null || !window.isDisplayable()) {
			return;
		}
		final Map<JTextComponent, Integer> carets = new IdentityHashMap<JTextComponent, Integer>();
		collectCaretPositions(window, carets);
		SwingUtilities.updateComponentTreeUI(window);
		window.invalidate();
		window.validate();
		window.repaint();
		restoreCaretPositions(carets);
	}

	/**
	 * Refreshes every window that currently exists. Still window based: it
	 * walks {@link Window#getWindows()} (which includes owned dialogs) and
	 * calls {@link #refresh(Window)} for each of them.
	 */
	public static void refreshAll() {
		final Window[] windows = Window.getWindows();
		for (int i = 0; i < windows.length; i++) {
			refresh(windows[i]);
		}
	}

	// ---------------------------------------------------------- caret saving

	/**
	 * Collects the caret position of every text component below {@code c}.
	 * Installing a text UI re-creates the caret and resets it to 0, which was
	 * the only component state a refresh was measured to lose.
	 */
	private static void collectCaretPositions(final Component c, final Map<JTextComponent, Integer> carets) {
		if (c instanceof JTextComponent) {
			final JTextComponent text = (JTextComponent) c;
			carets.put(text, Integer.valueOf(text.getCaretPosition()));
		}
		if (c instanceof Container) {
			final Component[] children = ((Container) c).getComponents();
			for (int i = 0; i < children.length; i++) {
				collectCaretPositions(children[i], carets);
			}
		}
	}

	private static void restoreCaretPositions(final Map<JTextComponent, Integer> carets) {
		for (final Map.Entry<JTextComponent, Integer> entry : carets.entrySet()) {
			final JTextComponent text = entry.getKey();
			try {
				final int position = Math.min(entry.getValue().intValue(), text.getDocument().getLength());
				text.setCaretPosition(position);
			}
			catch (final IllegalArgumentException e) {
				// document was replaced while refreshing - nothing to restore
			}
		}
	}

	private DocearUiInstaller() {
	}
}
