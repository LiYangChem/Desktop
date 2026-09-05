package org.freeplane.core.ui.ribbon.special;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.swing.JComboBox;
import javax.swing.event.TreeSelectionEvent;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.ribbon.ARibbonContributor;
import org.freeplane.core.ui.ribbon.CurrentState;
import org.freeplane.core.ui.ribbon.IChangeObserver;
import org.freeplane.core.ui.ribbon.IRibbonContributorFactory;
import org.freeplane.core.ui.ribbon.FlowOneRowResizePolicy;
import org.freeplane.core.ui.ribbon.RibbonActionContributorFactory;
import org.freeplane.core.ui.ribbon.RibbonActionContributorFactory.ActionAcceleratorChangeListener;
import org.freeplane.core.ui.ribbon.RibbonActionContributorFactory.ActionChangeListener;
import org.freeplane.core.ui.ribbon.RibbonBuildContext;
import org.freeplane.core.ui.ribbon.RibbonBuilder;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.view.swing.map.MapViewController;
import org.pushingpixels.flamingo.api.common.AbstractCommandButton;
import org.pushingpixels.flamingo.api.common.CommandButtonDisplayState;
import org.pushingpixels.flamingo.api.common.JCommandButton;
import org.pushingpixels.flamingo.api.common.JCommandButtonStrip;
import org.pushingpixels.flamingo.api.ribbon.JFlowRibbonBand;
import org.pushingpixels.flamingo.api.ribbon.resize.CoreRibbonResizePolicies;
import org.pushingpixels.flamingo.api.ribbon.resize.IconRibbonBandResizePolicy;
import org.pushingpixels.flamingo.api.ribbon.resize.RibbonBandResizePolicy;

public class ZoomContributorFactory implements IRibbonContributorFactory {
	
	private ActionAcceleratorChangeListener changeListener;

	public ZoomContributorFactory(RibbonBuilder builder) {
		builder.getAcceleratorManager().addAcceleratorChangeListener(getAccelChangeListener());
	}
	

	protected ActionAcceleratorChangeListener getAccelChangeListener() {
		if(changeListener == null) {
			changeListener = new ActionAcceleratorChangeListener();
		}
		return changeListener;
	}

	public ARibbonContributor getContributor(final Properties attributes) {
		return new ARibbonContributor() {

			public String getKey() {
				return attributes.getProperty("name");
			}

			public void contribute(final RibbonBuildContext context, ARibbonContributor parent) {
				if (parent == null) {
					return;
				}				
				JFlowRibbonBand band = new JFlowRibbonBand(TextUtils.removeTranslateComment(TextUtils.getText("ribbon.band.zoom")), null, null);
				
				JComboBox zoomBox = ((MapViewController) Controller.getCurrentController().getMapViewManager()).createZoomBox();
				addDefaultToggleHandler(context,zoomBox);
				band.addFlowComponent(zoomBox);
				
				JCommandButtonStrip strip = new JCommandButtonStrip();

				AFreeplaneAction action = context.getBuilder().getMode().getAction("ZoomInAction");
				JCommandButton button = RibbonActionContributorFactory.createCommandButton(action);
				button.setDisplayState(CommandButtonDisplayState.SMALL);
				RibbonActionContributorFactory.applyTopBarStripScaling(button);
				getAccelChangeListener().addAction(action.getKey(), button);
				addDefaultToggleHandler(context, action, button);
				strip.add(button);

				action = context.getBuilder().getMode().getAction("ZoomOutAction");
				button = RibbonActionContributorFactory.createCommandButton(action);
				button.setDisplayState(CommandButtonDisplayState.SMALL);
				RibbonActionContributorFactory.applyTopBarStripScaling(button);
				getAccelChangeListener().addAction(action.getKey(), button);
				addDefaultToggleHandler(context, action, button);
				strip.add(button);

				action = context.getBuilder().getMode().getAction("FitToPage");
				button = RibbonActionContributorFactory.createCommandButton(action);
				// Docear: this button used to be MEDIUM, i.e. icon + label. At
				// larger font scales the label alone was wider than the band
				// the ribbon grants to the zoom band, so the strip overflowed
				// the band (its buttons were laid out at negative x) or the
				// strip was pushed to its own row and clipped. Keeping all
				// three buttons SMALL keeps the zoom controls on one row.
				button.setDisplayState(CommandButtonDisplayState.SMALL);
				RibbonActionContributorFactory.applyTopBarStripScaling(button);
				getAccelChangeListener().addAction(action.getKey(), button);
				addDefaultToggleHandler(context, action, button);
				strip.add(button);

				band.addFlowComponent(strip);
				
				List<RibbonBandResizePolicy> policies = new ArrayList<RibbonBandResizePolicy>();
				// Docear: keep the zoom controls on one row and make the band
				// exactly as wide as its content needs. Flamingo has no
				// single-row flow policy, so FlowOneRowResizePolicy asks for
				// the side by side width of zoom box + button strip; when the
				// ribbon runs out of width the band degrades to FlowTwoRows
				// (two rows, but the policy width always fits them - no
				// overflow) and then to the collapsed icon.
				policies.add(new FlowOneRowResizePolicy(band.getControlPanel()));
				policies.add(new CoreRibbonResizePolicies.FlowTwoRows(band.getControlPanel()));
				policies.add(new IconRibbonBandResizePolicy(band.getControlPanel()));
				band.setResizePolicies(policies);
				
				parent.addChild(band, new ChildProperties(parseOrderSettings(attributes.getProperty("orderPriority", ""))));		    	
			}

			public void addChild(Object child, ChildProperties properties) {
			}
		};
	}
	
	private void addDefaultToggleHandler(final RibbonBuildContext context, final Component component) {
		context.getBuilder().getMapChangeAdapter().addListener(new IChangeObserver() {
			public void updateState(CurrentState state) {
				if(state.isNodeChangeEvent()) {					
				}
				else if(state.allMapsClosed()) {					
					component.setEnabled(false);
				}
				else if (state.get(TreeSelectionEvent.class) == null) {
					component.setEnabled(true);
				}
			}
		});
	}
	
	private void addDefaultToggleHandler(final RibbonBuildContext context, final AFreeplaneAction action, final AbstractCommandButton button) {		
		context.getBuilder().getMapChangeAdapter().addListener(new ActionChangeListener(action, button));
	}	
}
