package org.docear.metadata.adapter;

import org.docear.metadata.data.MetaDataSource;

/**
 * MetaDataSource marker for results retrieved via DOI content negotiation
 * (doi.org). Results with this source were resolved by an exact DOI match
 * through whatever registration agency owns the DOI.
 *
 * New code of the metadata provider extension (phase 2: DOI). Java 1.6
 * syntax only.
 */
public enum DoiSource implements MetaDataSource {

	DOI;

}
