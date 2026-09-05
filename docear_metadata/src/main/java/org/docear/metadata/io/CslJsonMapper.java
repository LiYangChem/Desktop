package org.docear.metadata.io;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.docear.metadata.model.BibliographicMetadata;

/**
 * Shared CSL-JSON -> BibliographicMetadata mapper.
 *
 * Both the Crossref REST API ("message" / "items" members are a superset of
 * CSL-JSON) and the doi.org content negotiation response (plain CSL-JSON)
 * use the same field vocabulary (title, author, container-title, issued,
 * ...), so the mapping lives here exactly once. Field value shapes vary
 * slightly between agencies (title may be a string or an array, authors may
 * carry family/given or a single "literal" for organizational names) and
 * both variants are accepted.
 *
 * New code of the metadata provider extension (phase 2: DOI). Java 1.6
 * syntax only.
 */
public final class CslJsonMapper {

	private CslJsonMapper() {
	}

	/**
	 * Maps one CSL-JSON item (or Crossref work item) into a
	 * BibliographicMetadata. Returns null if the item has no usable title.
	 */
	public static BibliographicMetadata fromWorkItem(Map<String, Object> item) {
		if (item == null) {
			return null;
		}
		String title = firstString(item.get("title"));
		if (title == null || title.trim().length() == 0) {
			return null;
		}
		BibliographicMetadata metadata = new BibliographicMetadata();
		metadata.setTitle(title);
		metadata.setDoi(stringOrNull(item.get("DOI")));
		metadata.setJournal(firstString(item.get("container-title")));
		metadata.setVolume(stringOrNull(item.get("volume")));
		metadata.setIssue(stringOrNull(item.get("issue")));
		metadata.setPages(stringOrNull(item.get("page")));
		metadata.setPublisher(stringOrNull(item.get("publisher")));
		metadata.setUrl(stringOrNull(item.get("URL")));
		metadata.setPublicationType(stringOrNull(item.get("type")));
		metadata.setYear(extractYear(item.get("issued")));
		metadata.setAbstractText(cleanAbstract(stringOrNull(item.get("abstract"))));

		List<Object> authors = JsonReader.asList(item.get("author"));
		if (authors != null) {
			for (Iterator<Object> it = authors.iterator(); it.hasNext();) {
				Map<String, Object> author = JsonReader.asMap(it.next());
				if (author == null) {
					continue;
				}
				String family = stringOrNull(author.get("family"));
				String given = stringOrNull(author.get("given"));
				if (family != null && family.trim().length() > 0) {
					if (given != null && given.trim().length() > 0) {
						metadata.addAuthor(family.trim() + ", " + given.trim());
					} else {
						metadata.addAuthor(family.trim());
					}
				} else if (given != null && given.trim().length() > 0) {
					metadata.addAuthor(given.trim());
				} else {
					// DataCite records often use {"literal": "Org Name"}
					// for organizational authors
					String literal = stringOrNull(author.get("literal"));
					if (literal != null) {
						metadata.addAuthor("{" + literal.trim() + "}");
					}
				}
			}
		}
		return metadata;
	}

	private static Integer extractYear(Object issued) {
		Map<String, Object> issuedMap = JsonReader.asMap(issued);
		if (issuedMap == null) {
			return null;
		}
		List<Object> dateParts = JsonReader.asList(issuedMap.get("date-parts"));
		if (dateParts == null || dateParts.isEmpty()) {
			return null;
		}
		List<Object> firstPart = JsonReader.asList(dateParts.get(0));
		if (firstPart == null || firstPart.isEmpty()) {
			return null;
		}
		return JsonReader.asInteger(firstPart.get(0));
	}

	/**
	 * Crossref abstracts are JATS XML fragments; strip the tags and unescape
	 * the most common entities.
	 */
	private static String cleanAbstract(String abstractText) {
		if (abstractText == null) {
			return null;
		}
		String cleaned = abstractText.replaceAll("<[^>]+>", " ");
		cleaned = cleaned.replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&quot;", "\"")
				.replaceAll("&apos;", "'").replaceAll("&amp;", "&");
		cleaned = cleaned.replaceAll("\\s+", " ").trim();
		if (cleaned.length() == 0) {
			return null;
		}
		return cleaned;
	}

	private static String firstString(Object value) {
		List<Object> list = JsonReader.asList(value);
		if (list != null && !list.isEmpty()) {
			return stringOrNull(list.get(0));
		}
		return stringOrNull(value);
	}

	private static String stringOrNull(Object value) {
		String s = JsonReader.asString(value);
		if (s == null || s.trim().length() == 0) {
			return null;
		}
		return s.trim();
	}
}
