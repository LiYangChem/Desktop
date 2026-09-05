package org.docear.metadata.model;

import java.io.Serializable;

/**
 * A scored candidate result produced by a MetadataProvider. Similarity values
 * are in [0,1]; the value -1 means "not applicable / unknown" (e.g. the query
 * contained no author names, so author similarity cannot be computed).
 * yearMatch/doiMatch are Boolean: null = not applicable, TRUE = exact match,
 * FALSE = mismatch.
 *
 * Candidates are ordered by finalScore, descending.
 *
 * New code of the metadata provider extension (phase 1: Crossref). Java 1.6
 * syntax only.
 */
public class MetadataCandidate implements Serializable, Comparable<MetadataCandidate> {

	private static final long serialVersionUID = 1L;

	public static final double SIMILARITY_UNKNOWN = -1.0d;

	private final BibliographicMetadata metadata;
	private final String provider;
	private final double titleSimilarity;
	private final double authorSimilarity;
	private final Boolean yearMatch;
	private final Boolean doiMatch;
	private final double finalScore;

	public MetadataCandidate(BibliographicMetadata metadata, String provider, double titleSimilarity,
			double authorSimilarity, Boolean yearMatch, Boolean doiMatch, double finalScore) {
		this.metadata = metadata;
		this.provider = provider;
		this.titleSimilarity = titleSimilarity;
		this.authorSimilarity = authorSimilarity;
		this.yearMatch = yearMatch;
		this.doiMatch = doiMatch;
		this.finalScore = finalScore;
	}

	public BibliographicMetadata getMetadata() {
		return metadata;
	}

	public String getProvider() {
		return provider;
	}

	public double getTitleSimilarity() {
		return titleSimilarity;
	}

	public double getAuthorSimilarity() {
		return authorSimilarity;
	}

	public Boolean getYearMatch() {
		return yearMatch;
	}

	public Boolean getDoiMatch() {
		return doiMatch;
	}

	public double getFinalScore() {
		return finalScore;
	}

	public int compareTo(MetadataCandidate other) {
		if (other == null) {
			return -1;
		}
		if (this.finalScore > other.finalScore) {
			return -1;
		}
		if (this.finalScore < other.finalScore) {
			return 1;
		}
		return 0;
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("MetadataCandidate[provider=").append(provider);
		sb.append(", finalScore=").append(finalScore);
		sb.append(", titleSim=").append(titleSimilarity);
		sb.append(", authorSim=").append(authorSimilarity);
		sb.append(", yearMatch=").append(yearMatch);
		sb.append(", doiMatch=").append(doiMatch);
		sb.append(", title=").append(metadata == null ? null : metadata.getTitle());
		sb.append("]");
		return sb.toString();
	}
}
