package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pojoquery.TestUtils.norm;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Table;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.PojoMetadata;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;

public class TestInheritance {

	@BeforeEach
	public void setup() {
		DbContext.setDefault(DbContext.forDialect(Dialect.MYSQL));
	}
	
	static class Entity {
		@Id
		Long id;
	}
	
	@Table("room")
	@SubClasses({BedRoom.class, Kitchen.class})
	static class Room extends Entity {
		Double area;
	}
	
	@Table("bedroom")
	static class BedRoom extends Room {
		Integer numberOfBeds;
	}
	
	static class LuxuryBedRoom extends BedRoom {
		Double tvScreenSize;
	}
	
	@Table("kitchen")
	static class Kitchen extends Room {
		Boolean hasDishWasher;
	}
	
	@Table("apartment")
	static class Apartment {
		@Id
		Long id;
		Room[] rooms;
	}
	
	@Table("apartment")
	static class ApartmentWithSpecificProperties {
		@Id
		Long id;
		BedRoom[] bedrooms; // Implies an apartment_id in 
	}
	
	@Test
	public void testBuildTableHierarchy() throws Exception {
		// When querying a superclasses, we want a list of all tables
		// and fields per table
		List<TableMapping> mapping = PojoMetadata.determineTableMapping(new ReflectionTypeModel(Room.class));
		Assertions.assertEquals(1, mapping.size());
		Assertions.assertEquals(List.of("id", "area"), fieldNames(mapping.get(0)));
		
		List<TableMapping> bedroom = PojoMetadata.determineTableMapping(new ReflectionTypeModel(BedRoom.class));
		Assertions.assertEquals(2, bedroom.size());
		Assertions.assertEquals(List.of("id", "area"), fieldNames(bedroom.get(0)));
		Assertions.assertEquals(List.of("numberOfBeds"), fieldNames(bedroom.get(1)));
		
		List<TableMapping> luxury = PojoMetadata.determineTableMapping(new ReflectionTypeModel(LuxuryBedRoom.class));
		Assertions.assertEquals(2, luxury.size());
		Assertions.assertEquals(List.of("numberOfBeds", "tvScreenSize"), fieldNames(luxury.get(1)));
	}
	
	@Test
	public void testSubClasses() {
		PojoQuery<Room> b = PojoQuery.build(Room.class);
		String sql = b.toSql();
		
		assertEquals(
				norm("""
					SELECT
					 `room`.`id` AS `room.id`,
					 `room`.`area` AS `room.area`,
					 `room.bedroom`.`id` AS `room.bedroom.id`,
					 `room.bedroom`.`numberOfBeds` AS `room.bedroom.numberOfBeds`,
					 `room.kitchen`.`id` AS `room.kitchen.id`,
					 `room.kitchen`.`hasDishWasher` AS `room.kitchen.hasDishWasher`
					FROM `room` AS `room`
					 LEFT JOIN `bedroom` AS `room.bedroom` ON `room`.`id` = `room.bedroom`.`id`
					 LEFT JOIN `kitchen` AS `room.kitchen` ON `room`.`id` = `room.kitchen`.`id`
					"""),
				norm(sql));
		
		List<Map<String, Object>> result = TestUtils.resultSet(new String[] {
					"room.id", "room.area", "room.bedroom.id", "room.bedroom.numberOfBeds", "room.kitchen.id", "room.kitchen.hasDishWasher" }, 
				     1L,        100.0,       1L,           1,                      null,         null,
				     2L,        40.0,        null,         null,                   2L,           true);
		
		List<Room> room = b.processRows(result);
		assertTrue(room.get(0) instanceof BedRoom);
		assertEquals((Object)1, ((BedRoom)room.get(0)).numberOfBeds);
		assertEquals(Boolean.TRUE, ((Kitchen)room.get(1)).hasDishWasher);
		assertEquals((Object)2L, room.get(1).id);
	}
	
	@Test
	public void testSuperclasses() {
		PojoQuery<BedRoom> n = PojoQuery.build(BedRoom.class);
		String sql = n.toStatement().getSql();
		System.out.println(sql);
		assertEquals(
				norm("""
					SELECT
					`bedroom`.`numberOfBeds` AS `bedroom.numberOfBeds`,
					`bedroom.room`.`id` AS `bedroom.room.id`,
					`bedroom.room`.`area` AS `bedroom.room.area`
					FROM `bedroom` AS `bedroom`
					LEFT JOIN `room` AS `bedroom.room` ON `bedroom`.`id` = `bedroom.room`.`id`
					"""),
				norm(sql));
		
		List<Map<String, Object>> result = TestUtils.resultSet(new String[] {
				"bedroom.room.id", "bedroom.room.area", "bedroom.numberOfBeds" }, 
			     1L,           100.0,          1);
		

		List<BedRoom> list = PojoQuery.build(BedRoom.class).processRows(result);
		Assertions.assertEquals(1, list.size());
		BedRoom bedroom = list.get(0);
		Assertions.assertTrue(bedroom instanceof BedRoom);
		Assertions.assertEquals(100.0F, bedroom.area, 0.1F);
		Assertions.assertEquals((Integer)1, bedroom.numberOfBeds);
	}
	
	@Test
	public void testSuperClassOfLinked() {
		String sql = PojoQuery.build(ApartmentWithSpecificProperties.class).toStatement().getSql();
		System.out.println(sql);
		assertEquals(
			norm("""
				SELECT
				 `apartment`.`id` AS `apartment.id`,
				 `bedrooms`.`numberOfBeds` AS `bedrooms.numberOfBeds`,
				 `bedrooms.room`.`id` AS `bedrooms.room.id`,
				 `bedrooms.room`.`area` AS `bedrooms.room.area`
				FROM `apartment` AS `apartment`
				 LEFT JOIN `bedroom` AS `bedrooms` ON `bedrooms`.`apartment_id` = `apartment`.`id`
 				 LEFT JOIN `room` AS `bedrooms.room` ON `bedrooms`.`id` = `bedrooms.room`.`id`
				"""), 
			norm(sql));
	}
	
	@Test
	public void testDeeper() {
		String sql = PojoQuery.build(Apartment.class).toStatement().getSql();
		assertEquals(
				norm("""
					SELECT
					`apartment`.`id` AS `apartment.id`,
					`rooms`.`id` AS `rooms.id`,
					`rooms`.`area` AS `rooms.area`,
					`rooms.bedroom`.`id` AS `rooms.bedroom.id`,
					`rooms.bedroom`.`numberOfBeds` AS `rooms.bedroom.numberOfBeds`,
					`rooms.kitchen`.`id` AS `rooms.kitchen.id`,
					`rooms.kitchen`.`hasDishWasher` AS `rooms.kitchen.hasDishWasher`
					FROM `apartment` AS `apartment`
					LEFT JOIN `room` AS `rooms` ON `rooms`.`apartment_id` = `apartment`.`id`
					LEFT JOIN `bedroom` AS `rooms.bedroom` ON `rooms`.`id` = `rooms.bedroom`.`id`
					LEFT JOIN `kitchen` AS `rooms.kitchen` ON `rooms`.`id` = `rooms.kitchen`.`id`
					"""),
				norm(sql));
		
		List<Map<String, Object>> result = TestUtils.resultSet(new String[] {
				"apartment.id", "rooms.id", "rooms.area", "rooms.bedroom.id", "rooms.bedroom.numberOfBeds", "rooms.kitchen.id", "rooms.kitchen.hasDishwasher" }, 
			     1L,             1L,         100.0,        1L,                 2,                            null,               null);
		
		List<Apartment> list = PojoQuery.build(Apartment.class).processRows(result);
		Assertions.assertTrue(list.get(0).rooms[0] instanceof BedRoom);
	}
	
	private List<String> fieldNames(TableMapping mapping) {
		return mapping.getFields().stream().map(FieldModel::getName).toList();
	}
	
}
