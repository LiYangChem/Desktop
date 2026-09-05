package org.freeplane.core.ui.ribbon;

import java.util.List;

import javax.swing.JComponent;

import org.pushingpixels.flamingo.api.ribbon.resize.BaseRibbonBandResizePolicy;
import org.pushingpixels.flamingo.internal.ui.ribbon.JFlowBandControlPanel;

/**
 * Single row resize policy for {@link org.pushingpixels.flamingo.api.ribbon.JFlowRibbonBand}.
 * <p>
 * Flamingo 6.3 ships flow band resize policies for two
 * ({@code CoreRibbonResizePolicies.FlowTwoRows}) and three
 * ({@code CoreRibbonResizePolicies.FlowThreeRows}) rows only - there is no
 * policy that keeps every flow component on a single row. This one does.
 * <p>
 * How the ribbon uses it: {@code BasicRibbonUI.BandHostPanel.layoutContainer}
 * grants every band exactly the width its current resize policy asks for
 * ({@code currentResizePolicy.getPreferredWidth(availableHeight, gap)}), and
 * {@code BasicFlowBandControlPanelUI.FlowControlPanelLayout.layoutContainer}
 * starts a new row whenever a flow component does not fit the width the band
 * was granted. Asking for the width of all components side by side therefore
 * means the band is granted that width (as long as the ribbon has room) and
 * the flow layout never wraps: one row, and the band is exactly as wide as its
 * content needs.
 * <p>
 * Registered as the <b>first</b> (most permissive) policy, with
 * {@code FlowTwoRows} and {@code IconRibbonBandResizePolicy} behind it. When
 * the ribbon runs out of width it steps bands down one policy at a time, so a
 * narrow window degrades gracefully: two rows with
 * {@code FlowTwoRows}' width (which by definition fits both rows - no
 * overflow), then the collapsed icon.
 * <p>
 * {@link FlamingoUtilities#checkResizePoliciesConsistency} requires the
 * policies of a band to be ordered by decreasing preferred width; the one row
 * width is always the largest possible flow width, so this policy can only be
 * the first entry of the list.
 */
public class FlowOneRowResizePolicy extends BaseRibbonBandResizePolicy<JFlowBandControlPanel> {

	public FlowOneRowResizePolicy(final JFlowBandControlPanel controlPanel) {
		super(controlPanel);
	}

	/**
	 * The width all flow components need when laid out side by side: the sum
	 * of their preferred widths plus one gap each - the same sum
	 * {@code FlowTwoRows} starts from before it optimizes for two rows.
	 */
	@Override
	public int getPreferredWidth(final int availableHeight, final int gap) {
		final List<JComponent> components = controlPanel.getFlowComponents();
		int width = 0;
		for (int i = 0; i < components.size(); i++) {
			width += components.get(i).getPreferredSize().width + gap;
		}
		return width;
	}

	/**
	 * Nothing to install: the flow layout derives its row count from the
	 * granted width alone, and this policy does not change any component.
	 */
	@Override
	public void install(final int availableHeight, final int gap) {
	}
}
