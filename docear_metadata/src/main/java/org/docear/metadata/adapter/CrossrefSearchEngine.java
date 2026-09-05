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
import org.docear.metadata.providers.CrossrefProvider;

/**
 * Bridge between the existing MetaDataSearchHub dispatcher and the
 * CrossrefProvider. Registered in MetaDataExtractorPage.preparePage() next to
 * (not instead of) GoogleScholarSearchEngine; selected via
 * setupSources() when the user checks the Crossref option.
 *
 * Unlike GoogleScholarSearchEngine this bridge builds a fresh merged config
 * map instead of mutating the shared engine config (the original engine
 * shares one mutable map across queries - a known quirk that is deliberately
 * left untouched there but not replicated here).
 *
 * New code of the metadata provider extension (phase 1: Crossref). Java 1.6
 * syntax only.
 */
public class CrossrefSearchEngine extends SearchEngine {

	public CrossrefSearchEngine(Map<ExtractorConfigKey, Object> config) {
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
		// Crossref cold starts can easily exceed the 3s default of
		// HtmlDataExtractor; raise the timeout unless the caller configured
		// one explicitly.
		if (queryConfig.get(CommonConfigKeys.TIMEOUT) == null) {
			queryConfig.put(CommonConfigKeys.TIMEOUT, Integer.valueOf(CrossrefProvider.DEFAULT_TIMEOUT));
		}
		return new CrossrefExtractor(queryConfig, listener);
	}

}
