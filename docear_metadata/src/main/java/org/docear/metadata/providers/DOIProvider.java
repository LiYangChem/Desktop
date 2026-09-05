package org.docear.metadata.providers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.docear.metadata.adapter.DoiSource;
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
 * DOI content negotiation provider (https://doi.org).
 *
 * Sends "Accept: application/vnd.citationstyles.csl+json" to
 * https://doi.org/{doi}; the DOI proxy redirects to whichever registration
 * agency owns the DOI (Crossref, DataCite, mEDRA, ...) and returns a
 * CSL-JSON document. Unlike CrossrefProvider this resolves DOIs from ALL
 * agencies - datasets and preprints registered with DataCite are the main
 * beneficiaries.
 *
 * The DOI provider is an exact resolver by design: title/author searches
 * return an empty list (the interface forces the methods to exist). It is
 * the authoritative source for doiMatch=true in the candidate scoring.
 *
 * New code of the metadata provider extension (phase 2: DOI). Java 1.6
 * syntax only.
 */
public class DOIProvider implements MetadataProvider {

	private static final Logger logger = LoggerFactory.getLogger(DOIProvider.class);

	public static final String CONTENT_NEGOTIATION_URL = "https://doi.org/";

	public static final String CSL_JSON_ACCEPT = "application/vnd.citationstyles.csl+json";

	public static final int DEFAULT_TIMEOUT = 10000;

	private static final String USER_AGENT = "Docear-Desktop/1.2-Stable (metadata retrieval; +https://github.com/BeelGroup/Docear-Desktop)";

	private int timeout = DEFAULT_TIMEOUT;

	public String getProviderName() {
		return "DOI";
	}

	public MetaDataSource getSource() {
		return DoiSource.DOI;
	}

	public boolean supports(MetadataQuery query) {
		// the DOI provider can only answer exact DOI lookups
		return query != null && query.hasDoi();
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
		List<BibliographicMetadata> result = new ArrayList<BibliographicMetadata>();
		String extracted = CrossrefProvider.extractDoi(doi);
		if (extracted == null) {
			return result;
		}
		String body = executeRequest(CONTENT_NEGOTIATION_URL + urlEncodeDoi(extracted));
		Object root = JsonReader.parse(body);
		Map<String, Object> item = JsonReader.asMap(root);
		BibliographicMetadata metadata = CslJsonMapper.fromWorkItem(item);
		if (metadata != null) {
			// agencies sometimes omit the DOI in the response; make sure the
			// resolved record carries the DOI that was asked for
			if (metadata.getDoi() == null || metadata.getDoi().trim().length() == 0) {
				metadata.setDoi(extracted);
			}
			result.add(metadata);
		}
		return result;
	}

	public List<BibliographicMetadata> resolveByTitle(String title, int maxResults) throws IOException {
		// not a search provider by design
		return new ArrayList<BibliographicMetadata>();
	}

	public List<BibliographicMetadata> resolveByAuthors(List<String> authors, int maxResults) throws IOException {
		return new ArrayList<BibliographicMetadata>();
	}

	public List<BibliographicMetadata> resolveByTitleAndYear(String title, int year, int maxResults)
			throws IOException {
		return new ArrayList<BibliographicMetadata>();
	}

	// ------------------------------------------------------------------
	// HTTP
	// ------------------------------------------------------------------

	private String executeRequest(String url) throws IOException {
		// doi.org is a thin redirect layer and does not rate limit like the
		// Crossref search API; one retry on a transient 5xx is enough
		IOException lastFailure = null;
		for (int attempt = 0; attempt < 2; attempt++) {
			if (attempt > 0) {
				try {
					Thread.sleep(1000L);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					throw lastFailure != null ? lastFailure : new IOException("interrupted during back-off");
				}
			}
			try {
				Connection connection = Jsoup.connect(url).ignoreContentType(true).userAgent(USER_AGENT)
						.timeout(timeout).followRedirects(true);
				// content negotiation: this header must survive the redirect
				// to the registration agency, otherwise HTML comes back
				connection.header("Accept", CSL_JSON_ACCEPT);
				return connection.execute().body();
			} catch (IOException e) {
				lastFailure = e;
				if (!isRetryable(e)) {
					throw e;
				}
				logger.warn("doi.org request failed (" + e.getMessage() + "), retrying once");
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

	private static String urlEncodeDoi(String doi) {
		// unlike the Crossref API path (which requires "%2F"), doi.org expects
		// the canonical display form with unencoded slashes; only characters
		// that would break URL parsing itself are escaped
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < doi.length(); i++) {
			char c = doi.charAt(i);
			if (c == ' ' || c == '#' || c == '?' || c == '%') {
				sb.append('%');
				sb.append(Character.toUpperCase(Character.forDigit((c >> 4) & 0xF, 16)));
				sb.append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}
}
