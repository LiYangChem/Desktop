package org.docear.metadata.adapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.docear.metadata.data.MetaData;
import org.docear.metadata.events.FetchedResultsEvent;
import org.docear.metadata.events.MetaDataListener;
import org.docear.metadata.extractors.ExtractorConfigKey;
import org.docear.metadata.extractors.HtmlDataExtractor;
import org.docear.metadata.extractors.HtmlDataExtractor.CommonConfigKeys;
import org.docear.metadata.extractors.MalformedConfigException;
import org.docear.metadata.match.CandidateScorer;
import org.docear.metadata.model.BibliographicMetadata;
import org.docear.metadata.model.MetadataCandidate;
import org.docear.metadata.model.MetadataQuery;
import org.docear.metadata.providers.CrossrefProvider;

/**
 * Callable execution unit for Crossref retrieval, structurally identical to
 * GoogleScholarExtractor but far simpler: the Crossref REST API needs neither
 * cookies nor CAPTCHA handling.
 *
 * On any IOException the extractor logs the problem and still fires a
 * FetchedResultsEvent with an empty result list - the wizard's request
 * counter depends on every engine firing exactly one event per search.
 *
 * If the search string is a bare DOI (or a doi.org URL), the extractor
 * performs an exact DOI resolution instead of a title query.
 *
 * New code of the metadata provider extension (phase 1: Crossref). Java 1.6
 * syntax only.
 */
public class CrossrefExtractor extends HtmlDataExtractor {

	private int providerTimeout = CrossrefProvider.DEFAULT_TIMEOUT;

	public CrossrefExtractor() {
	}

	public CrossrefExtractor(Map<ExtractorConfigKey, Object> config) throws MalformedConfigException {
		super(config);
		readTimeout(config);
	}

	public CrossrefExtractor(Map<ExtractorConfigKey, Object> config, MetaDataListener listener)
			throws MalformedConfigException {
		super(config, listener);
		readTimeout(config);
	}

	private void readTimeout(Map<ExtractorConfigKey, Object> config) {
		Object timeout = config == null ? null : config.get(CommonConfigKeys.TIMEOUT);
		if (timeout instanceof Integer) {
			this.providerTimeout = ((Integer) timeout).intValue();
		}
	}

	@Override
	protected void readConfig(Map<ExtractorConfigKey, Object> config) throws MalformedConfigException {
		super.readConfig(config);
		readTimeout(config);
	}

	public Collection<MetaData> search(final String query) {
		ArrayList<MetaData> result = new ArrayList<MetaData>();
		CrossrefProvider provider = new CrossrefProvider();
		provider.setTimeout(this.providerTimeout);

		try {
			String doi = CrossrefProvider.extractDoi(query);
			List<BibliographicMetadata> items;
			if (doi != null) {
				items = provider.resolveByDOI(doi);
			} else {
				items = provider.resolveByTitle(query, this.maxResults);
			}

			// score candidates against the query so the list order reflects
			// similarity, not just the provider's internal relevance order
			MetadataQuery metadataQuery = new MetadataQuery();
			metadataQuery.setTitle(query);
			if (doi != null) {
				metadataQuery.setDoi(doi);
			}
			ArrayList<MetadataCandidate> candidates = new ArrayList<MetadataCandidate>();
			for (int i = 0; i < items.size(); i++) {
				BibliographicMetadata metadata = items.get(i);
				if (metadata == null || metadata.getTitle() == null || metadata.getTitle().trim().length() == 0) {
					continue;
				}
				candidates.add(CandidateScorer.score(metadataQuery, metadata, provider.getProviderName()));
			}
			Collections.sort(candidates);

			int rank = 0;
			for (int i = 0; i < candidates.size(); i++) {
				MetadataCandidate candidate = candidates.get(i);
				result.add(new BibMetaData(rank, candidate.getMetadata(), candidate.getProvider(),
						CrossrefSource.CROSSREF, query));
				rank++;
			}
		} catch (IOException e) {
			// network down, timeout, HTTP error or malformed JSON - degrade
			// gracefully: log and fire an empty result (see class comment)
			logger.warn("Crossref retrieval failed: " + e.getMessage(), e);
		}

		FetchedResultsEvent event = new FetchedResultsEvent(result);
		for (MetaDataListener listener : this.getListeners()) {
			listener.onFinishedRequest(event);
		}
		return result;
	}

	public Collection<MetaData> call() throws Exception {
		return search(this.searchValue);
	}

}
