package org.docear.metadata.io;

import java.util.Iterator;
import java.util.List;

import org.docear.metadata.model.BibliographicMetadata;

/**
 * Converts a BibliographicMetadata into a plain BibTeX entry string. The
 * generated BibTeX is consumed by the existing JabRef BibtexParser chain
 * (MetaDataExtractorPage -> BibtexParser.parse -> BibtexEntry), which is why
 * the new providers produce ScholarMetaData-compatible objects carrying this
 * string (see org.docear.metadata.adapter.BibMetaData).
 *
 * New code of the metadata provider extension (phase 1: Crossref). Java 1.6
 * syntax only.
 */
public final class Metadata2BibtexConverter {

	private Metadata2BibtexConverter() {
	}

	public static String toBibtex(BibliographicMetadata metadata) {
		if (metadata == null) {
			return "";
		}
		String entryType = toEntryType(metadata.getPublicationType());
		String key = makeCitationKey(metadata);

		StringBuffer sb = new StringBuffer();
		sb.append("@").append(entryType).append("{").append(key).append(",\n");
		appendField(sb, "title", metadata.getTitle());
		if (metadata.getAuthors() != null && !metadata.getAuthors().isEmpty()) {
			appendField(sb, "author", joinAuthors(metadata.getAuthors()));
		}
		if (isArticleLike(entryType)) {
			appendField(sb, "journal", metadata.getJournal());
		} else {
			appendField(sb, "booktitle", metadata.getJournal());
		}
		if (metadata.getYear() != null) {
			appendField(sb, "year", String.valueOf(metadata.getYear()));
		}
		appendField(sb, "volume", metadata.getVolume());
		appendField(sb, "number", metadata.getIssue());
		appendField(sb, "pages", normalizePages(metadata.getPages()));
		appendField(sb, "doi", metadata.getDoi());
		appendField(sb, "url", metadata.getUrl());
		if (isArticleLike(entryType) || "book".equals(entryType)) {
			appendField(sb, "publisher", metadata.getPublisher());
		}
		appendField(sb, "abstract", metadata.getAbstractText());
		sb.append("}");
		return sb.toString();
	}

	/**
	 * Maps a provider publication type to a BibTeX entry type. Accepts both
	 * the Crossref API vocabulary ("journal-article", "book-chapter", ...)
	 * and the CSL vocabulary returned by doi.org content negotiation
	 * ("article-journal", "chapter", "paper-conference", ...).
	 */
	static String toEntryType(String publicationType) {
		if (publicationType == null) {
			return "misc";
		}
		String type = publicationType.trim().toLowerCase();
		if ("journal-article".equals(type) || "article-journal".equals(type)) {
			return "article";
		}
		if ("book".equals(type)) {
			return "book";
		}
		if ("book-chapter".equals(type) || "book-part".equals(type) || "book-section".equals(type)
				|| "chapter".equals(type)) {
			return "incollection";
		}
		if ("proceedings-article".equals(type) || "paper-conference".equals(type)) {
			return "inproceedings";
		}
		if ("report".equals(type) || "report-component".equals(type)) {
			return "techreport";
		}
		if ("dissertation".equals(type) || "thesis".equals(type)) {
			return "phdthesis";
		}
		if ("posted-content".equals(type)) {
			return "unpublished";
		}
		// BibTeX vocabulary (round-trip of adapted Google Scholar results,
		// whose publicationType carries the raw BibTeX entry type)
		if ("article".equals(type) || "inproceedings".equals(type) || "incollection".equals(type)
				|| "techreport".equals(type) || "unpublished".equals(type) || "phdthesis".equals(type)) {
			return type;
		}
		if ("mastersthesis".equals(type)) {
			return "mastersthesis";
		}
		return "misc";
	}

	/** Test hook: same mapping as above. */
	public static String mapEntryType(String publicationType) {
		return toEntryType(publicationType);
	}

	private static boolean isArticleLike(String entryType) {
		return "article".equals(entryType) || "inproceedings".equals(entryType) || "incollection".equals(entryType);
	}

	/**
	 * Derives a citation key in the well known "familyyear" / "familyetalyear"
	 * style. Characters that are illegal in BibTeX keys are removed.
	 */
	static String makeCitationKey(BibliographicMetadata metadata) {
		String family = "";
		if (metadata.getAuthors() != null && !metadata.getAuthors().isEmpty()) {
			String first = metadata.getAuthors().get(0);
			int comma = first.indexOf(',');
			if (comma > 0) {
				family = first.substring(0, comma).trim();
			} else {
				String trimmed = first.trim();
				int lastSpace = trimmed.lastIndexOf(' ');
				if (lastSpace >= 0) {
					family = trimmed.substring(lastSpace + 1);
				} else {
					family = trimmed;
				}
			}
		}
		family = sanitizeKeyPart(family);
		if (family.length() == 0 && metadata.getTitle() != null) {
			// fall back to the first meaningful word of the title; leading
			// articles ("The Semantic Web") would produce the useless key "the"
			String[] words = metadata.getTitle().trim().split("\\s+");
			int start = 0;
			while (start < words.length && isArticle(words[start])) {
				start++;
			}
			if (start < words.length) {
				family = sanitizeKeyPart(words[start]);
			}
		}
		String year = metadata.getYear() != null ? String.valueOf(metadata.getYear()) : "";
		StringBuffer key = new StringBuffer(family);
		if (metadata.getAuthors() != null && metadata.getAuthors().size() > 1) {
			key.append("etal");
		}
		key.append(year);
		if (key.length() == 0) {
			key.append("docear");
		}
		return key.toString();
	}

	private static boolean isArticle(String word) {
		if (word == null) {
			return false;
		}
		String w = word.trim().toLowerCase();
		return "the".equals(w) || "a".equals(w) || "an".equals(w);
	}

	private static String sanitizeKeyPart(String part) {
		if (part == null) {
			return "";
		}
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < part.length(); i++) {
			char c = part.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
				sb.append(Character.toLowerCase(c));
			}
		}
		return sb.toString();
	}

	private static String joinAuthors(List<String> authors) {
		StringBuffer sb = new StringBuffer();
		Iterator<String> it = authors.iterator();
		while (it.hasNext()) {
			if (sb.length() > 0) {
				sb.append(" and ");
			}
			sb.append(it.next());
		}
		return sb.toString();
	}

	/**
	 * Crossref uses "firstpage-lastpage" in the page field; BibTeX convention
	 * is "firstpage--lastpage".
	 */
	private static String normalizePages(String pages) {
		if (pages == null) {
			return null;
		}
		String p = pages.trim();
		if (p.length() == 0) {
			return null;
		}
		int dash = p.indexOf('-');
		if (dash > 0 && p.indexOf("--") < 0) {
			return p.substring(0, dash) + "--" + p.substring(dash + 1);
		}
		return p;
	}

	private static void appendField(StringBuffer sb, String field, String value) {
		if (value == null) {
			return;
		}
		String trimmed = value.trim();
		if (trimmed.length() == 0) {
			return;
		}
		sb.append("  ").append(field).append(" = {").append(escapeValue(trimmed)).append("},\n");
	}

	/**
	 * Escapes braces and backslashes so the value survives a round trip
	 * through the JabRef BibtexParser.
	 */
	private static String escapeValue(String value) {
		StringBuffer sb = new StringBuffer(value.length() + 16);
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '{' || c == '}' || c == '\\') {
				sb.append('\\');
			}
			sb.append(c);
		}
		return sb.toString();
	}
}
