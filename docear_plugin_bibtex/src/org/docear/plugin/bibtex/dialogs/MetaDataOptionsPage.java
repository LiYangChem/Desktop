package org.docear.plugin.bibtex.dialogs;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import org.docear.metadata.net.MetadataNetworkConfig;
import org.docear.plugin.core.actions.OpenLogsFolderAction;
import org.docear.plugin.core.ui.MultiLineActionLabel;
import org.docear.plugin.core.ui.wizard.AWizardPage;
import org.docear.plugin.core.ui.wizard.WizardSession;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.Controller;

import com.jgoodies.forms.factories.FormFactory;
import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.RowSpec;

public class MetaDataOptionsPage extends AWizardPage {
	
	public static final String DOCEAR_METADATA_MAX_RESULT = "docear_metadata_maxResult";
	public static final String DOCEAR_METADATA_DEBUG_LOGGING = "docear_metadata_debugLogging";
	public static final String DOCEAR_METADATA_SEARCH_DOCEAR = "docear_metadata_searchDocear";
	public static final String DOCEAR_METADATA_SEARCH_SCHOLAR = "docear_metadata_searchScholar";
	public static final String DOCEAR_METADATA_SEARCH_CROSSREF = "docear_metadata_searchCrossref";
	public static final String DOCEAR_METADATA_SEARCH_DOI = "docear_metadata_searchDoi";
	public static final String DOCEAR_METADATA_PROXY_MODE = "docear_metadata_proxyMode";
	public static final String DOCEAR_METADATA_PROXY_HOST = "docear_metadata_proxyHost";
	public static final String DOCEAR_METADATA_PROXY_PORT = "docear_metadata_proxyPort";
	private static final long serialVersionUID = 1L;
	private JCheckBox checkBoxScholar;
	private JCheckBox checkBoxCrossref;
	private JCheckBox checkBoxDoi;
	private JCheckBox checkBoxDocear;
	private JSpinner spinnerMaxResult;
	private JCheckBox checkBoxLogging;
	private JRadioButton radioProxySystem;
	private JRadioButton radioProxyNone;
	private JRadioButton radioProxyCustom;
	private JTextField fieldProxyHost;
	private JTextField fieldProxyPort;
	
	public MetaDataOptionsPage() {
		setBackground(Color.WHITE);
		setLayout(new FormLayout(new ColumnSpec[] {
				FormFactory.RELATED_GAP_COLSPEC,
				ColumnSpec.decode("default:grow"),
				FormFactory.RELATED_GAP_COLSPEC,},
			new RowSpec[] {
				FormFactory.RELATED_GAP_ROWSPEC,
				FormFactory.DEFAULT_ROWSPEC,
				FormFactory.RELATED_GAP_ROWSPEC,
				RowSpec.decode("default:grow"),
				FormFactory.RELATED_GAP_ROWSPEC,
				FormFactory.DEFAULT_ROWSPEC,
				FormFactory.RELATED_GAP_ROWSPEC,
				RowSpec.decode("default:grow"),
				FormFactory.RELATED_GAP_ROWSPEC,
				FormFactory.DEFAULT_ROWSPEC,
				FormFactory.RELATED_GAP_ROWSPEC,
				RowSpec.decode("default:grow"),
				FormFactory.RELATED_GAP_ROWSPEC,}));
		
		JLabel labelSources = new JLabel(TextUtils.getText("docear.metadata.extraction.sources.title"));
		add(labelSources, "2, 2");
		
		JScrollPane scrollPane = new JScrollPane();
		add(scrollPane, "2, 4, fill, fill");
		
		JPanel panel = new JPanel();
		panel.setBackground(Color.WHITE);
		scrollPane.setViewportView(panel);
		panel.setLayout(new FormLayout(new ColumnSpec[] {
				FormFactory.RELATED_GAP_COLSPEC,
				ColumnSpec.decode("default:grow"),
				FormFactory.RELATED_GAP_COLSPEC,
				ColumnSpec.decode("default:grow"),
				FormFactory.RELATED_GAP_COLSPEC,
				ColumnSpec.decode("default:grow"),
				FormFactory.RELATED_GAP_COLSPEC,
				ColumnSpec.decode("default:grow"),
				FormFactory.RELATED_GAP_COLSPEC,},
			new RowSpec[] {
				FormFactory.RELATED_GAP_ROWSPEC,
				FormFactory.DEFAULT_ROWSPEC,
				FormFactory.RELATED_GAP_ROWSPEC,}));
		
		checkBoxScholar = new JCheckBox(TextUtils.getText("docear.metadata.extraction.sources.scholar"));
		checkBoxScholar.setBackground(Color.WHITE);
		panel.add(checkBoxScholar, "2, 2");
		
		checkBoxCrossref = new JCheckBox(TextUtils.getText("docear.metadata.extraction.sources.crossref"));
		checkBoxCrossref.setBackground(Color.WHITE);
		panel.add(checkBoxCrossref, "4, 2");
		
		checkBoxDoi = new JCheckBox(TextUtils.getText("docear.metadata.extraction.sources.doi"));
		checkBoxDoi.setBackground(Color.WHITE);
		panel.add(checkBoxDoi, "6, 2");
		
		checkBoxDocear = new JCheckBox(TextUtils.getText("docear.metadata.extraction.sources.docear"));
		checkBoxDocear.setBackground(Color.WHITE);
		panel.add(checkBoxDocear, "8, 2");
		
		JLabel labelSearchOptions = new JLabel(TextUtils.getText("docear.metadata.extraction.options.title"));
		add(labelSearchOptions, "2, 6");
		
		JScrollPane scrollPane_1 = new JScrollPane();
		add(scrollPane_1, "2, 8, fill, fill");
		
		JPanel panel_1 = new JPanel();
		panel_1.setBackground(Color.WHITE);
		scrollPane_1.setViewportView(panel_1);
		panel_1.setLayout(new FormLayout(new ColumnSpec[] {
				FormFactory.RELATED_GAP_COLSPEC,
				ColumnSpec.decode("default:grow"),
				FormFactory.RELATED_GAP_COLSPEC,
				ColumnSpec.decode("default:grow"),
				FormFactory.RELATED_GAP_COLSPEC,},
			new RowSpec[] {
				FormFactory.RELATED_GAP_ROWSPEC,
				FormFactory.DEFAULT_ROWSPEC,
				FormFactory.RELATED_GAP_ROWSPEC,
				FormFactory.DEFAULT_ROWSPEC,}));
		
		MultiLineActionLabel labelMaxResult = new MultiLineActionLabel(TextUtils.getText("docear.metadata.extraction.options.maxResult"));
		labelMaxResult.setBackground(Color.WHITE);
		panel_1.add(labelMaxResult, "2, 2, left, default");
		
		spinnerMaxResult = new JSpinner();
		spinnerMaxResult.setModel(new SpinnerNumberModel(3, 1, 50, 1));
		spinnerMaxResult.setBackground(Color.WHITE);
		panel_1.add(spinnerMaxResult, "4, 2");
		
		MultiLineActionLabel labelLogging = new MultiLineActionLabel(TextUtils.getText("docear.metadata.extraction.options.logging"));
		labelLogging.setHorizontalAlignment(0);
		labelLogging.setAlignmentX(Component.LEFT_ALIGNMENT);
		labelLogging.setBackground(Color.WHITE);		
		labelLogging.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {				
				if("logging_source".equals(e.getActionCommand())) {
					try {
						new OpenLogsFolderAction().actionPerformed(null);						
					}					
					catch (Exception ex) {
						LogUtils.warn(ex.getMessage());
					}
				}
			}
		});
		panel_1.add(labelLogging, "2, 4, left, default");
		
		checkBoxLogging = new JCheckBox("");
		checkBoxLogging.setBackground(Color.WHITE);
		checkBoxLogging.setHorizontalAlignment(SwingConstants.TRAILING);
		panel_1.add(checkBoxLogging, "4, 4");
		
		// network proxy section
		JLabel labelProxy = new JLabel(TextUtils.getText("docear.metadata.extraction.proxy.title"));
		add(labelProxy, "2, 10");
		
		JScrollPane scrollPane_2 = new JScrollPane();
		add(scrollPane_2, "2, 12, fill, fill");
		
		JPanel panel_2 = new JPanel();
		panel_2.setBackground(Color.WHITE);
		scrollPane_2.setViewportView(panel_2);
		panel_2.setLayout(new FormLayout(new ColumnSpec[] {
				FormFactory.RELATED_GAP_COLSPEC,
				ColumnSpec.decode("default:grow"),
				FormFactory.RELATED_GAP_COLSPEC,
				ColumnSpec.decode("default:grow"),
				FormFactory.RELATED_GAP_COLSPEC,
				ColumnSpec.decode("default:grow"),
				FormFactory.RELATED_GAP_COLSPEC,
				ColumnSpec.decode("default:grow"),
				FormFactory.RELATED_GAP_COLSPEC,
				ColumnSpec.decode("default:grow"),
				FormFactory.RELATED_GAP_COLSPEC,},
			new RowSpec[] {
				FormFactory.RELATED_GAP_ROWSPEC,
				FormFactory.DEFAULT_ROWSPEC,
				FormFactory.RELATED_GAP_ROWSPEC,
				FormFactory.DEFAULT_ROWSPEC,
				FormFactory.RELATED_GAP_ROWSPEC,}));
		
		radioProxySystem = new JRadioButton(TextUtils.getText("docear.metadata.extraction.proxy.system"));
		radioProxySystem.setBackground(Color.WHITE);
		panel_2.add(radioProxySystem, "2, 2");
		
		radioProxyNone = new JRadioButton(TextUtils.getText("docear.metadata.extraction.proxy.none"));
		radioProxyNone.setBackground(Color.WHITE);
		panel_2.add(radioProxyNone, "4, 2");
		
		radioProxyCustom = new JRadioButton(TextUtils.getText("docear.metadata.extraction.proxy.custom"));
		radioProxyCustom.setBackground(Color.WHITE);
		panel_2.add(radioProxyCustom, "6, 2");
		
		ButtonGroup proxyGroup = new ButtonGroup();
		proxyGroup.add(radioProxySystem);
		proxyGroup.add(radioProxyNone);
		proxyGroup.add(radioProxyCustom);
		
		JLabel labelProxyHost = new JLabel(TextUtils.getText("docear.metadata.extraction.proxy.host") + ":");
		labelProxyHost.setBackground(Color.WHITE);
		panel_2.add(labelProxyHost, "2, 4, right, default");
		
		fieldProxyHost = new JTextField();
		fieldProxyHost.setColumns(12);
		panel_2.add(fieldProxyHost, "4, 4, fill, default");
		
		JLabel labelProxyPort = new JLabel(TextUtils.getText("docear.metadata.extraction.proxy.port") + ":");
		labelProxyPort.setBackground(Color.WHITE);
		panel_2.add(labelProxyPort, "6, 4, right, default");
		
		fieldProxyPort = new JTextField();
		fieldProxyPort.setColumns(6);
		panel_2.add(fieldProxyPort, "8, 4, fill, default");
		
		radioProxyCustom.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				updateProxyFieldsEnabled();
			}
		});
	}	

	@Override
	public String getTitle() {		
		return TextUtils.getText("docear.metadata.extraction.options.dialogtitle");
	}

	@Override
	public void preparePage(WizardSession session) {
		session.setWizardTitle(getTitle());
		session.getBackButton().setVisible(true);
		getRootPane().setDefaultButton((JButton)session.getNextButton());
		session.getNextButton().setText(TextUtils.getText("ok"));
		session.getBackButton().setText(TextUtils.getText("cancel"));
		this.checkBoxDocear.setVisible(false);
		final ResourceController properties = Controller.getCurrentController().getResourceController();
		this.checkBoxScholar.setSelected(properties.getBooleanProperty(DOCEAR_METADATA_SEARCH_SCHOLAR));
		this.checkBoxCrossref.setSelected(properties.getBooleanProperty(DOCEAR_METADATA_SEARCH_CROSSREF));
		this.checkBoxDoi.setSelected(properties.getBooleanProperty(DOCEAR_METADATA_SEARCH_DOI));
		this.checkBoxDocear.setSelected(properties.getBooleanProperty(DOCEAR_METADATA_SEARCH_DOCEAR));
		this.spinnerMaxResult.setModel(new SpinnerNumberModel(properties.getIntProperty(DOCEAR_METADATA_MAX_RESULT, 3), 1, 50, 1));
		this.checkBoxLogging.setSelected(properties.getBooleanProperty(DOCEAR_METADATA_DEBUG_LOGGING));
		
		MetadataNetworkConfig.Mode proxyMode = MetadataNetworkConfig.parseMode(
				properties.getProperty(DOCEAR_METADATA_PROXY_MODE), MetadataNetworkConfig.DEFAULT_MODE);
		this.radioProxySystem.setSelected(MetadataNetworkConfig.Mode.SYSTEM.equals(proxyMode));
		this.radioProxyNone.setSelected(MetadataNetworkConfig.Mode.NONE.equals(proxyMode));
		this.radioProxyCustom.setSelected(MetadataNetworkConfig.Mode.CUSTOM.equals(proxyMode));
		this.fieldProxyHost.setText(properties.getProperty(DOCEAR_METADATA_PROXY_HOST, ""));
		this.fieldProxyPort.setText(properties.getProperty(DOCEAR_METADATA_PROXY_PORT, ""));
		updateProxyFieldsEnabled();
		
		session.getNextButton().addActionListener(new ActionListener() {			
			@Override
			public void actionPerformed(ActionEvent e) {
				properties.setProperty(DOCEAR_METADATA_SEARCH_SCHOLAR, checkBoxScholar.isSelected());
				properties.setProperty(DOCEAR_METADATA_SEARCH_CROSSREF, checkBoxCrossref.isSelected());
				properties.setProperty(DOCEAR_METADATA_SEARCH_DOI, checkBoxDoi.isSelected());
				properties.setProperty(DOCEAR_METADATA_SEARCH_DOCEAR, checkBoxDocear.isSelected());
				properties.setProperty(DOCEAR_METADATA_MAX_RESULT, spinnerMaxResult.getModel().getValue().toString());
				properties.setProperty(DOCEAR_METADATA_DEBUG_LOGGING, checkBoxLogging.isSelected());
				properties.setProperty(DOCEAR_METADATA_PROXY_MODE, selectedProxyMode());
				properties.setProperty(DOCEAR_METADATA_PROXY_HOST, fieldProxyHost.getText());
				properties.setProperty(DOCEAR_METADATA_PROXY_PORT, fieldProxyPort.getText());
			}
		});
	}

	private void updateProxyFieldsEnabled() {
		boolean custom = radioProxyCustom.isSelected();
		fieldProxyHost.setEnabled(custom);
		fieldProxyPort.setEnabled(custom);
	}

	private String selectedProxyMode() {
		if (radioProxySystem.isSelected()) {
			return MetadataNetworkConfig.Mode.SYSTEM.name();
		}
		if (radioProxyNone.isSelected()) {
			return MetadataNetworkConfig.Mode.NONE.name();
		}
		return MetadataNetworkConfig.Mode.CUSTOM.name();
	}

}
