package org.docear.metadata.match;

import java.util.Locale;

/**
 * Text normalization for title/author comparison. This capability is
 * completely absent from the original Google Scholar implementation (which
 * compared nothing at all) and is a prerequisite for meaningful candidate
 * scoring.
 *
 * New code of the metadata provider extension (phase 1: Crossref). Java 1.6
 * syntax only.
 */
public final class TextNormalizer {

	private TextNormalizer() {
	}

	/**
	 * Normalizes a title for comparison: lower case, every non letter/digit
	 * sequence collapsed to a single space, trimmed, leading English article
	 * ("a", "an", "the") removed. Registries are inconsistent about leading
	 * articles and leaving it in penalizes otherwise identical titles.
	 */
	public static String normalizeTitle(String title) {
		if (title == null) {
			return "";
		}
		String normalized = title.toLowerCase(Locale.ENGLISH);
		normalized = normalized.replaceAll("[^\\p{L}\\p{N}]+", " ");
		normalized = normalized.trim().replaceAll("\\s+", " ");
		if (normalized.startsWith("the ") || normalized.startsWith("a ") || normalized.startsWith("an ")) {
			normalized = normalized.substring(normalized.indexOf(' ') + 1);
		}
		return normalized;
	}

	/**
	 * Extracts the family name of an author given either as "Family, Given" or
	 * as "Given Family", normalized to lower case.
	 */
	public static String familyName(String author) {
		if (author == null) {
			return "";
		}
		String a = author.trim();
		if (a.length() == 0) {
			return "";
		}
		int comma = a.indexOf(',');
		if (comma >= 0) {
			return normalizeNamePart(a.substring(0, comma));
		}
		// "Given Family" - take the last token as the family name
		String normalized = normalizeNamePart(a);
		int lastSpace = normalized.lastIndexOf(' ');
		if (lastSpace >= 0) {
			return normalized.substring(lastSpace + 1);
		}
		return normalized;
	}

	/**
	 * Normalizes a person name for comparison: lower case, hyphens and
	 * apostrophes removed without splitting the token (so "Berners-Lee"
	 * stays one name, not two), remaining non letter characters collapsed
	 * to a single space, diacritics kept as letters.
	 */
	public static String normalizeNamePart(String name) {
		if (name == null) {
			return "";
		}
		return name.toLowerCase(Locale.ENGLISH).replaceAll("['\u2019-]+", "").replaceAll("[^\\p{L}]+", " ").trim()
				.replaceAll("\\s+", " ");
	}

	/**
	 * Normalizes a DOI: strips common prefixes (doi:, https://doi.org/,
	 * https://dx.doi.org/) and lower cases.
	 */
	public static String normalizeDoi(String doi) {
		if (doi == null) {
			return null;
		}
		String d = doi.trim().toLowerCase(Locale.ENGLISH);
		if (d.startsWith("doi:")) {
			d = d.substring(4).trim();
		}
		String[] prefixes = new String[] { "https://doi.org/", "http://doi.org/", "https://dx.doi.org/",
				"http://dx.doi.org/" };
		for (int i = 0; i < prefixes.length; i++) {
			if (d.startsWith(prefixes[i])) {
				d = d.substring(prefixes[i].length());
				break;
			}
		}
		return d;
	}
}
