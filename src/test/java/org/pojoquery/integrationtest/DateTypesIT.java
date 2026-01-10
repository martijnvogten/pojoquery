package org.pojoquery.integrationtest;

import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Integration tests for all supported date/time types.
 */
@ExtendWith(DbContextExtension.class)
public class DateTypesIT {

	@Table("event_with_dates")
	public static class EventWithDates {
		@Id
		Long id;
		
		String name;
		
		// TIMESTAMP types (represent absolute points in time)
		@FieldName("legacy_date")
		Date legacyDate;
		@FieldName("sql_timestamp")
		java.sql.Timestamp sqlTimestamp;
		Instant instant;
		
		// DATETIME type (local date-time without timezone)
		@FieldName("local_date_time")
		LocalDateTime localDateTime;
		
		// DATE type
		@FieldName("local_date")
		LocalDate localDate;
		@FieldName("sql_date")
		java.sql.Date sqlDate;
		
		// TIME type
		@FieldName("local_time")
		LocalTime localTime;
		@FieldName("sql_time")
		java.sql.Time sqlTime;
	}

	@Test
	public void testAllDateTypes_insertAndQuery() {
		DataSource db = initDatabase();
		
		// Create test data with all date/time types
		Date legacyDate = new Date();
		java.sql.Timestamp sqlTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
		Instant instant = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		LocalDateTime localDateTime = LocalDateTime.of(2025, 6, 15, 14, 30, 45);
		LocalDate localDate = LocalDate.of(2025, 6, 15);
		java.sql.Date sqlDate = java.sql.Date.valueOf(localDate);
		LocalTime localTime = LocalTime.of(14, 30, 45);
		java.sql.Time sqlTime = java.sql.Time.valueOf(localTime);

		DB.withConnection(db, (Connection c) -> {
			EventWithDates event = new EventWithDates();
			event.name = "Test Event";
			event.legacyDate = legacyDate;
			event.sqlTimestamp = sqlTimestamp;
			event.instant = instant;
			event.localDateTime = localDateTime;
			event.localDate = localDate;
			event.sqlDate = sqlDate;
			event.localTime = localTime;
			event.sqlTime = sqlTime;
			
			PojoQuery.insert(c, event);
			Assertions.assertNotNull(event.id);
			
			// Query back and verify
			EventWithDates loaded = PojoQuery.build(EventWithDates.class).findById(c, event.id);
			
			Assertions.assertNotNull(loaded);
			Assertions.assertEquals("Test Event", loaded.name);
			
			// Verify TIMESTAMP types (may lose some precision)
			Assertions.assertNotNull(loaded.legacyDate);
			Assertions.assertNotNull(loaded.sqlTimestamp);
			Assertions.assertEquals(instant, loaded.instant);
			
			// Verify DATETIME type
			Assertions.assertEquals(localDateTime, loaded.localDateTime);
			
			// Verify DATE types
			Assertions.assertEquals(localDate, loaded.localDate);
			Assertions.assertEquals(sqlDate.toString(), loaded.sqlDate.toString());
			
			// Verify TIME types
			Assertions.assertEquals(localTime, loaded.localTime);
			Assertions.assertEquals(sqlTime.toString(), loaded.sqlTime.toString());
		});
	}

	@Test
	public void testInstant_preservesPrecision() {
		DataSource db = initDatabase();
		
		// Test with a specific instant
		Instant testInstant = Instant.parse("2025-12-25T10:30:00Z");

		DB.withConnection(db, (Connection c) -> {
			EventWithDates event = new EventWithDates();
			event.name = "Christmas Event";
			event.instant = testInstant;
			
			PojoQuery.insert(c, event);
			
			EventWithDates loaded = PojoQuery.build(EventWithDates.class).findById(c, event.id);
			Assertions.assertEquals(testInstant, loaded.instant);
		});
	}

	@Test
	public void testLocalDateTime_preservesValue() {
		DataSource db = initDatabase();
		
		LocalDateTime testDateTime = LocalDateTime.of(2025, 12, 31, 23, 59, 59);

		DB.withConnection(db, (Connection c) -> {
			EventWithDates event = new EventWithDates();
			event.name = "New Year Event";
			event.localDateTime = testDateTime;
			
			PojoQuery.insert(c, event);
			
			EventWithDates loaded = PojoQuery.build(EventWithDates.class).findById(c, event.id);
			Assertions.assertEquals(testDateTime, loaded.localDateTime);
		});
	}

	@Test
	public void testLocalDate_preservesValue() {
		DataSource db = initDatabase();
		
		LocalDate testDate = LocalDate.of(2025, 7, 4);

		DB.withConnection(db, (Connection c) -> {
			EventWithDates event = new EventWithDates();
			event.name = "Independence Day";
			event.localDate = testDate;
			
			PojoQuery.insert(c, event);
			
			EventWithDates loaded = PojoQuery.build(EventWithDates.class).findById(c, event.id);
			Assertions.assertEquals(testDate, loaded.localDate);
		});
	}

	@Test
	public void testLocalTime_preservesValue() {
		DataSource db = initDatabase();
		
		LocalTime testTime = LocalTime.of(9, 15, 30);

		DB.withConnection(db, (Connection c) -> {
			EventWithDates event = new EventWithDates();
			event.name = "Morning Meeting";
			event.localTime = testTime;
			
			PojoQuery.insert(c, event);
			
			EventWithDates loaded = PojoQuery.build(EventWithDates.class).findById(c, event.id);
			Assertions.assertEquals(testTime, loaded.localTime);
		});
	}

	@Test
	public void testNullDateValues() {
		DataSource db = initDatabase();

		DB.withConnection(db, (Connection c) -> {
			EventWithDates event = new EventWithDates();
			event.name = "Event with nulls";
			// All date fields are null
			
			PojoQuery.insert(c, event);
			
			EventWithDates loaded = PojoQuery.build(EventWithDates.class).findById(c, event.id);
			
			Assertions.assertEquals("Event with nulls", loaded.name);
			Assertions.assertNull(loaded.legacyDate);
			Assertions.assertNull(loaded.sqlTimestamp);
			Assertions.assertNull(loaded.instant);
			Assertions.assertNull(loaded.localDateTime);
			Assertions.assertNull(loaded.localDate);
			Assertions.assertNull(loaded.sqlDate);
			Assertions.assertNull(loaded.localTime);
			Assertions.assertNull(loaded.sqlTime);
		});
	}

	@Test
	public void testQueryByDateField() {
		DataSource db = initDatabase();
		
		LocalDate targetDate = LocalDate.of(2025, 8, 15);

		DB.withConnection(db, (Connection c) -> {
			// Insert multiple events
			for (int i = 1; i <= 5; i++) {
				EventWithDates event = new EventWithDates();
				event.name = "Event " + i;
				event.localDate = LocalDate.of(2025, 8, 10 + i);
				PojoQuery.insert(c, event);
			}
			
			// Query by specific date
			List<EventWithDates> results = PojoQuery.build(EventWithDates.class)
				.addWhere("{event_with_dates}.local_date = ?", targetDate)
				.execute(c);
			
			Assertions.assertEquals(1, results.size());
			Assertions.assertEquals("Event 5", results.get(0).name);
			Assertions.assertEquals(targetDate, results.get(0).localDate);
		});
	}

	@Test
	public void testQueryByInstant() {
		DataSource db = initDatabase();
		
		Instant targetInstant = Instant.parse("2025-09-01T12:00:00Z");

		DB.withConnection(db, (Connection c) -> {
			EventWithDates event1 = new EventWithDates();
			event1.name = "Before";
			event1.instant = Instant.parse("2025-09-01T11:00:00Z");
			PojoQuery.insert(c, event1);
			
			EventWithDates event2 = new EventWithDates();
			event2.name = "Target";
			event2.instant = targetInstant;
			PojoQuery.insert(c, event2);
			
			EventWithDates event3 = new EventWithDates();
			event3.name = "After";
			event3.instant = Instant.parse("2025-09-01T13:00:00Z");
			PojoQuery.insert(c, event3);
			
			// Query by exact instant
			List<EventWithDates> results = PojoQuery.build(EventWithDates.class)
				.addWhere("{event_with_dates}.instant = ?", targetInstant)
				.execute(c);
			
			Assertions.assertEquals(1, results.size());
			Assertions.assertEquals("Target", results.get(0).name);
		});
	}

	private static DataSource initDatabase() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, EventWithDates.class);
		return db;
	}
}
