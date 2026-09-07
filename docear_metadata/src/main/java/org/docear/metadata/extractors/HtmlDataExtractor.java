package org.docear.metadata.extractors;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.docear.metadata.events.MetaDataListener;
import org.docear.metadata.net.MetadataNetworkConfig;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

public abstract class HtmlDataExtractor implements MetaDataExtractor{
	
	protected final static Logger logger = LoggerFactory.getLogger(HtmlDataExtractor.class);
	private String userAgent = "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:25.0) Gecko/20100101 Firefox/25.0";	
	private String referrer = "http://www.google.com";
	private int timeout = 3000;
	private boolean followRedirects = true;
	protected boolean debuglogging = false;
	private String cookieFolder = System.getProperty("user.home");
	protected String searchValue = "";	
	protected int maxResults = 3;
	private String proxyMode = MetadataNetworkConfig.DEFAULT_MODE.name();
	private String proxyHost = "";
	private String proxyPort = "";
	private Map<ExtractorConfigKey, Object> config = new HashMap<ExtractorConfigKey, Object>();
	private ArrayList<MetaDataListener> listeners = new ArrayList<MetaDataListener>();
	
	public enum CommonConfigKeys implements ExtractorConfigKey{
		SEARCHVALUE,
		TIMEOUT,
		USERAGENT,
		REFERRER,
		FOLLOWREDIRECTS,
		COOKIE_FOLDER,		
		MAXRESULTS,
		DEBUGLOGGING,
		PROXY_MODE,
		PROXY_HOST,
		PROXY_PORT;
	}
	
	public HtmlDataExtractor(){};
	
	public HtmlDataExtractor(Map<ExtractorConfigKey, Object> config) throws MalformedConfigException{
		readConfig(config);
	}
	
	public HtmlDataExtractor(Map<ExtractorConfigKey, Object> config, MetaDataListener listener) throws MalformedConfigException {
		readConfig(config);
		this.addListeners(listener);
	}
	
	protected void readConfig(Map<ExtractorConfigKey, Object> config) throws MalformedConfigException{		
		for(Object value : config.values()){
			if(value == null){
				logger.warn("Null value in config map.");
				throw new MalformedConfigException();		
			}
		}
		try{
			for(ExtractorConfigKey key : config.keySet()){
				if(key instanceof CommonConfigKeys){
					CommonConfigKeys commonKey = (CommonConfigKeys)key;
					switch(commonKey){
						case USERAGENT:
							this.userAgent = (String) config.get(CommonConfigKeys.USERAGENT);
							break;
						case REFERRER:
							this.referrer = (String) config.get(CommonConfigKeys.REFERRER);
							break;
						case COOKIE_FOLDER:
							this.cookieFolder = (String) config.get(CommonConfigKeys.COOKIE_FOLDER);
							break;		
						case TIMEOUT:
							this.timeout = (Integer) config.get(CommonConfigKeys.TIMEOUT);						
							break;
						case FOLLOWREDIRECTS:
							this.followRedirects = (Boolean) config.get(CommonConfigKeys.FOLLOWREDIRECTS);						
							break;
						case MAXRESULTS:						
							this.maxResults = (Integer) config.get(CommonConfigKeys.MAXRESULTS);						
							break;			
						case SEARCHVALUE:
							this.searchValue = (String) config.get(CommonConfigKeys.SEARCHVALUE);
							break;
						case DEBUGLOGGING:
							this.debuglogging = (Boolean) config.get(CommonConfigKeys.DEBUGLOGGING);						
							break;
						case PROXY_MODE:
							this.proxyMode = (String) config.get(CommonConfigKeys.PROXY_MODE);
							break;
						case PROXY_HOST:
							this.proxyHost = (String) config.get(CommonConfigKeys.PROXY_HOST);
							break;
						case PROXY_PORT:
							this.proxyPort = (String) config.get(CommonConfigKeys.PROXY_PORT);
							break;
							
						default:
							break;					
					}
				}			
			}
		}catch(ClassCastException e){
			logger.error("Could not cast config parameter.", e);
			throw new MalformedConfigException();
		}
		applyProxyConfig();
	}

	/**
	 * Applies the proxy mode to the JVM's standard proxy mechanism. jsoup
	 * 1.7.3 has no per-connection proxy API, so the only supported approach
	 * is toggling the JVM proxy properties read by the default
	 * ProxySelector. Called after config parsing so every extractor applies
	 * the mode that was configured for it.
	 */
	private void applyProxyConfig() {
		MetadataNetworkConfig.Mode mode = MetadataNetworkConfig.parseMode(this.proxyMode, MetadataNetworkConfig.DEFAULT_MODE);
		MetadataNetworkConfig.apply(mode, this.proxyHost, this.proxyPort);
		if (this.debuglogging) {
			logger.info("[Network] Proxy mode = " + MetadataNetworkConfig.describe());
		}
	}

	protected Connection getConnection(String URL) {		
		return Jsoup.connect(URL)				   
		           .ignoreContentType(true)
		           .userAgent(this.userAgent)  
		           .referrer(this.referrer)   
		           .timeout(this.timeout) 
		           .followRedirects(this.followRedirects);		           
	}

	protected boolean saveCookies(Map<String, String> cookies, String cookieFileName) {
		boolean correctSaved = true;
		String path = getPath(cookieFileName);	
		XStream xStream = new XStream(new DomDriver());
		xStream.alias("map", java.util.Map.class);
		String xml = xStream.toXML(cookies);
		FileOutputStream fos = null;
		try {
		    fos = new FileOutputStream(path);
		    fos.write("<?xml version=\"1.0\"?>\n".getBytes("UTF-8")); 		    
		    byte[] bytes = xml.getBytes("UTF-8");
		    fos.write(bytes);	
		} catch(Exception e) {
			logger.error("Could not write cookie data to " + path, e);
			correctSaved = false;
		} finally {
		    if(fos!=null) {
		        try{ 
		            fos.close();
		        } catch (IOException e) {
		        	logger.info("Could not write cookie data to " + path);
		        	correctSaved = false;
		        }
		    }		    
		}
		return correctSaved;
	}

	
	protected Map<String, String> readCookies(String cookieFileName) {
		String path = getPath(cookieFileName);	
		if(!new File(path).exists()){
			return null;
		}
		XStream xStream = new XStream(new DomDriver());
		xStream.alias("map", java.util.Map.class);
		try{
			@SuppressWarnings("unchecked")
			Map<String,String> cookies = (Map<String,String>)xStream.fromXML(new File(path));
			return cookies;
		}catch(Exception e){
			logger.info("Could not read cookie data from " + path);
		}
		return null;
	}

	protected String getPath(String cookieFileName) {
		String path = this.cookieFolder;
		if(path.endsWith(File.separator)){
			path = path + cookieFileName;
		}
		else{
			path = path + File.separator + cookieFileName;
		}
		return path;
	}

	public Map<ExtractorConfigKey, Object> getConfig() {
		return config;
	}

	public void setConfig(Map<ExtractorConfigKey, Object> config) throws MalformedConfigException {
		this.config = config;
		readConfig(config);
	}

	public ArrayList<MetaDataListener> getListeners() {
		return listeners;
	}
	
	public boolean removeListener(MetaDataListener listener) {
		return this.listeners.remove(listener);		
	}

	public void addListeners(MetaDataListener listener) {
		this.listeners.add(listener);
	}

}