package org.docear.metadata.adapter;

import org.docear.metadata.data.MetaDataSource;

/**
 * MetaDataSource marker for results retrieved via the Crossref API. Later
 * phases add sibling enums (DoiSource, PubMedSource, ...) in this package.
 *
 * New code of the metadata provider extension (phase 1: Crossref). Java 1.6
 * syntax only.
 */
public enum CrossrefSource implements MetaDataSource {

	CROSSREF;

}
