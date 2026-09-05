package org.freeplane.core.ui.ribbon;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.DocearUiInstaller;
import org.freeplane.core.ui.UiFontScale;
import org.freeplane.core.util.LogUtils;

/**
 * "界面外观" dialog: one global UI font scale (in percent) and the ribbon
 * icon size (in pixels). Both values are persisted in user properties and
 * both are applied immediately through {@link DocearUiInstaller} - the single
 * install and refresh path - so no restart is needed for either of them.
 */
public class RibbonAppearanceDialog extends JDialog {
	private static final long serialVersionUID = 1L;

	private static final int FONT_SCALE_MIN_PERCENT = 100;
	private static final int FONT_SCALE_MAX_PERCENT = 250;
	private static final int ICON_SIZE_MIN = 16;
	private static final int ICON_SIZE_MAX = 48;
	private static final int ICON_SIZE_STEP = 8;

	private final JSlider fontScaleSlider;
	private final JSlider iconSizeSlider;
	private final JLabel fontPreviewLabel;
	private final JLabel iconPreviewLabel;
	private final Font fontPreviewBaseFont;
	private ImageIcon previewIconImage;

	public RibbonAppearanceDialog(final Frame owner) {
		super(owner, "界面外观", true);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		fontScaleSlider = new JSlider(FONT_SCALE_MIN_PERCENT, FONT_SCALE_MAX_PERCENT,
		    Math.round(UiFontScale.getUiFontScale() * 100f));
		fontScaleSlider.setMajorTickSpacing(25);
		fontScaleSlider.setMinorTickSpacing(5);
		fontScaleSlider.setPaintTicks(true);
		fontScaleSlider.setPaintLabels(true);

		iconSizeSlider = new JSlider(ICON_SIZE_MIN, ICON_SIZE_MAX,
		    RibbonActionContributorFactory.getTopBarIconSize());
		iconSizeSlider.setMajorTickSpacing(ICON_SIZE_STEP);
		iconSizeSlider.setMinorTickSpacing(ICON_SIZE_STEP);
		iconSizeSlider.setPaintTicks(true);
		iconSizeSlider.setPaintLabels(true);
		iconSizeSlider.setSnapToTicks(true);

		fontPreviewBaseFont = new JLabel().getFont();
		fontPreviewLabel = new JLabel("界面文字示例 Menu");
		iconPreviewLabel = new JLabel();
		iconPreviewLabel.setHorizontalAlignment(JLabel.CENTER);
		iconPreviewLabel.setPreferredSize(new Dimension(ICON_SIZE_MAX + 16, ICON_SIZE_MAX + 16));

		fontScaleSlider.addChangeListener(new javax.swing.event.ChangeListener() {
			public void stateChanged(final javax.swing.event.ChangeEvent e) {
				updatePreview();
			}
		});
		iconSizeSlider.addChangeListener(new javax.swing.event.ChangeListener() {
			public void stateChanged(final javax.swing.event.ChangeEvent e) {
				updatePreview();
			}
		});
		updatePreview();

		final JPanel content = new JPanel(new GridBagLayout());
		content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		final GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(4, 4, 4, 4);
		content.add(new JLabel("界面字体缩放："), gbc);
		gbc.gridx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;
		content.add(fontScaleSlider, gbc);
		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0.0;
		content.add(new JLabel("菜单栏图标尺寸："), gbc);
		gbc.gridx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;
		content.add(iconSizeSlider, gbc);

		final JPanel previewPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 8));
		previewPanel.setBorder(BorderFactory.createTitledBorder("预览"));
		previewPanel.add(fontPreviewLabel);
		previewPanel.add(iconPreviewLabel);
		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.gridwidth = 2;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;
		content.add(previewPanel, gbc);

		final JButton okButton = new JButton("确定");
		okButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				applyAndClose();
			}
		});
		final JButton resetButton = new JButton("恢复原始尺寸");
		resetButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				fontScaleSlider.setValue(FONT_SCALE_MIN_PERCENT);
				iconSizeSlider.setValue(ICON_SIZE_MIN);
			}
		});
		final JButton cancelButton = new JButton("取消");
		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				dispose();
			}
		});
		final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttonPanel.add(okButton);
		buttonPanel.add(resetButton);
		buttonPanel.add(cancelButton);
		gbc.gridy = 3;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0.0;
		content.add(buttonPanel, gbc);

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(content, BorderLayout.CENTER);
		pack();
		setResizable(false);
		setLocationRelativeTo(owner);
	}

	private void updatePreview() {
		final float scale = fontScaleSlider.getValue() / 100f;
		final int iconSize = iconSizeSlider.getValue();
		fontPreviewLabel.setFont(fontPreviewBaseFont.deriveFont(fontPreviewBaseFont.getSize2D() * scale));
		fontPreviewLabel.setText("界面文字示例 Menu (" + fontScaleSlider.getValue() + "%)");
		if (previewIconImage == null) {
			final URL iconUrl = ResourceController.getResourceController().getResource("/images/icons/idea.png");
			if (iconUrl != null) {
				previewIconImage = new ImageIcon(iconUrl);
			}
		}
		if (previewIconImage != null && previewIconImage.getImage() != null) {
			iconPreviewLabel.setIcon(new ImageIcon(previewIconImage.getImage().getScaledInstance(
			    iconSize, iconSize, java.awt.Image.SCALE_SMOOTH)));
		}
		iconPreviewLabel.setText(previewIconImage == null ? String.valueOf(iconSize) + " px" : null);
	}

	private void applyAndClose() {
		final float fontScale = fontScaleSlider.getValue() / 100f;
		final int iconSize = iconSizeSlider.getValue();
		// one entry point for both values: persist, re-install the look and
		// feel with the new metrics and refresh every window. No restart and
		// no icon-only refresh any more.
		try {
			DocearUiInstaller.applyAppearance(fontScale, iconSize);
		}
		catch (final RuntimeException ex) {
			// the settings stay persisted; the UI is refreshed on the next start
			LogUtils.warn("RibbonAppearanceDialog: could not re-apply the appearance", ex);
		}
		dispose();
	}
}
