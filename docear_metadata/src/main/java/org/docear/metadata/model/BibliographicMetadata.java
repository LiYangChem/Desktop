package org.docear.metadata.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified structured bibliographic metadata. This is an intermediate model that
 * only lives during the retrieval/candidate phase. Once the user picks a
 * candidate it is converted to BibTeX (see
 * org.docear.metadata.io.Metadata2BibtexConverter) and handed to the existing
 * JabRef parser chain. It is NOT a storage object.
 *
 * New code of the metadata provider extension (phase 1: Crossref). Java 1.6
 * syntax only.
 */
public class BibliographicMetadata implements Serializable {

	private static final long serialVersionUID = 1L;

	private String title;
	private List<String> authors = new ArrayList<String>();
	private String journal;
	private Integer year;
	private String volume;
	private String issue;
	private String pages;
	private String doi;
	private String url;
	private String publisher;
	private String abstractText;
	private String publicationType;

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

	public void addAuthor(String author) {
		if (author != null && author.trim().length() > 0) {
			this.authors.add(author.trim());
		}
	}

	public String getJournal() {
		return journal;
	}

	public void setJournal(String journal) {
		this.journal = journal;
	}

	public Integer getYear() {
		return year;
	}

	public void setYear(Integer year) {
		this.year = year;
	}

	public String getVolume() {
		return volume;
	}

	public void setVolume(String volume) {
		this.volume = volume;
	}

	public String getIssue() {
		return issue;
	}

	public void setIssue(String issue) {
		this.issue = issue;
	}

	public String getPages() {
		return pages;
	}

	public void setPages(String pages) {
		this.pages = pages;
	}

	public String getDoi() {
		return doi;
	}

	public void setDoi(String doi) {
		this.doi = doi;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getPublisher() {
		return publisher;
	}

	public void setPublisher(String publisher) {
		this.publisher = publisher;
	}

	public String getAbstractText() {
		return abstractText;
	}

	public void setAbstractText(String abstractText) {
		this.abstractText = abstractText;
	}

	public String getPublicationType() {
		return publicationType;
	}

	public void setPublicationType(String publicationType) {
		this.publicationType = publicationType;
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("BibliographicMetadata[");
		sb.append("title=").append(title);
		sb.append(", authors=").append(authors);
		sb.append(", journal=").append(journal);
		sb.append(", year=").append(year);
		sb.append(", doi=").append(doi);
		sb.append(", type=").append(publicationType);
		sb.append("]");
		return sb.toString();
	}
}
