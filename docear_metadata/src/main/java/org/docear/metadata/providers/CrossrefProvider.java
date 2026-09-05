package org.docear.metadata.providers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.docear.metadata.adapter.CrossrefSource;
import org.docear.metadata.data.MetaDataSource;
import org.docear.metadata.io.CslJsonMapper;
import org.docear.metadata.io.JsonReader;
import org.docear.metadata.model.BibliographicMetadata;
import org.docear.metadata.model.MetadataQuery;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Crossref REST API provider (https://api.crossref.org/works). Returns
 * structured BibliographicMetadata which the CrossrefExtractor converts into
 * scored BibMetaData candidates.
 *
 * HTTP is done with jsoup's Connection (already bundled inside the metadata
 * lib jar), responses are JSON parsed by the dependency-free JsonReader -
 * the project constraint "do not upgrade dependencies" forbids adding a JSON
 * library.
 *
 * New code of the metadata provider extension (phase 1: Crossref). Java 1.6
 * syntax only.
 */
public class CrossrefProvider implements MetadataProvider {

	private static final Logger logger = LoggerFactory.getLogger(CrossrefProvider.class);

	public static final String API_URL = "https://api.crossref.org/works";
	public static final int DEFAULT_TIMEOUT = 10000;

	private static final String USER_AGENT = "Docear-Desktop/1.2-Stable (metadata retrieval; +https://github.com/BeelGroup/Docear-Desktop)";

	private static final String MAILTO_PROPERTY = "org.docear.metadata.crossref.mailto";

	private static final String SELECT_FIELDS = "DOI,title,author,container-title,issued,volume,issue,page,publisher,URL,abstract,type";

	private static final Pattern DOI_INPUT_PATTERN = Pattern
			.compile("^\\s*(?:doi\\s*:\\s*|https?://(?:dx\\.)?doi\\.org/)?(10\\.\\d{4,9}/\\S+?)\\s*$",
					Pattern.CASE_INSENSITIVE);

	private int timeout = DEFAULT_TIMEOUT;

	public String getProviderName() {
		return "Crossref";
	}

	public MetaDataSource getSource() {
		return CrossrefSource.CROSSREF;
	}

	public boolean supports(MetadataQuery query) {
		if (query == null) {
			return false;
		}
		return query.hasTitle() || query.hasDoi() || query.hasAuthors();
	}

	public void setTimeout(int timeout) {
		if (timeout > 0) {
			this.timeout = timeout;
		}
	}

	public int getTimeout() {
		return timeout;
	}

	// ------------------------------------------------------------------
	// resolve methods
	// ------------------------------------------------------------------

	public List<BibliographicMetadata> resolveByDOI(String doi) throws IOException {
		String normalized = normalizeDoiForUrl(doi);
		if (normalized == null) {
			return new ArrayList<BibliographicMetadata>();
		}
		String body = getBody(API_URL + "/" + normalized, new LinkedHashMap<String, String>());
		return parseSingleWork(body);
	}

	public List<BibliographicMetadata> resolveByTitle(String title, int maxResults) throws IOException {
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("query.bibliographic", title);
		return searchWorks(params, maxResults);
	}

	public List<BibliographicMetadata> resolveByAuthors(List<String> authors, int maxResults) throws IOException {
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("query.author", join(authors));
		return searchWorks(params, maxResults);
	}

	public List<BibliographicMetadata> resolveByTitleAndYear(String title, int year, int maxResults)
			throws IOException {
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("query.bibliographic", title);
		params.put("filter", "from-pub-date:" + year + "-01-01,until-pub-date:" + year + "-12-31");
		return searchWorks(params, maxResults);
	}

	// ------------------------------------------------------------------
	// HTTP
	// ------------------------------------------------------------------

	private List<BibliographicMetadata> searchWorks(Map<String, String> params, int maxResults) throws IOException {
		if (maxResults <= 0) {
			maxResults = 3;
		}
		Map<String, String> query = new LinkedHashMap<String, String>(params);
		query.put("rows", String.valueOf(maxResults));
		query.put("select", SELECT_FIELDS);
		String body = getBody(API_URL, query);

		List<BibliographicMetadata> result = new ArrayList<BibliographicMetadata>();
		Object root = JsonReader.parse(body);
		Map<String, Object> message = JsonReader.asMap(JsonReader.asMap(root).get("message"));
		if (message == null) {
			return result;
		}
		List<Object> items = JsonReader.asList(message.get("items"));
		if (items == null) {
			return result;
		}
		for (Iterator<Object> it = items.iterator(); it.hasNext();) {
			Map<String, Object> item = JsonReader.asMap(it.next());
			if (item != null) {
				BibliographicMetadata metadata = CslJsonMapper.fromWorkItem(item);
				if (metadata != null) {
					result.add(metadata);
				}
			}
		}
		return result;
	}

	private List<BibliographicMetadata> parseSingleWork(String body) throws IOException {
		List<BibliographicMetadata> result = new ArrayList<BibliographicMetadata>();
		Object root = JsonReader.parse(body);
		Map<String, Object> message = JsonReader.asMap(JsonReader.asMap(root).get("message"));
		if (message == null) {
			return result;
		}
		BibliographicMetadata metadata = CslJsonMapper.fromWorkItem(message);
		if (metadata != null) {
			result.add(metadata);
		}
		return result;
	}

	private String getBody(String url, Map<String, String> params) throws IOException {
		// Crossref rate limits anonymous bursts (HTTP 429). Retry a couple of
		// times with increasing back-off before giving up; other I/O failures
		// (timeout, offline) propagate immediately like in the Google Scholar
		// extractor.
		long[] backoffMillis = new long[] { 1000L, 3000L };
		IOException lastFailure = null;
		for (int attempt = 0; attempt <= backoffMillis.length; attempt++) {
			if (attempt > 0) {
				try {
					Thread.sleep(backoffMillis[attempt - 1]);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					throw lastFailure != null ? lastFailure : new IOException("interrupted during back-off");
				}
			}
			try {
				return executeRequest(url, params);
			} catch (IOException e) {
				lastFailure = e;
				if (!isRetryable(e) || attempt == backoffMillis.length) {
					throw e;
				}
				logger.warn("Crossref request failed (" + e.getMessage() + "), retrying after back-off");
			}
		}
		throw lastFailure;
	}

	private boolean isRetryable(IOException e) {
		if (e instanceof org.jsoup.HttpStatusException) {
			int status = ((org.jsoup.HttpStatusException) e).getStatusCode();
			return status == 429 || status >= 500;
		}
		return false;
	}

	private String executeRequest(String url, Map<String, String> params) throws IOException {
		Connection connection = Jsoup.connect(url).ignoreContentType(true).userAgent(USER_AGENT).timeout(timeout)
				.followRedirects(true);
		String mailto = System.getProperty(MAILTO_PROPERTY);
		if (mailto != null && mailto.trim().length() > 0) {
			// opt-in to the Crossref "polite pool" (recommended for
			// applications; set -Dorg.docear.metadata.crossref.mailto=you@host)
			connection.data("mailto", mailto.trim());
		}
		Iterator<Map.Entry<String, String>> it = params.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, String> entry = it.next();
			connection.data(entry.getKey(), entry.getValue());
		}
		return connection.execute().body();
	}

	// ------------------------------------------------------------------
	// JSON -> model: delegated to the shared CslJsonMapper (the Crossref
	// work items are a superset of CSL-JSON, which doi.org content
	// negotiation also returns - one mapper serves both providers)
	// ------------------------------------------------------------------

	private String join(List<String> parts) {
		StringBuffer sb = new StringBuffer();
		if (parts != null) {
			for (Iterator<String> it = parts.iterator(); it.hasNext();) {
				if (sb.length() > 0) {
					sb.append(' ');
				}
				sb.append(it.next());
			}
		}
		return sb.toString();
	}

	/**
	 * Recognizes a raw DOI ("10.1234/abc") including "doi:" and doi.org URL
	 * prefixes; returns null if the input is not a DOI.
	 */
	public static String extractDoi(String input) {
		if (input == null) {
			return null;
		}
		Matcher matcher = DOI_INPUT_PATTERN.matcher(input);
		if (matcher.matches()) {
			return matcher.group(1);
		}
		return null;
	}

	private static String normalizeDoiForUrl(String doi) {
		String extracted = extractDoi(doi);
		if (extracted == null) {
			return null;
		}
		return extracted.replace("/", "%2F");
	}
}
