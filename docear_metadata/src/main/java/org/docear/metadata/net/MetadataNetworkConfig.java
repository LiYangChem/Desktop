package org.docear.metadata.net;

/**
 * Applies the metadata retrieval proxy mode through the standard JVM
 * networking mechanism.
 *
 * <p>The metadata HTTP layer is jsoup 1.7.3, which internally uses
 * {@code java.net.HttpURLConnection} via {@code URL.openConnection()}. jsoup
 * 1.7.3 exposes <b>no</b> per-connection {@code proxy(...)} method, so the
 * proxy cannot be configured per request. The only supported way to influence
 * the proxy is the JVM's own {@link java.net.ProxySelector}
 * ({@code sun.net.spi.DefaultProxySelector}), which reads:
 *
 * <ul>
 *   <li>{@code java.net.useSystemProxies} - if {@code true}, the operating
 *       system proxy (Windows registry under the current user, gsettings on
 *       Linux, ...) is used, and the proxy system properties below are
 *       ignored.</li>
 *   <li>{@code http.proxyHost}/{@code http.proxyPort} and
 *       {@code https.proxyHost}/{@code https.proxyPort} - a fixed HTTP/HTTPS
 *       proxy, used when {@code useSystemProxies} is not {@code true}.</li>
 * </ul>
 *
 * This class therefore implements the three user selectable modes by toggling
 * those standard properties. No proxy address is hard-coded; SYSTEM follows
 * whatever the operating system is already configured with, CUSTOM uses the
 * host/port the user entered, NONE disables proxying.
 *
 * <p>The values are read lazily by the default {@code ProxySelector} on each
 * {@code select()} call, so applying a mode right before a search (as
 * {@code HtmlDataExtractor.readConfig()} does) is sufficient - no JVM restart
 * is needed.
 *
 * New code of the metadata network configuration. Java 1.6 syntax only.
 */
public final class MetadataNetworkConfig {

	/** The three selectable proxy modes. */
	public enum Mode {
		/** Use the operating system proxy (Windows registry / gsettings). */
		SYSTEM,
		/** Do not use any proxy. */
		NONE,
		/** Use an explicit host/port proxy. */
		CUSTOM
	}

	public static final String PROP_USE_SYSTEM_PROXIES = "java.net.useSystemProxies";
	public static final String PROP_HTTP_PROXY_HOST = "http.proxyHost";
	public static final String PROP_HTTP_PROXY_PORT = "http.proxyPort";
	public static final String PROP_HTTPS_PROXY_HOST = "https.proxyHost";
	public static final String PROP_HTTPS_PROXY_PORT = "https.proxyPort";

	/**
	 * Internal marker so {@link #currentMode()} can tell SYSTEM apart from
	 * CUSTOM after apply(). SYSTEM must NOT rely on
	 * {@code java.net.useSystemProxies} at runtime: sun.net.spi.DefaultProxySelector
	 * reads that flag into a static final field when the class is first loaded,
	 * which in the GUI happens during startup (UpdateCheck) - before the user
	 * configures anything. A later toggle has no effect (proven by test T1).
	 * The fixed proxy properties, in contrast, are re-read by the default
	 * ProxySelector on every select() call (proven by test T2), so SYSTEM is
	 * implemented by copying the OS proxy into those properties.
	 */
	public static final String PROP_MODE_MARKER = "org.docear.metadata.proxyMode";

	/** Default mode when nothing is configured: follow the OS proxy. */
	public static final Mode DEFAULT_MODE = Mode.SYSTEM;

	private MetadataNetworkConfig() {
		// utility class
	}

	/**
	 * Resolves a mode from a string without failing on unknown input. Returns
	 * the given default when the string is null, blank or not a known mode.
	 */
	public static Mode parseMode(String value, Mode defaultMode) {
		if (value == null || value.trim().length() == 0) {
			return defaultMode;
		}
		try {
			return Mode.valueOf(value.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return defaultMode;
		}
	}

	/**
	 * Applies the given mode (and, for {@link Mode#CUSTOM}, the given
	 * host/port) to the JVM proxy system properties.
	 */
	public static void apply(Mode mode, String host, String port) {
		if (mode == null) {
			mode = DEFAULT_MODE;
		}
		if (Mode.SYSTEM.equals(mode)) {
			System.setProperty(PROP_MODE_MARKER, Mode.SYSTEM.name());
			applySystemProxy();
		} else if (Mode.NONE.equals(mode)) {
			System.setProperty(PROP_MODE_MARKER, Mode.NONE.name());
			System.setProperty(PROP_USE_SYSTEM_PROXIES, "false");
			clearFixedProxyProperties();
		} else { // CUSTOM
			System.setProperty(PROP_MODE_MARKER, Mode.CUSTOM.name());
			System.setProperty(PROP_USE_SYSTEM_PROXIES, "false");
			if (host != null && host.trim().length() > 0) {
				String h = host.trim();
				String p = port != null ? port.trim() : "";
				System.setProperty(PROP_HTTP_PROXY_HOST, h);
				System.setProperty(PROP_HTTP_PROXY_PORT, p);
				System.setProperty(PROP_HTTPS_PROXY_HOST, h);
				System.setProperty(PROP_HTTPS_PROXY_PORT, p);
			} else {
				clearFixedProxyProperties();
			}
		}
	}

	/**
	 * Applies the OS-configured proxy. On Windows the current user's proxy is
	 * read live from the registry (Internet Settings) and copied into the
	 * fixed proxy properties, because {@code java.net.useSystemProxies} is
	 * cached by the JDK at class-load time and cannot be toggled at runtime.
	 * The address is read fresh on every call - nothing is hard-coded. On
	 * other operating systems the standard {@code useSystemProxies} flag is
	 * used (it is set at JVM start there in practice, and no registry exists).
	 */
	private static void applySystemProxy() {
		if (!isWindows()) {
			System.setProperty(PROP_USE_SYSTEM_PROXIES, "true");
			clearFixedProxyProperties();
			return;
		}
		String[] proxy = readWindowsProxy();
		if (proxy != null) {
			System.setProperty(PROP_USE_SYSTEM_PROXIES, "false");
			System.setProperty(PROP_HTTP_PROXY_HOST, proxy[0]);
			System.setProperty(PROP_HTTP_PROXY_PORT, proxy[1]);
			System.setProperty(PROP_HTTPS_PROXY_HOST, proxy[0]);
			System.setProperty(PROP_HTTPS_PROXY_PORT, proxy[1]);
		} else {
			// OS proxy disabled: connect directly
			System.setProperty(PROP_USE_SYSTEM_PROXIES, "false");
			clearFixedProxyProperties();
		}
	}

	private static boolean isWindows() {
		String os = System.getProperty("os.name", "");
		return os.toLowerCase().indexOf("win") >= 0;
	}

	/**
	 * Reads ProxyEnable/ProxyServer from
	 * HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings via
	 * "reg query". Returns {host, port} when an OS proxy is enabled, else
	 * null. Never throws - a failed read means "no proxy".
	 */
	private static String[] readWindowsProxy() {
		try {
			Process p = Runtime.getRuntime().exec(new String[] {
					"reg", "query",
					"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
					"/v", "ProxyEnable" });
			String enable = readRegValue(p);
			p.waitFor();
			if (enable == null || !enable.contains("0x1")) {
				return null;
			}
			p = Runtime.getRuntime().exec(new String[] {
					"reg", "query",
					"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
					"/v", "ProxyServer" });
			String server = readRegValue(p);
			p.waitFor();
			if (server == null || server.trim().length() == 0) {
				return null;
			}
			return parseProxyServer(server.trim());
		} catch (Exception e) {
			return null;
		}
	}

	/** Reads the last token of the reg query output line (the value). */
	private static String readRegValue(Process p) {
		java.io.InputStream in = null;
		try {
			in = p.getInputStream();
			java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
			byte[] b = new byte[512];
			int n;
			while ((n = in.read(b)) > 0) {
				buf.write(b, 0, n);
			}
			String out = new String(buf.toByteArray());
			int idx = out.indexOf("REG_SZ");
			if (idx >= 0) {
				return out.substring(idx + 6).trim();
			}
			idx = out.indexOf("REG_DWORD");
			if (idx >= 0) {
				return out.substring(idx + 9).trim();
			}
			return null;
		} catch (Exception e) {
			return null;
		} finally {
			if (in != null) {
				try { in.close(); } catch (Exception ignore) { }
			}
		}
	}

	/**
	 * Parses the ProxyServer registry value. Two formats exist:
	 * "host:port" (one proxy for all protocols) or semicolon separated
	 * per-protocol entries like "ftp=...;http=host:port;https=host:port".
	 */
	static String[] parseProxyServer(String value) {
		if (value.indexOf('=') >= 0 || value.indexOf(';') >= 0) {
			String https = extractProtocolProxy(value, "https=");
			String http = extractProtocolProxy(value, "http=");
			String chosen = https != null ? https : http;
			if (chosen != null) {
				return splitHostPort(chosen);
			}
			return null;
		}
		return splitHostPort(value);
	}

	private static String extractProtocolProxy(String value, String prefix) {
		int i = value.indexOf(prefix);
		if (i < 0) {
			return null;
		}
		int end = value.indexOf(';', i);
		String s = end > i ? value.substring(i + prefix.length(), end) : value.substring(i + prefix.length());
		s = s.trim();
		return s.length() > 0 ? s : null;
	}

	private static String[] splitHostPort(String s) {
		int colon = s.lastIndexOf(':');
		if (colon <= 0 || colon == s.length() - 1) {
			return null;
		}
		return new String[] { s.substring(0, colon), s.substring(colon + 1) };
	}

	/** Returns the currently active mode (as set by the last apply() call). */
	public static Mode currentMode() {
		String marker = System.getProperty(PROP_MODE_MARKER);
		if (marker != null) {
			return parseMode(marker, DEFAULT_MODE);
		}
		// fallback for code that never called apply()
		if ("true".equalsIgnoreCase(System.getProperty(PROP_USE_SYSTEM_PROXIES))) {
			return Mode.SYSTEM;
		}
		String host = System.getProperty(PROP_HTTP_PROXY_HOST);
		if (host != null && host.trim().length() > 0) {
			return Mode.CUSTOM;
		}
		return Mode.NONE;
	}

	/** Returns a human readable summary for logging (never any password). */
	public static String describe() {
		Mode mode = currentMode();
		if (Mode.NONE.equals(mode)) {
			return "NONE";
		}
		String host = System.getProperty(PROP_HTTP_PROXY_HOST, "");
		String port = System.getProperty(PROP_HTTP_PROXY_PORT, "");
		String hostPort = host.length() > 0 ? " " + host + ":" + port : " (no OS proxy)";
		if (Mode.SYSTEM.equals(mode)) {
			return "SYSTEM" + hostPort;
		}
		return "CUSTOM" + hostPort;
	}

	private static void clearFixedProxyProperties() {
		System.clearProperty(PROP_HTTP_PROXY_HOST);
		System.clearProperty(PROP_HTTP_PROXY_PORT);
		System.clearProperty(PROP_HTTPS_PROXY_HOST);
		System.clearProperty(PROP_HTTPS_PROXY_PORT);
	}
}
