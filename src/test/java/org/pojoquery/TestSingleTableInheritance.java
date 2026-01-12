package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pojoquery.TestUtils.norm;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.DiscriminatorColumn;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Table;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Tests for single table inheritance support using @DiscriminatorColumn.
 */
public class TestSingleTableInheritance {

	@BeforeEach
	public void setup() {
		DbContext.setDefault(DbContext.forDialect(Dialect.MYSQL));
	}
	static class Entity {
		@Id
		Long id;
	}

	@Table("room")
	@DiscriminatorColumn  // Uses default column name "dtype"
	@SubClasses({STIBedRoom.class, STIKitchen.class})
	static class STIRoom extends Entity {
		Double area;
	}

	@Table("room")
	static class STIBedRoom extends STIRoom {
		Integer numberOfBeds;
	}

	@Table("room")
	static class STIKitchen extends STIRoom {
		Boolean hasDishWasher;
	}

	@Table("room")
	@DiscriminatorColumn(name = "room_type")  // Custom column name
	@SubClasses({CustomDiscBedRoom.class})
	static class CustomDiscRoom extends Entity {
		Double area;
	}

	@Table("room")
	static class CustomDiscBedRoom extends CustomDiscRoom {
		Integer numberOfBeds;
	}

	@Test
	public void testSingleTableInheritanceQuery() {
		PojoQuery<STIRoom> b = PojoQuery.build(STIRoom.class);
		String sql = b.toSql();

		// Should generate a single table query with discriminator column and all subclass fields
		// No JOINs should be present
		assertEquals(
				norm("""
					SELECT
					 `room`.`id` AS `room.id`,
					 `room`.`area` AS `room.area`,
					 `room`.`dtype` AS `room.dtype`,
					 `room`.`numberOfBeds` AS `room.numberOfBeds`,
					 `room`.`hasDishWasher` AS `room.hasDishWasher`
					FROM `room` AS `room`
					"""),
				norm(sql));
	}

	@Test
	public void testCustomDiscriminatorColumnName() {
		PojoQuery<CustomDiscRoom> b = PojoQuery.build(CustomDiscRoom.class);
		String sql = b.toSql();

		// Should use custom discriminator column name
		assertEquals(
				norm("""
					SELECT
					 `room`.`id` AS `room.id`,
					 `room`.`area` AS `room.area`,
					 `room`.`room_type` AS `room.room_type`,
					 `room`.`numberOfBeds` AS `room.numberOfBeds`
					FROM `room` AS `room`
					"""),
				norm(sql));
	}

	@Test
	public void testProcessRowsWithDiscriminator() {
		PojoQuery<STIRoom> b = PojoQuery.build(STIRoom.class);

		// Simulate result set with discriminator values
		List<Map<String, Object>> result = TestUtils.resultSet(new String[] {
				"room.id", "room.area", "room.dtype", "room.numberOfBeds", "room.hasDishWasher" },
				1L, 100.0, "STIBedRoom", 2, null,
				2L, 40.0, "STIKitchen", null, true,
				3L, 50.0, "STIRoom", null, null);

		List<STIRoom> rooms = b.processRows(result);

		assertEquals(3, rooms.size());

		// First room should be a BedRoom
		assertTrue(rooms.get(0) instanceof STIBedRoom);
		assertEquals((Object)2, ((STIBedRoom)rooms.get(0)).numberOfBeds);
		assertEquals(100.0, rooms.get(0).area);

		// Second room should be a Kitchen
		assertTrue(rooms.get(1) instanceof STIKitchen);
		assertEquals(Boolean.TRUE, ((STIKitchen)rooms.get(1)).hasDishWasher);

		// Third room should be base STIRoom (not a subclass)
		assertEquals(STIRoom.class, rooms.get(2).getClass());
	}

	@Test
	public void testSchemaGeneratorSingleTableInheritance() {
		List<String> statements = SchemaGenerator.generateCreateTableStatements(STIRoom.class);

		// Should generate only ONE table with discriminator column and all fields
		assertEquals(1, statements.size());

		String createTable = statements.get(0);
		System.out.println("STI Schema:\n" + createTable);

		// Should contain discriminator column
		assertTrue(createTable.contains("`dtype`"));

		// Should contain all fields from hierarchy
		assertTrue(createTable.contains("`id`"));
		assertTrue(createTable.contains("`area`"));
		assertTrue(createTable.contains("`numberOfBeds`"));
		assertTrue(createTable.contains("`hasDishWasher`"));

		// Should NOT contain separate bedroom or kitchen tables
		// (only one statement was generated)
	}

	@Test
	public void testSchemaGeneratorCustomDiscriminator() {
		List<String> statements = SchemaGenerator.generateCreateTableStatements(CustomDiscRoom.class);

		assertEquals(1, statements.size());

		String createTable = statements.get(0);
		System.out.println("Custom Discriminator Schema:\n" + createTable);

		// Should use custom discriminator column name
		assertTrue(createTable.contains("`room_type`"));
	}
}
