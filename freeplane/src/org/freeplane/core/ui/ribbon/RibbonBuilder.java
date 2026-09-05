package org.freeplane.core.ui.ribbon;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.components.OneTouchCollapseResizer;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.ui.ribbon.StructureTree.StructurePath;
import org.freeplane.core.ui.ribbon.event.AboutToPerformEvent;
import org.freeplane.core.ui.ribbon.event.IActionEventListener;
import org.freeplane.core.ui.ribbon.special.EdgeStyleContributorFactory;
import org.freeplane.core.ui.ribbon.special.FilterConditionsContributorFactory;
import org.freeplane.core.ui.ribbon.special.FontStyleContributorFactory;
import org.freeplane.core.ui.ribbon.special.ViewSettingsContributorFactory;
import org.freeplane.core.ui.ribbon.special.ZoomContributorFactory;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.ModeController;
import org.pushingpixels.flamingo.api.common.AbstractCommandButton;
import org.pushingpixels.flamingo.api.common.JCommandButtonStrip;
import org.pushingpixels.flamingo.api.common.icon.ImageWrapperResizableIcon;
import org.pushingpixels.flamingo.api.common.icon.ResizableIcon;
import org.pushingpixels.flamingo.api.ribbon.JRibbon;
import org.pushingpixels.flamingo.internal.ui.ribbon.appmenu.JRibbonApplicationMenuButton;


public class RibbonBuilder {
	
	private final HashMap<String, IRibbonContributorFactory> contributorFactories = new HashMap<String, IRibbonContributorFactory>();
	
	final StructureTree structure;
	private final RootContributor rootContributor;
	private final RibbonStructureReader reader;
	private final JRibbon ribbon;
	private final ModeController mode;

	private final RibbonAcceleratorManager accelManager;

	private boolean enabled = true;

	private RibbonMapChangeAdapter changeAdapter;

	private RibbonActionEventHandler raeHandler;
	
	public RibbonBuilder(ModeController mode, JRibbon ribbon) {
		structure = new StructureTree();
		// NOTE: the ribbon UI delegate (DocearRibbonUI, which scales the task
		// tab row and the taskbar with the global font scale) is installed via
		// the UIManager key "RibbonUI" in UiFontScale.applyGlobalFontScale(),
		// because JRibbon already created its UI in its own constructor and
		// JComponent.setUI() is protected.
		this.rootContributor = new RootContributor(ribbon);
		this.ribbon = ribbon;
		this.mode = mode;
		reader = new RibbonStructureReader(this);
		accelManager = new RibbonAcceleratorManager(this);
		registerContributorFactory("separator", new RibbonSeparatorContributorFactory());
		registerContributorFactory("ribbon_menu", new RibbonMenuContributorFactory());
		registerContributorFactory("ribbon_taskbar", new RibbonTaskbarContributorFactory());
		registerContributorFactory("primary_entry", new RibbonMenuPrimaryContributorFactory(this));
		registerContributorFactory("entry_group", new RibbonMenuSecondaryGroupContributorFactory());
		registerContributorFactory("footer_entry", new RibbonMenuFooterContributorFactory(this));
		registerContributorFactory("ribbon_task", new RibbonTaskContributorFactory());
		registerContributorFactory("ribbon_band", new RibbonBandContributorFactory());
		registerContributorFactory("ribbon_action", new RibbonActionContributorFactory(this));
		registerContributorFactory("fontStyleContributor", new FontStyleContributorFactory());
		registerContributorFactory("edgeStyleContributor", new EdgeStyleContributorFactory());		
		registerContributorFactory("ribbon_flowband", new FlowRibbonBandContributorFactory());
		
		registerContributorFactory("zoomContributor", new ZoomContributorFactory(this));		
		registerContributorFactory("viewSettingsContributor", new ViewSettingsContributorFactory());
		registerContributorFactory("filterConditionsContributor", new FilterConditionsContributorFactory());
		
		updateApplicationMenuButton(ribbon);
	}

	public void updateApplicationMenuButton(JRibbon ribbon) {
		for(Component comp : ribbon.getComponents()) {
			if(comp instanceof JRibbonApplicationMenuButton) {
				String appName = ResourceController.getResourceController().getProperty("ApplicationName", "Freeplane");
				URL location = ResourceController.getResourceController().getResource("/images/"+appName.trim()+"_app_menu_128.png");
				if (location != null) {
					ResizableIcon icon = ImageWrapperResizableIcon.getIcon(location, new Dimension(32, 32));
					((JRibbonApplicationMenuButton) comp).setIcon(icon);
					((JRibbonApplicationMenuButton) comp).setBackground(Color.blue);
				}
			}
		}
	}
	
	public void add(ARibbonContributor contributor, StructurePath path, int position) {
		if(contributor == null || path == null) {
			throw new IllegalArgumentException("NULL");
		}
		synchronized (structure) {
			structure.insert(path, contributor, position);
		}
	}
	
	public void registerContributorFactory(String key, IRibbonContributorFactory factory) {
		synchronized (contributorFactories) {
			this.contributorFactories.put(key, factory);
		}

	}
	
	public IRibbonContributorFactory getContributorFactory(String key) {
		return this.contributorFactories.get(key);
	}
	
	public void buildRibbon() {
		Window f = SwingUtilities.getWindowAncestor(ribbon);
		if(!isEnabled()) {
			return;
		}
		// font scaling is global: UiFontScale.applyGlobalFontScale() ran at
		// startup (FreeplaneGUIStarter) right after the L&F installation, so
		// Flamingo's internal dummy command buttons (which anchor the band
		// control panel heights) already pick up the scaled L&F fonts here.

		try {
			getAcceleratorManager().loadAcceleratorPresets(new FileInputStream(getAcceleratorManager().getPresetsFile()));
		}
		catch (IOException ex) {
		}
		
		getMapChangeAdapter().clear();
		synchronized (structure) {
			final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
			try {
				rootContributor.contribute(new RibbonBuildContext(this), null);
			}
			finally {
				Thread.currentThread().setContextClassLoader(contextClassLoader);
			}
		}
		applyRibbonScaling(ribbon);
		f.setMinimumSize(new Dimension(640,240));
		f.pack();

	}

	/**
	 * Re-applies the user configurable icon size (see
	 * RibbonActionContributorFactory) to the whole ribbon without rebuilding
	 * it. Safe to call from any thread.
	 * <p>
	 * This is <b>one step</b> of the unified refresh, not a refresh of its
	 * own: it only covers the ribbon icon dimension, so calling it on its own
	 * would leave fonts, insets and every other window behind. The single
	 * entry point that runs the complete refresh (metrics -&gt; component
	 * tree -&gt; ribbon icons) is
	 * {@link org.freeplane.core.ui.DocearUiInstaller#reapply()}, which calls
	 * this method last because {@code updateComponentTreeUI} rebuilds the
	 * ribbon's UI delegates.
	 */
	public void reapplyTopBarScaling() {
		if (SwingUtilities.isEventDispatchThread()) {
			applyTopBarScalingChanges();
		}
		else {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					applyTopBarScalingChanges();
				}
			});
		}
	}

	private void applyTopBarScalingChanges() {
		// icon dimensions only - the fonts follow the global L&F scaling
		// which is installed at startup and read at UI delegate creation
		applyRibbonScaling(ribbon);
		ribbon.revalidate();
		ribbon.repaint();
	}

	/**
	 * Top menu bar (ribbon) only: re-asserts the user configured icon size on
	 * every command button (contributors may override the display state
	 * after creation). Fonts are NOT touched here - they follow the global
	 * L&F font scale (see UiFontScale). The application menu button is left
	 * untouched.
	 */
	private void applyRibbonScaling(final Component component) {
		if (component == null) {
			return;
		}
		if (component instanceof JRibbonApplicationMenuButton) {
			return;
		}
		if (component instanceof JCommandButtonStrip) {
			// a strip button must keep its own (smaller) icon size: the
			// plain top bar size does not fit the strip row and its icon
			// would be clipped again on every refresh
			final JCommandButtonStrip strip = (JCommandButtonStrip) component;
			for (int i = 0; i < strip.getButtonCount(); i++) {
				RibbonActionContributorFactory.applyTopBarStripScaling(strip.getButton(i));
			}
			return;
		}
		if (component instanceof AbstractCommandButton) {
			RibbonActionContributorFactory.applyTopBarScaling((AbstractCommandButton) component);
		}
		if (component instanceof Container) {
			final Component[] children = ((Container) component).getComponents();
			for (final Component child : children) {
				applyRibbonScaling(child);
			}
		}
	}
	
	public boolean isEnabled() {
		return enabled ;
	}
	
	public void setEnabled(boolean b) {
		enabled = b;
	}
	
	public void setMinimized(boolean b) {
		ribbon.setMinimized(b);
		OneTouchCollapseResizer otcr = OneTouchCollapseResizer.findResizerFor(ribbon);
		if(otcr != null) {
			otcr.recalibrate();
		}
	}
	
	public boolean isMinimized() {
		return ribbon.isMinimized();
	}
	
	public void updateRibbon(URL xmlResource) {
		//final URL xmlSource = ResourceController.getResourceController().getResource(xmlResource);
		if (xmlResource != null) {
			final boolean isUserDefined = xmlResource.getProtocol().equalsIgnoreCase("file");
			try{
				reader.loadStructure(xmlResource);
			}
			catch (RuntimeException e){
				if(isUserDefined){
					LogUtils.warn(e);
					String myMessage = TextUtils.format("ribbon_error", xmlResource.getPath(), e.getMessage());
					UITools.backOtherWindows();
					JOptionPane.showMessageDialog(UITools.getFrame(), myMessage, "Freeplane", JOptionPane.ERROR_MESSAGE);
					System.exit(-1);
				}
				throw e;
			}
		}
	}

	public boolean containsPath(StructurePath path) {
		synchronized (structure) {
			return structure.contains(path);
		}		
	}
	
	public ModeController getMode() {
		return mode;
	}

	public RibbonAcceleratorManager getAcceleratorManager() {
		return accelManager;
	}
	
	public RibbonMapChangeAdapter getMapChangeAdapter() {
		if(changeAdapter == null) {
			changeAdapter = new RibbonMapChangeAdapter();
		}
		return changeAdapter;
	}

	public RibbonActionEventHandler getRibbonActionEventHandler() {
		if(raeHandler == null) {
			raeHandler = new RibbonActionEventHandler();
		}
		return raeHandler;
	}
	
	public static class RibbonActionEventHandler {
		
		private List<IActionEventListener> listeners = new ArrayList<IActionEventListener>();

		public void fireAboutToPerformEvent(AboutToPerformEvent event) {
    		synchronized (listeners) {
    			IActionEventListener[] aListeners = listeners.toArray(new IActionEventListener[0]);
    			for(int i=aListeners.length-1; i >= 0; i--) {
    				aListeners[i].aboutToPerform(event);
    			}
 			}	
		}
		
		public void addListener(IActionEventListener listener) {
			synchronized (listeners) {
				if(!listeners.contains(listener)) {
					listeners.add(listener);
				}
			}
		}
		
		public void removeListener(IActionEventListener listener) {
			synchronized (listeners) {
				listeners.remove(listener);
			}
		}

	}

	public JRibbon getRibbonRootComponent() {
		return ribbon;
	}

	

}
