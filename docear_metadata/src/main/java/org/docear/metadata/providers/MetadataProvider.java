package org.docear.metadata.providers;

import java.io.IOException;
import java.util.List;

import org.docear.metadata.data.MetaDataSource;
import org.docear.metadata.model.BibliographicMetadata;
import org.docear.metadata.model.MetadataQuery;

/**
 * Unified query contract for metadata providers. Phase 1 ships
 * CrossrefProvider; DOIProvider, PubMedProvider and OpenAlexProvider are
 * planned for later phases (each: one class in this package plus one thin
 * SearchEngine bridge in org.docear.metadata.adapter).
 *
 * New code of the metadata provider extension (phase 1: Crossref). Java 1.6
 * syntax only.
 */
public interface MetadataProvider {

	/** Human readable provider name, e.g. "Crossref" (UI label). */
	String getProviderName();

	/** The MetaDataSource marker of this provider. */
	MetaDataSource getSource();

	/** Capability negotiation: can this provider answer the given query? */
	boolean supports(MetadataQuery query);

	/** Exact resolution by DOI. Usually returns 0 or 1 entries. */
	List<BibliographicMetadata> resolveByDOI(String doi) throws IOException;

	/** Free-text / title search, ordered by provider relevance. */
	List<BibliographicMetadata> resolveByTitle(String title, int maxResults) throws IOException;

	/** Author search. Not yet exposed in the UI (reserved for later phases). */
	List<BibliographicMetadata> resolveByAuthors(List<String> authors, int maxResults) throws IOException;

	/** Title + publication year search (reduces false positives). */
	List<BibliographicMetadata> resolveByTitleAndYear(String title, int year, int maxResults) throws IOException;
}
