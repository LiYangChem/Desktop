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
import org.docear.metadata.providers.DOIProvider;

/**
 * Callable execution unit for DOI resolution. The mirror image of
 * CrossrefExtractor with one specialization: the DOI provider resolves
 * exactly - if the search string is not a bare DOI (or doi.org URL) the
 * extractor fires an empty FetchedResultsEvent without touching the network.
 *
 * The wizard's request counter depends on every engine firing exactly one
 * event per search, so the "not a DOI" case must also complete normally.
 *
 * New code of the metadata provider extension (phase 2: DOI). Java 1.6
 * syntax only.
 */
public class DoiExtractor extends HtmlDataExtractor {

	private int providerTimeout = DOIProvider.DEFAULT_TIMEOUT;

	public DoiExtractor() {
	}

	public DoiExtractor(Map<ExtractorConfigKey, Object> config) throws MalformedConfigException {
		super(config);
		readTimeout(config);
	}

	public DoiExtractor(Map<ExtractorConfigKey, Object> config, MetaDataListener listener)
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
		String doi = CrossrefProvider.extractDoi(query);

		if (doi != null) {
			DOIProvider provider = new DOIProvider();
			provider.setTimeout(this.providerTimeout);
			try {
				List<BibliographicMetadata> items = provider.resolveByDOI(doi);

				MetadataQuery metadataQuery = new MetadataQuery();
				metadataQuery.setTitle(query);
				metadataQuery.setDoi(doi);

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
							DoiSource.DOI, query));
					rank++;
				}
			} catch (IOException e) {
				// network down, timeout, HTTP error or malformed JSON -
				// degrade gracefully (see class comment)
				logger.warn("DOI resolution failed: " + e.getMessage(), e);
			}
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
