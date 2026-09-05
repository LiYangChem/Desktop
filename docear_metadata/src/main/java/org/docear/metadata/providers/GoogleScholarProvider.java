package org.docear.metadata.providers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.docear.metadata.data.MetaDataSource;
import org.docear.metadata.data.ScholarMetaData;
import org.docear.metadata.io.BibtexFieldParser;
import org.docear.metadata.model.BibliographicMetadata;
import org.docear.metadata.model.MetadataQuery;

/**
 * Thin adapter for the EXISTING Google Scholar chain (architecture decision
 * D7): the retrieval itself keeps running through GoogleScholarSearchEngine /
 * GoogleScholarExtractor with their captcha/cookie/403 handling completely
 * untouched - this class never issues a request and all resolve methods
 * return empty lists.
 *
 * Its job is the result side: it converts the BibTeX that the Google Scholar
 * chain produces into a BibliographicMetadata so the
 * MetadataCandidateAggregator can score Google Scholar results with the same
 * CandidateScorer used for Crossref/DOI results. Implementing
 * MetadataProvider keeps the door open for a later migration of Google
 * Scholar to the unified provider registration (decision point B).
 *
 * New code of the metadata provider extension (phase 3: Google Scholar
 * adapter). Java 1.6 syntax only.
 */
public class GoogleScholarProvider implements MetadataProvider {

	private static final Pattern YEAR_PATTERN = Pattern.compile("\\d{4}");

	public String getProviderName() {
		return "Google Scholar";
	}

	public MetaDataSource getSource() {
		return ScholarMetaData.ScholarSource.GOOGLESCHOLAR;
	}

	public boolean supports(MetadataQuery query) {
		// Google Scholar is a free-text search engine, not an exact DOI
		// resolver - DOI lookups are served by DOIProvider/CrossrefProvider
		return query != null && query.hasTitle() && !query.hasDoi();
	}

	// ------------------------------------------------------------------
	// resolve methods - intentionally not implemented (adapter only)
	// ------------------------------------------------------------------

	public List<BibliographicMetadata> resolveByDOI(String doi) throws IOException {
		return new ArrayList<BibliographicMetadata>();
	}

	public List<BibliographicMetadata> resolveByTitle(String title, int maxResults) throws IOException {
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
	// adaptation of existing Google Scholar results
	// ------------------------------------------------------------------

	/**
	 * Converts the result of the existing Google Scholar chain into the
	 * structured model. Returns null if the BibTeX cannot be parsed.
	 */
	public BibliographicMetadata adapt(ScholarMetaData metaData) {
		if (metaData == null) {
			return null;
		}
		return adapt(metaData.getBibtex());
	}

	/**
	 * Parses a Google Scholar BibTeX string into the structured model.
	 * Returns null for input that is not a parsable entry.
	 */
	public BibliographicMetadata adapt(String bibtex) {
		if (bibtex == null || bibtex.trim().length() == 0) {
			return null;
		}
		Map<String, String> fields = BibtexFieldParser.parseFields(bibtex);
		if (fields.isEmpty()) {
			return null;
		}
		BibliographicMetadata metadata = new BibliographicMetadata();
		metadata.setTitle(stripBraces(fields.get("title")));
		addAuthors(metadata, fields.get("author"));
		metadata.setJournal(firstNonEmpty(stripBraces(fields.get("journal")), stripBraces(fields.get("booktitle"))));
		metadata.setYear(parseYear(fields.get("year")));
		metadata.setVolume(stripBraces(fields.get("volume")));
		metadata.setIssue(stripBraces(fields.get("number")));
		metadata.setPages(stripBraces(fields.get("pages")));
		metadata.setPublisher(stripBraces(fields.get("publisher")));
		String doi = stripBraces(fields.get("doi"));
		String url = stripBraces(fields.get("url"));
		if (doi == null && url != null) {
			doi = doiFromUrl(url);
		}
		metadata.setDoi(doi);
		metadata.setUrl(url);
		// the raw BibTeX entry type; Metadata2BibtexConverter.mapEntryType
		// knows the BibTeX vocabulary, so the value round-trips
		metadata.setPublicationType(BibtexFieldParser.getEntryType(bibtex));
		if (metadata.getTitle() == null && metadata.getAuthors().isEmpty()) {
			// nothing usable for scoring
			return null;
		}
		return metadata;
	}

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	private void addAuthors(BibliographicMetadata metadata, String authorField) {
		if (authorField == null) {
			return;
		}
		String[] names = authorField.split(" and | AND | And ");
		for (int i = 0; i < names.length; i++) {
			String name = stripBraces(names[i]);
			if (name != null && name.trim().length() > 0) {
				metadata.addAuthor(name.trim());
			}
		}
	}

	private String stripBraces(String value) {
		if (value == null) {
			return null;
		}
		String cleaned = value.trim();
		if (cleaned.length() == 0) {
			return null;
		}
		cleaned = cleaned.replaceAll("\\{", "").replaceAll("\\}", "");
		cleaned = cleaned.replaceAll("\\s+", " ").trim();
		if (cleaned.length() == 0) {
			return null;
		}
		return cleaned;
	}

	private Integer parseYear(String value) {
		if (value == null) {
			return null;
		}
		Matcher matcher = YEAR_PATTERN.matcher(value);
		if (matcher.find()) {
			try {
				return Integer.valueOf(matcher.group());
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

	/**
	 * Recovers a DOI from a "https://doi.org/..." (or dx.doi.org) link for
	 * records where Google Scholar only exports the URL.
	 */
	private String doiFromUrl(String url) {
		String marker = "doi.org/";
		int idx = url.toLowerCase().indexOf(marker);
		if (idx < 0) {
			return null;
		}
		String doi = url.substring(idx + marker.length()).trim();
		if (doi.length() == 0) {
			return null;
		}
		while (doi.endsWith("/") || doi.endsWith(".")) {
			doi = doi.substring(0, doi.length() - 1);
		}
		doi = doi.replaceAll("%2F", "/").replaceAll("%2f", "/");
		if (doi.indexOf('/') < 0) {
			return null;
		}
		return doi;
	}

	private String firstNonEmpty(String a, String b) {
		if (a != null && a.trim().length() > 0) {
			return a;
		}
		return b;
	}
}
