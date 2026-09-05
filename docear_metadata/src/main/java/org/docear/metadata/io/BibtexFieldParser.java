package org.docear.metadata.io;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal read-only BibTeX field extraction.
 *
 * Parses a single "@type{key, field = {value}, ...}" entry and returns the
 * fields as a plain name -> value map. It deliberately does NOT rebuild a
 * full entry model: its only consumer (GoogleScholarProvider) needs the
 * raw field values to build a BibliographicMetadata. The library must not
 * depend on the JabRef fork (dependency direction), so this is a small
 * hand-rolled parser instead of BibtexParser.
 *
 * Supported value forms: {...} with nested/escaped braces, "..." with
 * escaped quotes, bare tokens, and "#" concatenation of any of these.
 * The outer delimiters are stripped; inner braces are kept as-is (the
 * text normalizers of the match package strip them during scoring).
 *
 * New code of the metadata provider extension (phase 3: Google Scholar
 * adapter). Java 1.6 syntax only.
 */
public final class BibtexFieldParser {

	private BibtexFieldParser() {
	}

	/**
	 * Returns the entry type between '@' and the opening delimiter, e.g.
	 * "article" for "@article{key,...}", or null if the string contains no
	 * entry.
	 */
	public static String getEntryType(String bibtex) {
		if (bibtex == null) {
			return null;
		}
		int at = bibtex.indexOf('@');
		if (at < 0) {
			return null;
		}
		StringBuffer type = new StringBuffer();
		for (int i = at + 1; i < bibtex.length(); i++) {
			char c = bibtex.charAt(i);
			if (c == '{' || c == '(') {
				break;
			}
			if (Character.isWhitespace(c)) {
				continue;
			}
			type.append(c);
		}
		if (type.length() == 0) {
			return null;
		}
		return type.toString().trim().toLowerCase();
	}

	/**
	 * Returns the citation key of the first entry, or null if there is none.
	 */
	public static String getCitationKey(String bibtex) {
		if (bibtex == null) {
			return null;
		}
		int at = bibtex.indexOf('@');
		if (at < 0) {
			return null;
		}
		int open = bibtex.indexOf('{', at);
		int openAlt = bibtex.indexOf('(', at);
		if (open < 0 || (openAlt >= 0 && openAlt < open)) {
			open = openAlt;
		}
		if (open < 0) {
			return null;
		}
		int comma = bibtex.indexOf(',', open);
		int close = bibtex.indexOf('}', open);
		int end = comma;
		if (end < 0 || (close >= 0 && close < end)) {
			end = close;
		}
		if (end < 0) {
			end = bibtex.length();
		}
		String key = bibtex.substring(open + 1, end).trim();
		if (key.length() == 0) {
			return null;
		}
		return key;
	}

	/**
	 * Parses the fields of the first entry into a map with lowercased field
	 * names. Returns an empty map if the string is not a parsable entry.
	 */
	public static Map<String, String> parseFields(String bibtex) {
		Map<String, String> fields = new LinkedHashMap<String, String>();
		if (bibtex == null) {
			return fields;
		}
		int at = bibtex.indexOf('@');
		if (at < 0) {
			return fields;
		}
		int pos = skipWhitespace(bibtex, at + 1);
		// skip the entry type
		while (pos < bibtex.length() && bibtex.charAt(pos) != '{' && bibtex.charAt(pos) != '(') {
			pos++;
		}
		if (pos >= bibtex.length()) {
			return fields;
		}
		char closeDelimiter = bibtex.charAt(pos) == '{' ? '}' : ')';
		pos = skipWhitespace(bibtex, pos + 1);
		// skip the citation key up to the first comma (or the closing
		// delimiter for malformed input without fields)
		while (pos < bibtex.length() && bibtex.charAt(pos) != ',' && bibtex.charAt(pos) != closeDelimiter) {
			pos++;
		}
		while (pos < bibtex.length()) {
			pos = skipDelimiters(bibtex, pos);
			if (pos >= bibtex.length() || bibtex.charAt(pos) == closeDelimiter) {
				break;
			}
			// field name
			StringBuffer name = new StringBuffer();
			while (pos < bibtex.length()) {
				char c = bibtex.charAt(pos);
				if (c == '=' || c == ',' || c == closeDelimiter || Character.isWhitespace(c)) {
					break;
				}
				name.append(c);
				pos++;
			}
			pos = skipWhitespace(bibtex, pos);
			if (pos >= bibtex.length() || bibtex.charAt(pos) != '=' || name.length() == 0) {
				// malformed - skip to the next comma and try to resync
				while (pos < bibtex.length() && bibtex.charAt(pos) != ',') {
					pos++;
				}
				continue;
			}
			pos = skipWhitespace(bibtex, pos + 1);
			// value (possibly concatenated with '#')
			StringBuffer value = new StringBuffer();
			while (pos < bibtex.length()) {
				appendValue(bibtex, pos, value);
				pos = endOfValue(bibtex, pos);
				int next = skipWhitespace(bibtex, pos);
				if (next < bibtex.length() && bibtex.charAt(next) == '#') {
					pos = skipWhitespace(bibtex, next + 1);
					continue;
				}
				pos = next;
				break;
			}
			String cleaned = value.toString().trim();
			if (cleaned.length() == 0) {
				cleaned = "";
			}
			fields.put(name.toString().toLowerCase(), cleaned);
		}
		return fields;
	}

	// ------------------------------------------------------------------
	// value scanning helpers
	// ------------------------------------------------------------------

	/**
	 * Appends the value starting at {@code pos} (excluding its delimiters) to
	 * {@code out}. Does not advance any position - use endOfValue() for that.
	 */
	private static void appendValue(String s, int pos, StringBuffer out) {
		if (pos >= s.length()) {
			return;
		}
		char c = s.charAt(pos);
		if (c == '{') {
			int depth = 1;
			int i = pos + 1;
			while (i < s.length() && depth > 0) {
				char d = s.charAt(i);
				if (d == '\\' && i + 1 < s.length()) {
					out.append(d);
					out.append(s.charAt(i + 1));
					i += 2;
					continue;
				}
				if (d == '{') {
					depth++;
				} else if (d == '}') {
					depth--;
					if (depth == 0) {
						break;
					}
				}
				out.append(d);
				i++;
			}
		} else if (c == '"') {
			int i = pos + 1;
			while (i < s.length()) {
				char d = s.charAt(i);
				if (d == '\\' && i + 1 < s.length()) {
					out.append(d);
					out.append(s.charAt(i + 1));
					i += 2;
					continue;
				}
				if (d == '"') {
					break;
				}
				out.append(d);
				i++;
			}
		} else {
			int i = pos;
			while (i < s.length()) {
				char d = s.charAt(i);
				if (d == ',' || d == '}' || d == ')' || Character.isWhitespace(d)) {
					break;
				}
				out.append(d);
				i++;
			}
		}
	}

	/** Returns the index just after the value starting at {@code pos}. */
	private static int endOfValue(String s, int pos) {
		if (pos >= s.length()) {
			return pos;
		}
		char c = s.charAt(pos);
		if (c == '{') {
			int depth = 1;
			int i = pos + 1;
			while (i < s.length()) {
				char d = s.charAt(i);
				if (d == '\\' && i + 1 < s.length()) {
					i += 2;
					continue;
				}
				if (d == '{') {
					depth++;
				} else if (d == '}') {
					depth--;
					if (depth == 0) {
						return i + 1;
					}
				}
				i++;
			}
			return i;
		}
		if (c == '"') {
			int i = pos + 1;
			while (i < s.length()) {
				char d = s.charAt(i);
				if (d == '\\' && i + 1 < s.length()) {
					i += 2;
					continue;
				}
				if (d == '"') {
					return i + 1;
				}
				i++;
			}
			return i;
		}
		int i = pos;
		while (i < s.length()) {
			char d = s.charAt(i);
			if (d == ',' || d == '}' || d == ')' || Character.isWhitespace(d)) {
				break;
			}
			i++;
		}
		return i;
	}

	private static int skipWhitespace(String s, int pos) {
		while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
			pos++;
		}
		return pos;
	}

	private static int skipDelimiters(String s, int pos) {
		while (pos < s.length() && (s.charAt(pos) == ',' || Character.isWhitespace(s.charAt(pos)))) {
			pos++;
		}
		return pos;
	}
}
