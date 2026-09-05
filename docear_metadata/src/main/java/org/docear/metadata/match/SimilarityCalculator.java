package org.docear.metadata.match;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Similarity metrics used for candidate scoring. Pure static functions without
 * any external dependency so they can be unit tested in isolation.
 *
 * Title similarity is a hybrid of token-set Jaccard (order independent, robust
 * against reworded subtitles) and normalized Levenshtein distance (order
 * sensitive, robust against one token typos). Author similarity compares the
 * sets of family names (Jaccard), because given names are abbreviated
 * inconsistently across registries.
 *
 * New code of the metadata provider extension (phase 1: Crossref). Java 1.6
 * syntax only.
 */
public final class SimilarityCalculator {

	private SimilarityCalculator() {
	}

	/**
	 * Similarity of two titles in [0,1]. 0.6 * tokenJaccard + 0.4 *
	 * levenshteinRatio on the normalized strings.
	 */
	public static double titleSimilarity(String titleA, String titleB) {
		String a = TextNormalizer.normalizeTitle(titleA);
		String b = TextNormalizer.normalizeTitle(titleB);
		if (a.length() == 0 || b.length() == 0) {
			return 0.0d;
		}
		if (a.equals(b)) {
			return 1.0d;
		}
		double jaccard = tokenJaccard(a, b);
		double lev = levenshteinRatio(a, b);
		return 0.6d * jaccard + 0.4d * lev;
	}

	/**
	 * Similarity of two author lists in [0,1], based on family name sets.
	 * Returns -1 if either list is empty (similarity unknown).
	 */
	public static double authorSimilarity(List<String> authorsA, List<String> authorsB) {
		if (authorsA == null || authorsB == null || authorsA.isEmpty() || authorsB.isEmpty()) {
			return -1.0d;
		}
		Set<String> setA = familyNameSet(authorsA);
		Set<String> setB = familyNameSet(authorsB);
		if (setA.isEmpty() || setB.isEmpty()) {
			return -1.0d;
		}
		Set<String> intersection = new HashSet<String>(setA);
		intersection.retainAll(setB);
		Set<String> union = new HashSet<String>(setA);
		union.addAll(setB);
		return ((double) intersection.size()) / ((double) union.size());
	}

	private static Set<String> familyNameSet(List<String> authors) {
		Set<String> set = new HashSet<String>();
		for (int i = 0; i < authors.size(); i++) {
			String family = TextNormalizer.familyName(authors.get(i));
			if (family.length() > 0) {
				set.add(family);
			}
		}
		return set;
	}

	private static double tokenJaccard(String normalizedA, String normalizedB) {
		Set<String> setA = new HashSet<String>(Arrays.asList(normalizedA.split(" ")));
		Set<String> setB = new HashSet<String>(Arrays.asList(normalizedB.split(" ")));
		Set<String> intersection = new HashSet<String>(setA);
		intersection.retainAll(setB);
		Set<String> union = new HashSet<String>(setA);
		union.addAll(setB);
		if (union.isEmpty()) {
			return 0.0d;
		}
		return ((double) intersection.size()) / ((double) union.size());
	}

	/**
	 * Levenshtein distance of two strings divided by the longer length,
	 * subtracted from 1 (= 1.0 for identical strings).
	 */
	public static double levenshteinRatio(String a, String b) {
		if (a == null || b == null) {
			return 0.0d;
		}
		if (a.equals(b)) {
			return 1.0d;
		}
		int maxLen = Math.max(a.length(), b.length());
		if (maxLen == 0) {
			return 1.0d;
		}
		return 1.0d - ((double) levenshtein(a, b)) / ((double) maxLen);
	}

	private static int levenshtein(String a, String b) {
		int n = a.length();
		int m = b.length();
		if (n == 0) {
			return m;
		}
		if (m == 0) {
			return n;
		}
		int[] prev = new int[m + 1];
		int[] curr = new int[m + 1];
		for (int j = 0; j <= m; j++) {
			prev[j] = j;
		}
		for (int i = 1; i <= n; i++) {
			curr[0] = i;
			for (int j = 1; j <= m; j++) {
				int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
				int min = prev[j] + 1;
				if (curr[j - 1] + 1 < min) {
					min = curr[j - 1] + 1;
				}
				if (prev[j - 1] + cost < min) {
					min = prev[j - 1] + cost;
				}
				curr[j] = min;
			}
			int[] swap = prev;
			prev = curr;
			curr = swap;
		}
		return prev[m];
	}
}
