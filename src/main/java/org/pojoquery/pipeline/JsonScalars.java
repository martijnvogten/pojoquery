package org.pojoquery.pipeline;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Set;

import org.pojoquery.internal.MappingException;

/**
 * Converts the text of a positional JSON document back into the values an
 * entity field expects.
 *
 * <p>Values travel as text (see {@code DbContext.castToStringExpression}), so
 * nothing is rounded or reformatted in transit and there is a single conversion
 * path per target type.</p>
 *
 * <p>Conversion targets the <strong>field's own type</strong>. Aiming at the
 * JDBC shape instead is not well defined: which value a driver yields depends on
 * the driver and on the column type - {@code BIGINT} versus {@code INT} gives
 * {@code Long} versus {@code Integer}, Connector/J answers {@code DATETIME} with
 * a {@code LocalDateTime} where other drivers give a {@code Timestamp} - and a
 * query need not read a plain column at all. Text is unambiguous, so this class
 * converts it once, deterministically; the field's
 * {@link DefaultValueMappers value mapper} then passes the value through, as its
 * {@code instanceof} guards are written to do.</p>
 *
 * <p>The one genuine ambiguity is a zone-less timestamp read into an
 * {@link Instant}: it is resolved in the JVM's default zone, the same policy
 * {@link DefaultValueMappers} applies on the joined path.</p>
 *
 * <p>Dialects render dates and times slightly differently - a single-digit hour
 * on HSQLDB, a {@code T} separator or a zone offset elsewhere - so the temporal
 * parsers accept the variants rather than assuming one format.</p>
 */
public final class JsonScalars {

	private static final Set<String> TRUE_VALUES = Set.of("true", "t", "1", "y", "yes", "on");

	private JsonScalars() {
	}

	/**
	 * Converts one slot value.
	 *
	 * @param text     the value as it appeared in the document, or null
	 * @param javaType the type the entity field holds, or null if unknown
	 * @return the converted value, or null
	 */
	public static Object decode(String text, Class<?> javaType) {
		if (text == null) {
			return null;
		}
		if (javaType == null || javaType == String.class || javaType.isEnum()) {
			// Enums are mapped from their name by the field's value mapper.
			return text;
		}
		try {
			return convert(text, javaType);
		} catch (DateTimeParseException | IllegalArgumentException e) {
			throw new MappingException(
					"Cannot read '" + text + "' as " + javaType.getName() + " from a JSON document", e);
		}
	}

	private static Object convert(String text, Class<?> javaType) {
		if (javaType == Long.class || javaType == Long.TYPE) {
			return Long.valueOf(text.trim());
		}
		if (javaType == Integer.class || javaType == Integer.TYPE) {
			return Integer.valueOf(text.trim());
		}
		if (javaType == Short.class || javaType == Short.TYPE) {
			return Short.valueOf(text.trim());
		}
		if (javaType == Byte.class || javaType == Byte.TYPE) {
			return Byte.valueOf(text.trim());
		}
		if (javaType == Double.class || javaType == Double.TYPE) {
			return Double.valueOf(text.trim());
		}
		if (javaType == Float.class || javaType == Float.TYPE) {
			return Float.valueOf(text.trim());
		}
		if (javaType == BigDecimal.class) {
			return new BigDecimal(text.trim());
		}
		if (javaType == BigInteger.class) {
			return new BigInteger(text.trim());
		}
		if (javaType == Boolean.class || javaType == Boolean.TYPE) {
			return TRUE_VALUES.contains(text.trim().toLowerCase());
		}
		if (javaType == Character.class || javaType == Character.TYPE) {
			if (text.length() != 1) {
				throw new IllegalArgumentException("expected a single character");
			}
			return text.charAt(0);
		}
		if (javaType == LocalDate.class) {
			return parseLocalDate(text);
		}
		if (javaType == LocalDateTime.class) {
			return parseLocalDateTime(text);
		}
		if (javaType == LocalTime.class) {
			return parseLocalTime(text);
		}
		if (javaType == Instant.class) {
			// A zoned rendering (PostgreSQL timestamptz) names the instant outright;
			// a zone-less one is resolved in the JVM zone, as the mapper does.
			int zone = zoneIndex(text.trim());
			return zone > 0
					? parseOffsetInstant(text.trim(), zone)
					: parseLocalDateTime(text).atZone(ZoneId.systemDefault()).toInstant();
		}
		if (javaType == java.sql.Date.class) {
			return java.sql.Date.valueOf(parseLocalDate(text));
		}
		if (javaType == java.sql.Time.class) {
			return java.sql.Time.valueOf(parseLocalTime(text));
		}
		if (javaType == java.sql.Timestamp.class || javaType == Date.class) {
			return java.sql.Timestamp.valueOf(parseLocalDateTime(text));
		}
		throw new MappingException("Positional JSON documents cannot carry " + javaType.getName() + " values yet");
	}

	private static LocalDate parseLocalDate(String text) {
		return LocalDate.parse(normalizeDatePart(text.trim()));
	}

	private static LocalDateTime parseLocalDateTime(String text) {
		String value = text.trim();
		int separator = separatorIndex(value);
		if (separator < 0) {
			return parseLocalDate(value).atStartOfDay();
		}
		return LocalDateTime.of(parseLocalDate(value.substring(0, separator)),
				parseLocalTime(value.substring(separator + 1)));
	}

	private static LocalTime parseLocalTime(String text) {
		String value = stripZone(text.trim());
		String[] parts = value.split(":");
		if (parts.length < 2) {
			throw new IllegalArgumentException("expected a time of day");
		}
		int hour = Integer.parseInt(parts[0]);
		int minute = Integer.parseInt(parts[1]);
		if (parts.length == 2) {
			return LocalTime.of(hour, minute);
		}
		String[] seconds = parts[2].split("\\.");
		int second = Integer.parseInt(seconds[0]);
		int nanos = seconds.length > 1 ? fractionToNanos(seconds[1]) : 0;
		return LocalTime.of(hour, minute, second, nanos);
	}

	private static Instant parseOffsetInstant(String value, int zone) {
		int separator = separatorIndex(value);
		return OffsetDateTime.parse(value.substring(0, separator) + "T"
				+ stripZone(value.substring(separator + 1)) + normalizeOffset(value.substring(zone))).toInstant();
	}

	/** Pads a single-digit day or month, as HSQLDB may render them. */
	private static String normalizeDatePart(String date) {
		String[] parts = date.split("-");
		if (parts.length != 3) {
			throw new IllegalArgumentException("expected a date");
		}
		return String.format("%04d-%02d-%02d", Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
				Integer.parseInt(parts[2]));
	}

	private static int separatorIndex(String value) {
		int t = value.indexOf('T');
		return t >= 0 ? t : value.indexOf(' ');
	}

	private static int zoneIndex(String value) {
		int time = separatorIndex(value);
		if (time < 0) {
			return -1;
		}
		for (int i = time + 1; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '+' || c == '-' || c == 'Z') {
				return i;
			}
		}
		return -1;
	}

	private static String stripZone(String time) {
		int zone = zoneIndex(" " + time) - 1;
		return zone > 0 ? time.substring(0, zone) : time;
	}

	/** Expands {@code +02} to {@code +02:00}; leaves {@code Z} and full offsets alone. */
	private static String normalizeOffset(String offset) {
		if ("Z".equals(offset) || offset.length() >= 6) {
			return offset;
		}
		return offset.length() == 3 ? offset + ":00" : offset;
	}

	private static int fractionToNanos(String fraction) {
		String digits = (fraction + "000000000").substring(0, 9);
		return Integer.parseInt(digits);
	}
}
