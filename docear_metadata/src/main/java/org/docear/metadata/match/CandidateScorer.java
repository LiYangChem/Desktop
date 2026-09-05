package org.docear.metadata.match;

import org.docear.metadata.model.BibliographicMetadata;
import org.docear.metadata.model.MetadataCandidate;
import org.docear.metadata.model.MetadataQuery;

/**
 * Turns a (query, metadata) pair into a scored MetadataCandidate. Components
 * that cannot be computed (e.g. author similarity when the query has no
 * authors) are excluded and the remaining weights are re-normalized, so a
 * title-only query never gets diluted by unknown components.
 *
 * The score only influences the ORDER of candidates; it never auto-selects an
 * entry - the user still confirms manually.
 *
 * New code of the metadata provider extension (phase 1: Crossref). Java 1.6
 * syntax only.
 */
public final class CandidateScorer {

	public static final double TITLE_WEIGHT = 0.5d;
	public static final double AUTHOR_WEIGHT = 0.2d;
	public static final double YEAR_WEIGHT = 0.15d;
	public static final double DOI_WEIGHT = 0.15d;

	private CandidateScorer() {
	}

	public static MetadataCandidate score(MetadataQuery query, BibliographicMetadata metadata, String provider) {
		double titleSim = MetadataCandidate.SIMILARITY_UNKNOWN;
		double authorSim = MetadataCandidate.SIMILARITY_UNKNOWN;
		Boolean yearMatch = null;
		Boolean doiMatch = null;

		if (query != null && query.hasTitle() && metadata != null && metadata.getTitle() != null) {
			titleSim = SimilarityCalculator.titleSimilarity(query.getTitle(), metadata.getTitle());
		}
		if (query != null && query.hasAuthors() && metadata != null && metadata.getAuthors() != null
				&& !metadata.getAuthors().isEmpty()) {
			authorSim = SimilarityCalculator.authorSimilarity(query.getAuthors(), metadata.getAuthors());
		}
		if (query != null && query.hasYear() && metadata != null && metadata.getYear() != null) {
			yearMatch = Boolean.valueOf(query.getYear().intValue() == metadata.getYear().intValue());
		}
		if (query != null && query.hasDoi() && metadata != null && metadata.getDoi() != null) {
			String queryDoi = TextNormalizer.normalizeDoi(query.getDoi());
			String metaDoi = TextNormalizer.normalizeDoi(metadata.getDoi());
			doiMatch = Boolean.valueOf(queryDoi != null && queryDoi.equals(metaDoi));
		}

		double finalScore = weightedScore(titleSim, authorSim, yearMatch, doiMatch);
		return new MetadataCandidate(metadata, provider, titleSim, authorSim, yearMatch, doiMatch, finalScore);
	}

	private static double weightedScore(double titleSim, double authorSim, Boolean yearMatch, Boolean doiMatch) {
		double score = 0.0d;
		double weightSum = 0.0d;

		if (titleSim >= 0.0d) {
			score += TITLE_WEIGHT * titleSim;
			weightSum += TITLE_WEIGHT;
		}
		if (authorSim >= 0.0d) {
			score += AUTHOR_WEIGHT * authorSim;
			weightSum += AUTHOR_WEIGHT;
		}
		if (yearMatch != null) {
			score += YEAR_WEIGHT * (yearMatch.booleanValue() ? 1.0d : 0.0d);
			weightSum += YEAR_WEIGHT;
		}
		if (doiMatch != null) {
			score += DOI_WEIGHT * (doiMatch.booleanValue() ? 1.0d : 0.0d);
			weightSum += DOI_WEIGHT;
		}
		if (weightSum <= 0.0d) {
			return 0.0d;
		}
		return score / weightSum;
	}
}
