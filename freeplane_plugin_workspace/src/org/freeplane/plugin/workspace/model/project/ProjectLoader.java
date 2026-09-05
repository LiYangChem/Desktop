package org.freeplane.plugin.workspace.model.project;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URI;

import org.apache.commons.io.IOExceptionWithCause;
import org.freeplane.core.io.ReadManager;
import org.freeplane.core.io.WriteManager;
import org.freeplane.core.io.xml.TreeXmlReader;
import org.freeplane.core.util.LogUtils;
import org.freeplane.n3.nanoxml.XMLException;
import org.freeplane.plugin.workspace.URIUtils;
import org.freeplane.plugin.workspace.creator.ActionCreator;
import org.freeplane.plugin.workspace.creator.FolderCreator;
import org.freeplane.plugin.workspace.creator.FolderTypePhysicalCreator;
import org.freeplane.plugin.workspace.creator.FolderTypeVirtualCreator;
import org.freeplane.plugin.workspace.creator.LinkCreator;
import org.freeplane.plugin.workspace.creator.LinkTypeFileCreator;
import org.freeplane.plugin.workspace.creator.ProjectRootCreator;
import org.freeplane.plugin.workspace.io.IProjectSettingsIOHandler;
import org.freeplane.plugin.workspace.io.xml.ProjectNodeWriter;
import org.freeplane.plugin.workspace.io.xml.ProjectSettingsWriter;
import org.freeplane.plugin.workspace.model.AWorkspaceNodeCreator;
import org.freeplane.plugin.workspace.model.AWorkspaceTreeNode;
import org.freeplane.plugin.workspace.model.IResultProcessor;
import org.freeplane.plugin.workspace.nodes.ProjectRootNode;

public class ProjectLoader implements IProjectSettingsIOHandler {
	private final ReadManager readManager;
	private final WriteManager writeManager;

	public final static int WSNODE_FOLDER = 1;
	public final static int WSNODE_LINK = 2;
	public final static int WSNODE_ACTION = 4;

	public final static String PROJECT_SETTINGS_FILE_NAME = "settings.xml";

	private FolderCreator folderCreator = null;
	private LinkCreator linkCreator = null;
	private ActionCreator actionCreator = null;
	private ProjectRootCreator projectRootCreator = null;
	
	private ProjectSettingsWriter projectWriter;
	private IResultProcessor resultProcessor;
		
	public ProjectLoader() {
		this.readManager = new ReadManager();
		this.writeManager = new WriteManager();
		this.projectWriter = new ProjectSettingsWriter(writeManager);
		
		initReadManager();
		initWriteManager();
	}
	
	private void initReadManager() {
		readManager.addElementHandler("workspace", getProjectRootCreator());
		readManager.addElementHandler("project", getProjectRootCreator());
		readManager.addElementHandler("folder", getFolderCreator());
		readManager.addElementHandler("link", getLinkCreator());
		readManager.addElementHandler("action", getActionCreator());

		registerTypeCreator(ProjectLoader.WSNODE_FOLDER, "virtual", new FolderTypeVirtualCreator());
		registerTypeCreator(ProjectLoader.WSNODE_FOLDER, "physical", new FolderTypePhysicalCreator());
		registerTypeCreator(ProjectLoader.WSNODE_LINK, "file", new LinkTypeFileCreator());
	}

	private void initWriteManager() {
		ProjectNodeWriter writer = new ProjectNodeWriter();
		writeManager.addElementWriter("project", writer);
		writeManager.addAttributeWriter("project", writer);

		writeManager.addElementWriter("folder", writer);
		writeManager.addAttributeWriter("folder", writer);

		writeManager.addElementWriter("link", writer);
		writeManager.addAttributeWriter("link", writer);
		
		writeManager.addElementWriter("action", writer);
		writeManager.addAttributeWriter("action", writer);
	}

	protected ProjectRootCreator getProjectRootCreator() {
		if (this.projectRootCreator == null) {
			this.projectRootCreator = new ProjectRootCreator();
			this.projectRootCreator.setResultProcessor(getDefaultResultProcessor());
		}
		return this.projectRootCreator;
	}

	private FolderCreator getFolderCreator() {
		if (this.folderCreator == null) {
			this.folderCreator = new FolderCreator();
			this.folderCreator.setResultProcessor(getDefaultResultProcessor());
		}
		return this.folderCreator;
	}

	private ActionCreator getActionCreator() {
		if (this.actionCreator == null) {
			this.actionCreator = new ActionCreator();
			this.actionCreator.setResultProcessor(getDefaultResultProcessor());
		}
		return this.actionCreator;
	}
	
	private LinkCreator getLinkCreator() {
		if (this.linkCreator == null) {
			this.linkCreator = new LinkCreator();
			this.linkCreator.setResultProcessor(getDefaultResultProcessor());
		}
		return this.linkCreator;
	}

	public void registerTypeCreator(final int nodeType, final String typeName, final AWorkspaceNodeCreator creator) {
		if (typeName == null || typeName.trim().length() <= 0)
			return;
		switch (nodeType) {
			case WSNODE_FOLDER: {
				getFolderCreator().addTypeCreator(typeName, creator);
				break;
			}
			case WSNODE_LINK: {
				getLinkCreator().addTypeCreator(typeName, creator);
				break;
			}
			case WSNODE_ACTION: {
				getActionCreator().addTypeCreator(typeName, creator);
				break;
			}
			default: {
				throw new IllegalArgumentException("not allowed argument for nodeType. Use only WorkspaceConfiguration.WSNODE_ACTION, WorkspaceConfiguration.WSNODE_FOLDER or WorkspaceConfiguration.WSNODE_LINK.");
			}
		}
		if(creator.getResultProcessor() == null) {
			creator.setResultProcessor(getDefaultResultProcessor());
		}

	}

	protected void load(final URI xmlFile) throws MalformedURLException, XMLException, IOException {
		final TreeXmlReader reader = new TreeXmlReader(readManager);
		reader.load(new InputStreamReader(new BufferedInputStream(xmlFile.toURL().openStream())));
	}
	
	public synchronized LOAD_RETURN_TYPE loadProject(AWorkspaceProject project) throws IOException {
		try {
			File projectSettings = new File(URIUtils.getAbsoluteFile(project.getProjectDataPath()), PROJECT_SETTINGS_FILE_NAME);
			if(projectSettings.exists()) {
				if(projectSettings.length() > 0) {
					getDefaultResultProcessor().setProject(project);
					this.load(projectSettings.toURI());
					if(project.getModel().getRoot() != null) {
						project.setLoaded();
						return LOAD_RETURN_TYPE.EXISTING_PROJECT;
					}
					LogUtils.warn("project settings of '" + project.getProjectName() + "' contain no project root: " + projectSettings);
				}
				else {
					LogUtils.warn("project settings of '" + project.getProjectName() + "' are empty (0 bytes): " + projectSettings);
				}
				quarantineProjectSettings(projectSettings);
			}
			createDefaultProject(project);
			project.setLoaded();
			return LOAD_RETURN_TYPE.NEW_PROJECT;
		}
		catch (Exception e) {
			throw new IOExceptionWithCause(e);
		}
	}

	/**
	 * Moves an unusable settings.xml out of the way so that the project can be rebuilt from scratch. The file is
	 * renamed (not deleted) to keep the evidence; if renaming is not possible it is deleted as a last resort.
	 * 
	 * @return the quarantine file or <code>null</code> if the settings file could not be moved.
	 */
	protected File quarantineProjectSettings(final File projectSettings) {
		final File parent = projectSettings.getParentFile();
		File quarantine = new File(parent, PROJECT_SETTINGS_FILE_NAME + ".corrupt-" + System.currentTimeMillis());
		int counter = 1;
		while(quarantine.exists()) {
			quarantine = new File(parent, PROJECT_SETTINGS_FILE_NAME + ".corrupt-" + System.currentTimeMillis() + "-" + (counter++));
		}
		if(projectSettings.renameTo(quarantine)) {
			LogUtils.warn("moved corrupt project settings to: " + quarantine);
			return quarantine;
		}
		if(projectSettings.delete()) {
			LogUtils.warn("deleted corrupt project settings: " + projectSettings);
		}
		else {
			LogUtils.severe("could not remove corrupt project settings: " + projectSettings);
		}
		return null;
	}

	protected void createDefaultProject(AWorkspaceProject project) {
		ProjectRootNode root = new ProjectRootNode();
		root.setProjectID(project.getProjectID());				
		root.setModel(project.getModel());
		root.setName(URIUtils.getAbsoluteFile(project.getProjectHome()).getName());
		project.getModel().setRoot(root);
		// create and load all default nodes
		root.initiateMyFile(project);
		root.refresh();
	}
	
	public IResultProcessor getDefaultResultProcessor() {
		if(this.resultProcessor == null) {
			this.resultProcessor = new DefaultResultProcessor();
		}
		return this.resultProcessor;
	}

	private void storeProject(Writer writer, AWorkspaceProject project) throws IOException {
		this.projectWriter.storeProject(writer, project);		
	}

	/**
	 * Stores the project settings atomically:
	 * <ol>
	 * <li>the complete document is written to <code>settings.xml.tmp</code></li>
	 * <li>the previous <code>settings.xml</code> is copied to <code>settings.xml.bak</code></li>
	 * <li><code>settings.xml.tmp</code> is moved over <code>settings.xml</code></li>
	 * </ol>
	 * The former implementation opened <code>settings.xml</code> directly with a {@link FileWriter}, which truncates
	 * the file the moment it is opened. A crash or a killed JVM during the write therefore left a 0 byte (or half
	 * written) settings.xml behind; such a file silently parses to "no project root" and makes the workspace
	 * unusable. Writing through a temporary file keeps the old settings intact until the new ones are complete.
	 */
	public void storeProject(AWorkspaceProject project) throws IOException {
		final File dataDir = URIUtils.getAbsoluteFile(project.getProjectDataPath());
		if(!dataDir.exists() && !dataDir.mkdirs()) {
			throw new IOException("cannot create project data directory: " + dataDir);
		}
		final File outFile = new File(dataDir, PROJECT_SETTINGS_FILE_NAME);
		final File tmpFile = new File(dataDir, PROJECT_SETTINGS_FILE_NAME + ".tmp");
		final File bakFile = new File(dataDir, PROJECT_SETTINGS_FILE_NAME + ".bak");

		if(!tmpFile.exists() && !tmpFile.createNewFile()) {
			throw new IOException("cannot create temporary project settings file: " + tmpFile);
		}
		// 1) write the complete document into the temporary file
		boolean written = false;
		try {
			final Writer writer = new FileWriter(tmpFile);
			storeProject(writer, project);
			written = true;
		}
		finally {
			if(!written) {
				deleteQuietly(tmpFile);
			}
		}
		if(!written || tmpFile.length() <= 0) {
			deleteQuietly(tmpFile);
			throw new IOException("project settings were written empty - previous settings kept: " + outFile);
		}
		// 2) keep the last known good settings as a backup
		if(outFile.exists() && outFile.length() > 0) {
			deleteQuietly(bakFile);
			copyFile(outFile, bakFile);
		}
		// 3) replace the settings file with the temporary one
		deleteQuietly(outFile);
		if(!tmpFile.renameTo(outFile)) {
			copyFile(tmpFile, outFile);
			deleteQuietly(tmpFile);
		}
	}

	private static void copyFile(final File source, final File target) throws IOException {
		InputStream in = null;
		OutputStream out = null;
		try {
			in = new FileInputStream(source);
			out = new FileOutputStream(target);
			final byte[] buffer = new byte[4096];
			int read;
			while((read = in.read(buffer)) > 0) {
				out.write(buffer, 0, read);
			}
		}
		finally {
			closeQuietly(in);
			closeQuietly(out);
		}
		if(target.length() != source.length()) {
			throw new IOException("incomplete copy: " + source + " -> " + target);
		}
	}

	private static void closeQuietly(final Closeable closeable) {
		if(closeable == null) {
			return;
		}
		try {
			closeable.close();
		}
		catch (IOException e) {
			LogUtils.warn(e);
		}
	}

	private static void deleteQuietly(final File file) {
		if(file == null || !file.exists()) {
			return;
		}
		if(!file.delete()) {
			LogUtils.warn("could not delete file: " + file);
		}
	}
	
	protected ReadManager getReadManager() {
		return readManager;
	}
	
	private class DefaultResultProcessor implements IResultProcessor {

		private AWorkspaceProject project;

		public AWorkspaceProject getProject() {
			return project;
		}

		public void setProject(AWorkspaceProject project) {
			this.project = project;
		}

		public void process(AWorkspaceTreeNode parent, AWorkspaceTreeNode node) {
			if(getProject() == null) {
				LogUtils.warn("Missing project container! cannot add node to a model.");
				return;
			}
			if(node instanceof ProjectRootNode) {
				getProject().getModel().setRoot(node);
				if(((ProjectRootNode) node).getProjectID() == null) {
					((ProjectRootNode) node).setProjectID(getProject().getProjectID());
				}
				((ProjectRootNode) node).initiateMyFile(getProject());
			}
			else {
				if(parent == null) {
					if (!getProject().getModel().containsNode(node.getKey())) {
						getProject().getModel().addNodeTo(node, (AWorkspaceTreeNode) parent);			
					}
				}
				else {
					if (!parent.getModel().containsNode(node.getKey())) {
						parent.getModel().addNodeTo(node, (AWorkspaceTreeNode) parent);			
					}
				}
			}
		}

	}
}
