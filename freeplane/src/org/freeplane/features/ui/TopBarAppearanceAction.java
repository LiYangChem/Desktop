package org.freeplane.features.ui;

import java.awt.event.ActionEvent;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.ui.ribbon.RibbonAppearanceDialog;

/**
 * Opens the "界面外观" dialog which lets the user configure the global UI
 * font scale (whole application, restart to apply) and the top menu bar
 * (ribbon) icon size (live).
 */
public class TopBarAppearanceAction extends AFreeplaneAction {

	private static final long serialVersionUID = 1L;

	public TopBarAppearanceAction() {
		super("TopBarAppearanceAction", "界面外观", null);
	}

	public void actionPerformed(final ActionEvent e) {
		final RibbonAppearanceDialog dialog = new RibbonAppearanceDialog(UITools.getFrame());
		dialog.setVisible(true);
	}
}
