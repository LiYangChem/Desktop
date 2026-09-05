package org.docear.plugin.bibtex.dialogs;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.sf.jabref.BibtexEntry;
import net.sf.jabref.imports.BibtexParser;
import net.sf.jabref.imports.ParserResult;
import net.sf.jabref.util.Pair;

import org.docear.metadata.adapter.BibMetaData;
import org.docear.metadata.data.MetaData;
import org.docear.metadata.data.MetaDataSource;
import org.docear.metadata.data.ScholarMetaData;
import org.docear.metadata.match.CandidateScorer;
import org.docear.metadata.match.TextNormalizer;
import org.docear.metadata.model.BibliographicMetadata;
import org.docear.metadata.model.MetadataCandidate;
import org.docear.metadata.model.MetadataQuery;
import org.docear.metadata.providers.GoogleScholarProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects the results of all metadata engines of one search, scores them
 * uniformly and produces the final, ordered, deduplicated list for the
 * result list of MetaDataExtractorPage.
 *
 * Two result families feed in:
 * <ul>
 * <li>{@code BibMetaData} (Crossref / DOI providers): carries the structured
 * {@code BibliographicMetadata} alongside its generated BibTeX.</li>
 * <li>plain {@code ScholarMetaData} (the existing Google Scholar chain): the
 * adapter {@code GoogleScholarProvider} derives the structured view from the
 * BibTeX that Google Scholar exported.</li>
 * </ul>
 *
 * Every result is scored with the shared {@code CandidateScorer}, entries
 * with the same DOI (or, if no DOI is present, the same normalized
 * title+year) are collapsed to the best-scored copy, and the remaining
 * entries are returned ordered by finalScore, descending.
 *
 * The aggregation replaces the previous per-engine "append in arrival
 * order" behaviour of the page as well as the phase-2 interim
 * filterDuplicateDois() workaround.
 *
 * New code of the metadata provider extension (phase 3: Google Scholar
 * adapter + aggregator). Java 1.6 syntax only.
 */
public class MetadataCandidateAggregator {

	private static final Logger logger = LoggerFactory.getLogger(MetadataCandidateAggregator.class);

	private final GoogleScholarProvider scholarAdapter = new GoogleScholarProvider();

	/** scored candidates, index-synced with {@link #displayEntries}. */
	private final List<MetadataCandidate> candidates = new ArrayList<MetadataCandidate>();

	/** the BibTeX entry shown in the result list, index-synced with {@link #candidates}. */
	private final List<Pair<BibtexEntry, MetaDataSource>> displayEntries = new ArrayList<Pair<BibtexEntry, MetaDataSource>>();

	/**
	 * Adds all usable results of one finished engine request. Results whose
	 * BibTeX cannot be parsed are skipped (logged); results without usable
	 * fields still enter the list with score 0 so nothing the engines found
	 * gets silently lost.
	 *
	 * May be called from the search hub's worker threads - the method is
	 * synchronized, and the page refreshes the list model from the EDT-safe
	 * snapshot returned by getSortedEntryPairs().
	 */
	public synchronized void aggregate(Collection<MetaData> results, MetadataQuery query) {
		if (results == null) {
			return;
		}
		for (MetaData result : results) {
			if (!(result instanceof ScholarMetaData)) {
				continue;
			}
			ScholarMetaData scholarResult = (ScholarMetaData) result;
			BibliographicMetadata structured;
			String providerName;
			if (result instanceof BibMetaData) {
				structured = ((BibMetaData) result).getStructured();
				providerName = ((BibMetaData) result).getProviderName();
			} else {
				structured = scholarAdapter.adapt(scholarResult);
				providerName = scholarAdapter.getProviderName();
			}
			MetadataCandidate candidate = CandidateScorer.score(query, structured, providerName);
			try {
				ParserResult parsed = BibtexParser.parse(new StringReader(scholarResult.getBibtex()));
				Collection<BibtexEntry> entries = parsed.getDatabase().getEntries();
				for (BibtexEntry entry : entries) {
					candidates.add(candidate);
					displayEntries.add(new Pair<BibtexEntry, MetaDataSource>(entry, result.getSource()));
				}
			} catch (IOException e) {
				logger.warn("could not parse fetched BibTeX of provider {}: {}", providerName, e.getMessage());
			}
		}
	}

	/**
	 * Returns the current result list: deduplicated and ordered by
	 * finalScore, descending. Ties keep Google Scholar rank order (stable
	 * sort).
	 */
	public synchronized List<Pair<BibtexEntry, MetaDataSource>> getSortedEntryPairs() {
		List<int[]> order = new ArrayList<int[]>();
		for (int i = 0; i < candidates.size(); i++) {
			order.add(new int[] { i });
		}
		final List<MetadataCandidate> scored = candidates;
		Collections.sort(order, new Comparator<int[]>() {
			public int compare(int[] a, int[] b) {
				return scored.get(a[0]).compareTo(scored.get(b[0]));
			}
		});

		List<Pair<BibtexEntry, MetaDataSource>> result = new ArrayList<Pair<BibtexEntry, MetaDataSource>>();
		Set<String> seenKeys = new HashSet<String>();
		for (Iterator<int[]> it = order.iterator(); it.hasNext();) {
			int index = it.next()[0];
			Pair<BibtexEntry, MetaDataSource> pair = displayEntries.get(index);
			// dual identity: a DOI key AND/OR a title+year key. A record is
			// dropped when EITHER key was already emitted by a better-scored
			// candidate - this also collapses the (record with DOI) vs.
			// (same record without DOI) case across engines
			String doiKey = doiKey(candidates.get(index), pair);
			String titleKey = titleKey(candidates.get(index), pair);
			if (doiKey == null && titleKey == null) {
				// unidentifiable result: never deduplicated, always shown
				result.add(pair);
				continue;
			}
			if ((doiKey != null && seenKeys.contains(doiKey)) || (titleKey != null && seenKeys.contains(titleKey))) {
				continue;
			}
			if (doiKey != null) {
				seenKeys.add(doiKey);
			}
			if (titleKey != null) {
				seenKeys.add(titleKey);
			}
			result.add(pair);
		}
		return result;
	}

	/** Removes all collected results (called when a new search starts). */
	public synchronized void clear() {
		candidates.clear();
		displayEntries.clear();
	}

	public synchronized int size() {
		return displayEntries.size();
	}

	// ------------------------------------------------------------------
	// deduplication
	// ------------------------------------------------------------------

	/**
	 * Identity by DOI: the normalized DOI of the record, if it has one.
	 */
	private String doiKey(MetadataCandidate candidate, Pair<BibtexEntry, MetaDataSource> pair) {
		String doi = fieldValue(candidate, pair, "doi");
		if (doi == null) {
			return null;
		}
		String normalized = TextNormalizer.normalizeDoi(doi);
		if (normalized == null) {
			return null;
		}
		return "doi:" + normalized;
	}

	/**
	 * Identity by title+year: the normalized title plus the publication year,
	 * for records without a DOI.
	 */
	private String titleKey(MetadataCandidate candidate, Pair<BibtexEntry, MetaDataSource> pair) {
		String title = fieldValue(candidate, pair, "title");
		if (title == null) {
			return null;
		}
		String normalizedTitle = TextNormalizer.normalizeTitle(title);
		if (normalizedTitle == null || normalizedTitle.length() == 0) {
			return null;
		}
		return "title:" + normalizedTitle + "|" + fieldValue(candidate, pair, "year");
	}

	/**
	 * Reads a field from the structured candidate, falling back to the
	 * BibTeX entry field (for results without a structured view).
	 */
	private String fieldValue(MetadataCandidate candidate, Pair<BibtexEntry, MetaDataSource> pair, String field) {
		String value = null;
		if (candidate != null && candidate.getMetadata() != null) {
			BibliographicMetadata metadata = candidate.getMetadata();
			if ("doi".equals(field)) {
				value = metadata.getDoi();
			} else if ("title".equals(field)) {
				value = metadata.getTitle();
			} else if ("year".equals(field)) {
				value = metadata.getYear() != null ? String.valueOf(metadata.getYear()) : null;
			}
		}
		if ((value == null || value.trim().length() == 0) && pair != null && pair.p != null) {
			value = pair.p.getField(field);
		}
		if (value == null || value.trim().length() == 0) {
			return null;
		}
		return value.trim();
	}
}
