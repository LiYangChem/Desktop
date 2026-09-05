package org.docear.metadata.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Query context for a metadata retrieval: what the user/system expects the
 * result to look like. Used both for provider dispatch (supports()) and for
 * candidate scoring (CandidateScorer).
 *
 * New code of the metadata provider extension (phase 1: Crossref). Java 1.6
 * syntax only.
 */
public class MetadataQuery implements Serializable {

	private static final long serialVersionUID = 1L;

	private String title;
	private List<String> authors = new ArrayList<String>();
	private Integer year;
	private String doi;

	public MetadataQuery() {
	}

	public MetadataQuery(String title) {
		this.title = title;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public List<String> getAuthors() {
		return authors;
	}

	public void setAuthors(List<String> authors) {
		this.authors = authors;
	}

	public Integer getYear() {
		return year;
	}

	public void setYear(Integer year) {
		this.year = year;
	}

	public String getDoi() {
		return doi;
	}

	public void setDoi(String doi) {
		this.doi = doi;
	}

	public boolean hasTitle() {
		return title != null && title.trim().length() > 0;
	}

	public boolean hasAuthors() {
		return authors != null && !authors.isEmpty();
	}

	public boolean hasYear() {
		return year != null;
	}

	public boolean hasDoi() {
		return doi != null && doi.trim().length() > 0;
	}
}
