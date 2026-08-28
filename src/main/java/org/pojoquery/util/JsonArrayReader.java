package org.pojoquery.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the positional JSON documents PojoQuery asks the database to build.
 *
 * <p>Those documents contain only arrays and scalars - never objects - so this
 * is deliberately not a general JSON parser: it exists so hydration needs no
 * JSON library on the classpath. Values come back as {@link String} (for
 * strings, numbers, {@code true} and {@code false} alike, since the reader
 * leaves interpretation to the caller, which knows the target type),
 * {@code null}, or {@link List} of the same.</p>
 *
 * <p>Numbers and booleans are returned as their source text rather than parsed,
 * which keeps one conversion path for every value and cannot round a decimal on
 * the way through.</p>
 *
 * <p>The reader is fed documents the database built, but their string content is
 * whatever was stored, so it must not be possible for a value to alter the
 * structure. Escapes are consumed symmetrically to the way the database writes
 * them, which is what keeps a value containing {@code "], [} inside its slot.
 * Nesting depth is capped so a malformed or hostile document fails with an
 * exception rather than exhausting the stack.</p>
 */
public final class JsonArrayReader {

	/**
	 * Maximum array nesting. Documents nest one level per collection in the query
	 * tree, so a real document is shallow; the cap turns a hostile or corrupt one
	 * into an exception instead of a {@link StackOverflowError}.
	 */
	private static final int MAX_DEPTH = 256;

	private final String json;
	private int position;
	private int depth;

	private JsonArrayReader(String json) {
		this.json = json;
	}

	/**
	 * Parses one document.
	 *
	 * @param json the document text
	 * @return a {@link List} for an array, a {@link String} for a scalar, or null
	 * @throws IllegalArgumentException if the text is not the expected JSON
	 */
	public static Object read(String json) {
		if (json == null) {
			return null;
		}
		JsonArrayReader reader = new JsonArrayReader(json);
		Object value = reader.readValue();
		reader.skipWhitespace();
		if (reader.position < json.length()) {
			throw reader.error("trailing content");
		}
		return value;
	}

	private Object readValue() {
		skipWhitespace();
		if (position >= json.length()) {
			throw error("unexpected end of document");
		}
		char c = json.charAt(position);
		return switch (c) {
			case '[' -> readArray();
			case '"' -> readString();
			case '{' -> throw error("unexpected JSON object; positional documents contain only arrays");
			default -> readLiteral();
		};
	}

	private List<Object> readArray() {
		if (++depth > MAX_DEPTH) {
			throw error("nesting deeper than " + MAX_DEPTH + " levels");
		}
		position++; // '['
		List<Object> values = new ArrayList<>();
		skipWhitespace();
		if (position < json.length() && json.charAt(position) == ']') {
			position++;
			depth--;
			return values;
		}
		while (true) {
			values.add(readValue());
			skipWhitespace();
			if (position >= json.length()) {
				throw error("unterminated array");
			}
			char c = json.charAt(position++);
			if (c == ']') {
				depth--;
				return values;
			}
			if (c != ',') {
				throw error("expected ',' or ']'");
			}
		}
	}

	private String readString() {
		position++; // opening quote
		StringBuilder value = new StringBuilder();
		while (position < json.length()) {
			char c = json.charAt(position++);
			if (c == '"') {
				return value.toString();
			}
			if (c != '\\') {
				value.append(c);
				continue;
			}
			if (position >= json.length()) {
				throw error("unterminated escape");
			}
			char escaped = json.charAt(position++);
			switch (escaped) {
				case '"', '\\', '/' -> value.append(escaped);
				case 'b' -> value.append('\b');
				case 'f' -> value.append('\f');
				case 'n' -> value.append('\n');
				case 'r' -> value.append('\r');
				case 't' -> value.append('\t');
				case 'u' -> {
					if (position + 4 > json.length()) {
						throw error("truncated unicode escape");
					}
					value.append(readCodeUnit(json.substring(position, position + 4)));
					position += 4;
				}
				default -> throw error("unknown escape '\\" + escaped + "'");
			}
		}
		throw error("unterminated string");
	}

	/** Four hex digits exactly - not what {@code Integer.parseInt} alone accepts. */
	private char readCodeUnit(String hex) {
		int codeUnit = 0;
		for (int i = 0; i < 4; i++) {
			int digit = Character.digit(hex.charAt(i), 16);
			if (digit < 0) {
				throw error("invalid unicode escape '\\u" + hex + "'");
			}
			codeUnit = codeUnit * 16 + digit;
		}
		return (char) codeUnit;
	}

	/** A number, {@code true}, {@code false} or {@code null}. */
	private Object readLiteral() {
		int start = position;
		while (position < json.length() && ",]} \t\r\n".indexOf(json.charAt(position)) < 0) {
			position++;
		}
		String token = json.substring(start, position);
		if (token.isEmpty()) {
			throw error("expected a value");
		}
		return "null".equals(token) ? null : token;
	}

	private void skipWhitespace() {
		while (position < json.length() && Character.isWhitespace(json.charAt(position))) {
			position++;
		}
	}

	private IllegalArgumentException error(String message) {
		return new IllegalArgumentException("Malformed JSON document at offset " + position + ": " + message);
	}
}
