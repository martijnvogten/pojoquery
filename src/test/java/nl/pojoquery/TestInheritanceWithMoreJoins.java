package nl.pojoquery;

import static nl.pojoquery.TestUtils.norm;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.JoinCondition;
import nl.pojoquery.annotations.SubClasses;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.pipeline.QueryBuilder;

public class TestInheritanceWithMoreJoins {
	
	static class Entity {
		@Id
		Long id;
	}
	
	@Table("house")
	static class House extends Entity{
		String address;
	}
	
	@Table("room")
	@SubClasses({BedRoom.class, Kitchen.class})
	static class Room extends Entity {
		Double area;
		House house;
		
		@JoinCondition("{this}.id = {windows}.room_id")
		Window[] windows;
	}
	
	@Table("window")
	static class Window extends Entity {
		Double width;
	}
	
	@Table("bed")
	static class Bed extends Entity {
		Double width;
	}
	
	@Table("bedroom")
	static class BedRoom extends Room {
		Bed[] beds;
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
	
	@Test
	public void testDeeper() {
		QueryBuilder<BedRoom> qb = QueryBuilder.from(BedRoom.class);
		String sql = qb.toStatement().getSql();
		assertEquals(
				norm("""
					SELECT
					 `beds`.id AS `beds.id`,
					 `beds`.width AS `beds.width`,
					 `room`.id AS `bedroom.id`,
					 `room`.area AS `bedroom.area`,
					 `house`.id AS `house.id`,
					 `house`.address AS `house.address`,
					 `windows`.id AS `windows.id`,
					 `windows`.width AS `windows.width`
					FROM `bedroom` AS `bedroom`
					 INNER JOIN `room` AS `room` ON `room`.id = `bedroom`.id
					 LEFT JOIN `bed` AS `beds` ON `bedroom`.id = `beds`.bedroom_id
					 LEFT JOIN `house` AS `house` ON `room`.house_id = `house`.id
					 LEFT JOIN `window` AS `windows` ON `room`.id = `windows`.room_id
					"""),
				norm(sql));
	}
	
	@Test
	public void testApartment() {
		QueryBuilder<Apartment> qb = QueryBuilder.from(Apartment.class);
		String sql = qb.toStatement().getSql();
		assertEquals(
				norm("""
					SELECT
					 `apartment`.id AS `apartment.id`,
					 `rooms`.id AS `rooms.id`,
					 `rooms`.area AS `rooms.area`,
					 `rooms.house`.id AS `rooms.house.id`,
					 `rooms.house`.address AS `rooms.house.address`,
					 `rooms.windows`.id AS `rooms.windows.id`,
					 `rooms.windows`.width AS `rooms.windows.width`,
					 `rooms.bedroom`.id AS `rooms.bedroom.id`,
					 `rooms.bedroom.beds`.id AS `rooms.bedroom.beds.id`,
					 `rooms.bedroom.beds`.width AS `rooms.bedroom.beds.width`,
					 `rooms.kitchen`.id AS `rooms.kitchen.id`,
					 `rooms.kitchen`.hasDishWasher AS `rooms.kitchen.hasDishWasher`
					FROM `apartment` AS `apartment`
					 LEFT JOIN `room` AS `rooms` ON `apartment`.id = `rooms`.apartment_id
					 LEFT JOIN `house` AS `rooms.house` ON `rooms`.house_id = `rooms.house`.id
					 LEFT JOIN `window` AS `rooms.windows` ON `rooms`.id = `rooms.windows`.room_id
					 LEFT JOIN `bedroom` AS `rooms.bedroom` ON `rooms.bedroom`.id = `rooms`.id
					 LEFT JOIN `bed` AS `rooms.bedroom.beds` ON `rooms.bedroom`.id = `rooms.bedroom.beds`.bedroom_id
					 LEFT JOIN `kitchen` AS `rooms.kitchen` ON `rooms.kitchen`.id = `rooms`.id
					"""),
				norm(sql));
	}
	
}
