package org.pojoquery.multiset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.pojoquery.internal.MappingException;
import org.pojoquery.pipeline.JsonScalars;

/**
 * Values travel through a JSON document as text; these are the renderings the
 * three dialects actually produce for a string cast.
 *
 * <p>Decoding targets the field's own type: the JDBC shape is not a well-defined
 * target, since it varies with the driver and the column type. The field's value
 * mapper then passes the value through.</p>
 */
public class JsonScalarsTest {

	@Test
	public void decodesNumbersWithoutLosingPrecision() {
		assertEquals(new BigDecimal("12.3456"), JsonScalars.decode("12.3456", BigDecimal.class));
		assertEquals(new BigDecimal("123456789012345678901234567890.5"),
				JsonScalars.decode("123456789012345678901234567890.5", BigDecimal.class));
		assertEquals(9007199254740993L, JsonScalars.decode("9007199254740993", Long.class));   // beyond 2^53
		assertEquals(42, JsonScalars.decode("42", Integer.class));
		assertEquals(42, JsonScalars.decode("42", Integer.TYPE));
	}

	@Test
	public void decodesBooleansFromEveryDialectRendering() {
		assertEquals(true, JsonScalars.decode("TRUE", Boolean.class));   // HSQLDB
		assertEquals(true, JsonScalars.decode("true", Boolean.class));   // PostgreSQL
		assertEquals(true, JsonScalars.decode("1", Boolean.class));      // MySQL
		assertEquals(false, JsonScalars.decode("FALSE", Boolean.class));
		assertEquals(false, JsonScalars.decode("0", Boolean.class));
	}

	@Test
	public void decodesTemporalsFromEveryDialectRendering() {
		assertEquals(LocalDate.of(2024, 1, 2), JsonScalars.decode("2024-01-02", LocalDate.class));
		assertEquals(LocalDateTime.of(2024, 1, 2, 3, 4, 5),
				JsonScalars.decode("2024-01-02 03:04:05", LocalDateTime.class));
		assertEquals(LocalDateTime.of(2024, 1, 2, 3, 4, 5, 123456000),
				JsonScalars.decode("2024-01-02 03:04:05.123456", LocalDateTime.class));
		assertEquals(LocalDateTime.of(2024, 1, 2, 3, 4, 5),
				JsonScalars.decode("2024-01-02T03:04:05", LocalDateTime.class));
		// HSQLDB renders a single-digit hour
		assertEquals(LocalTime.of(3, 4, 5), JsonScalars.decode("3:04:05", LocalTime.class));
		assertEquals(LocalTime.of(3, 4, 5), JsonScalars.decode("03:04:05", LocalTime.class));
		// java.sql field types keep their own shapes
		assertEquals(Timestamp.valueOf(LocalDateTime.of(2024, 1, 2, 3, 4, 5)),
				JsonScalars.decode("2024-01-02 03:04:05", Timestamp.class));
		assertEquals(Date.valueOf(LocalDate.of(2024, 1, 2)), JsonScalars.decode("2024-01-02", Date.class));
		assertEquals(Time.valueOf(LocalTime.of(3, 4, 5)), JsonScalars.decode("03:04:05", Time.class));
	}

	/**
	 * A zone-less rendering is resolved in the JVM's default zone - the policy
	 * {@code DefaultValueMappers} applies on the joined path - while a rendering
	 * that names an offset (PostgreSQL timestamptz) fixes the instant outright.
	 */
	@Test
	public void decodesInstantsWithTheMappersZonePolicy() {
		assertEquals(LocalDateTime.of(2024, 1, 2, 3, 4, 5).atZone(ZoneId.systemDefault()).toInstant(),
				JsonScalars.decode("2024-01-02 03:04:05", Instant.class));
		assertEquals(Instant.parse("2024-01-02T01:04:05Z"),
				JsonScalars.decode("2024-01-02 03:04:05+02", Instant.class));
	}

	@Test
	public void passesTextAndEnumNamesThrough() {
		assertEquals("Dune", JsonScalars.decode("Dune", String.class));
		assertEquals("A", JsonScalars.decode("A", Grade.class));   // the field's mapper makes the enum
		assertEquals('x', JsonScalars.decode("x", Character.class));
		assertNull(JsonScalars.decode(null, Long.class));
	}

	/** An unknown field type fails loudly rather than guessing a conversion. */
	@Test
	public void rejectsUnknownFieldTypes() {
		MappingException e = assertThrows(MappingException.class,
				() -> JsonScalars.decode("2f9a", java.util.UUID.class));
		assertEquals(true, e.getMessage().contains("java.util.UUID"));
	}

	@Test
	public void reportsUnreadableValuesWithContext() {
		MappingException e = assertThrows(MappingException.class, () -> JsonScalars.decode("not-a-number", Long.class));
		assertEquals(true, e.getMessage().contains("not-a-number"));
		assertEquals(true, e.getMessage().contains("java.lang.Long"));
	}

	enum Grade {
		A, B
	}
}
