package org.docear.metadata.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser (objects, arrays, strings incl. escapes, numbers,
 * booleans, null). Produces a plain Map&lt;String,Object&gt;/List&lt;Object&gt;
 * tree with String/Long/Double/Boolean/null leaves. Long is used for integer
 * literals, Double otherwise.
 *
 * It deliberately does NOT aim for full JSON specification coverage - it
 * exists so the Crossref REST responses can be parsed without adding a JSON
 * library to the project (constraint: do not upgrade dependencies). A parse
 * error throws IOException which callers handle like any other retrieval
 * failure.
 *
 * New code of the metadata provider extension (phase 1: Crossref). Java 1.6
 * syntax only.
 */
public final class JsonReader {

	private final String json;
	private int pos;

	private JsonReader(String json) {
		this.json = json;
		this.pos = 0;
	}

	public static Object parse(String json) throws IOException {
		if (json == null) {
			return null;
		}
		JsonReader reader = new JsonReader(json);
		reader.skipWhitespace();
		Object value = reader.parseValue();
		reader.skipWhitespace();
		if (reader.pos < reader.json.length()) {
			throw new IOException("unexpected trailing character at position " + reader.pos);
		}
		return value;
	}

	private Object parseValue() throws IOException {
		if (pos >= json.length()) {
			throw new IOException("unexpected end of JSON input");
		}
		char c = json.charAt(pos);
		switch (c) {
		case '{':
			return parseObject();
		case '[':
			return parseArray();
		case '"':
			return parseString();
		case 't':
			expect("true");
			return Boolean.TRUE;
		case 'f':
			expect("false");
			return Boolean.FALSE;
		case 'n':
			expect("null");
			return null;
		default:
			return parseNumber();
		}
	}

	private Map<String, Object> parseObject() throws IOException {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		pos++; // consume '{'
		skipWhitespace();
		if (peek() == '}') {
			pos++;
			return map;
		}
		while (true) {
			skipWhitespace();
			if (peek() != '"') {
				throw new IOException("expected object key at position " + pos);
			}
			String key = parseString();
			skipWhitespace();
			if (peek() != ':') {
				throw new IOException("expected ':' at position " + pos);
			}
			pos++;
			skipWhitespace();
			map.put(key, parseValue());
			skipWhitespace();
			char next = peek();
			if (next == ',') {
				pos++;
				continue;
			}
			if (next == '}') {
				pos++;
				return map;
			}
			throw new IOException("expected ',' or '}' at position " + pos);
		}
	}

	private List<Object> parseArray() throws IOException {
		List<Object> list = new ArrayList<Object>();
		pos++; // consume '['
		skipWhitespace();
		if (peek() == ']') {
			pos++;
			return list;
		}
		while (true) {
			skipWhitespace();
			list.add(parseValue());
			skipWhitespace();
			char next = peek();
			if (next == ',') {
				pos++;
				continue;
			}
			if (next == ']') {
				pos++;
				return list;
			}
			throw new IOException("expected ',' or ']' at position " + pos);
		}
	}

	private String parseString() throws IOException {
		pos++; // consume opening '"'
		StringBuffer sb = new StringBuffer();
		while (true) {
			if (pos >= json.length()) {
				throw new IOException("unterminated string");
			}
			char c = json.charAt(pos);
			if (c == '"') {
				pos++;
				return sb.toString();
			}
			if (c == '\\') {
				pos++;
				if (pos >= json.length()) {
					throw new IOException("unterminated escape sequence");
				}
				char esc = json.charAt(pos);
				switch (esc) {
				case '"':
					sb.append('"');
					break;
				case '\\':
					sb.append('\\');
					break;
				case '/':
					sb.append('/');
					break;
				case 'b':
					sb.append('\b');
					break;
				case 'f':
					sb.append('\f');
					break;
				case 'n':
					sb.append('\n');
					break;
				case 'r':
					sb.append('\r');
					break;
				case 't':
					sb.append('\t');
					break;
				case 'u':
					if (pos + 4 >= json.length()) {
						throw new IOException("unterminated unicode escape");
					}
					String hex = json.substring(pos + 1, pos + 5);
					try {
						sb.append((char) Integer.parseInt(hex, 16));
					} catch (NumberFormatException e) {
						throw new IOException("invalid unicode escape \\u" + hex);
					}
					pos += 4;
					break;
				default:
					throw new IOException("invalid escape character '\\" + esc + "' at position " + pos);
				}
				pos++;
				continue;
			}
			sb.append(c);
			pos++;
		}
	}

	private Object parseNumber() throws IOException {
		int start = pos;
		if (peek() == '-') {
			pos++;
		}
		boolean isDouble = false;
		while (pos < json.length()) {
			char c = json.charAt(pos);
			if (c >= '0' && c <= '9') {
				pos++;
			} else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
				isDouble = isDouble || c == '.' || c == 'e' || c == 'E';
				pos++;
			} else {
				break;
			}
		}
		if (pos == start) {
			throw new IOException("unexpected character '" + peek() + "' at position " + pos);
		}
		String token = json.substring(start, pos);
		try {
			if (isDouble) {
				return Double.valueOf(token);
			}
			return Long.valueOf(token);
		} catch (NumberFormatException e) {
			throw new IOException("invalid number literal '" + token + "'");
		}
	}

	private void expect(String literal) throws IOException {
		if (pos + literal.length() > json.length() || !json.startsWith(literal, pos)) {
			throw new IOException("expected '" + literal + "' at position " + pos);
		}
		pos += literal.length();
	}

	private char peek() throws IOException {
		if (pos >= json.length()) {
			throw new IOException("unexpected end of JSON input");
		}
		return json.charAt(pos);
	}

	private void skipWhitespace() {
		while (pos < json.length()) {
			char c = json.charAt(pos);
			if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
				pos++;
			} else {
				break;
			}
		}
	}

	// ------------------------------------------------------------------
	// typed accessors for navigating the parsed tree
	// ------------------------------------------------------------------

	@SuppressWarnings("unchecked")
	public static Map<String, Object> asMap(Object value) {
		if (value instanceof Map) {
			return (Map<String, Object>) value;
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static List<Object> asList(Object value) {
		if (value instanceof List) {
			return (List<Object>) value;
		}
		return null;
	}

	public static String asString(Object value) {
		if (value instanceof String) {
			return (String) value;
		}
		return null;
	}

	public static Integer asInteger(Object value) {
		if (value instanceof Long) {
			return Integer.valueOf(((Long) value).intValue());
		}
		if (value instanceof Double) {
			return Integer.valueOf(((Double) value).intValue());
		}
		return null;
	}
}
