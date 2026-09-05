package org.docear.metadata.adapter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.docear.metadata.data.MetaData;
import org.docear.metadata.engines.SearchEngine;
import org.docear.metadata.events.MetaDataListener;
import org.docear.metadata.extractors.ExtractorConfigKey;
import org.docear.metadata.extractors.MalformedConfigException;
import org.docear.metadata.extractors.HtmlDataExtractor.CommonConfigKeys;
import org.docear.metadata.providers.DOIProvider;

/**
 * Bridge between the existing MetaDataSearchHub dispatcher and the
 * DOIProvider. Registered in MetaDataExtractorPage.preparePage() next to
 * GoogleScholarSearchEngine and CrossrefSearchEngine; selected via
 * setupSources() when the user checks the DOI option.
 *
 * Structurally identical to CrossrefSearchEngine: fresh merged config map
 * (never mutates the shared engine config), raised default timeout.
 *
 * New code of the metadata provider extension (phase 2: DOI). Java 1.6
 * syntax only.
 */
public class DoiSearchEngine extends SearchEngine {

	public DoiSearchEngine(Map<ExtractorConfigKey, Object> config) {
		super(config);
	}

	@Override
	public Callable<Collection<MetaData>> getExtractor(String query, Map<ExtractorConfigKey, Object> options,
			MetaDataListener listener) throws MalformedConfigException {
		Map<ExtractorConfigKey, Object> queryConfig = new HashMap<ExtractorConfigKey, Object>();
		if (this.config != null) {
			queryConfig.putAll(this.config);
		}
		if (options != null) {
			queryConfig.putAll(options);
		}
		queryConfig.put(CommonConfigKeys.SEARCHVALUE, query);
		if (queryConfig.get(CommonConfigKeys.TIMEOUT) == null) {
			queryConfig.put(CommonConfigKeys.TIMEOUT, Integer.valueOf(DOIProvider.DEFAULT_TIMEOUT));
		}
		return new DoiExtractor(queryConfig, listener);
	}

}
