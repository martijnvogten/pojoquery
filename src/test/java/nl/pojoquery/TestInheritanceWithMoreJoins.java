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
				norm("SELECT\n"
						+ " `beds`.id AS `beds.id`,\n"
						+ " `beds`.width AS `beds.width`,\n"
						+ " `room`.id AS `bedroom.id`,\n"
						+ " `room`.area AS `bedroom.area`,\n"
						+ " `house`.id AS `house.id`,\n"
						+ " `house`.address AS `house.address`,\n"
						+ " `windows`.id AS `windows.id`,\n"
						+ " `windows`.width AS `windows.width`\n"
						+ "FROM `bedroom` AS `bedroom`\n"
						+ " INNER JOIN `room` AS `room` ON `room`.id = `bedroom`.id\n"
						+ " LEFT JOIN `bed` AS `beds` ON `bedroom`.id = `beds`.bedroom_id\n"
						+ " LEFT JOIN `house` AS `house` ON `room`.house_id = `house`.id\n"
						+ " LEFT JOIN `window` AS `windows` ON `room`.id = `windows`.room_id"),
				norm(sql));
	}
	
	@Test
	public void testApartment() {
		QueryBuilder<Apartment> qb = QueryBuilder.from(Apartment.class);
		String sql = qb.toStatement().getSql();
		assertEquals(
				norm("SELECT\n"
						+ " `apartment`.id AS `apartment.id`,\n"
						+ " `rooms`.id AS `rooms.id`,\n"
						+ " `rooms`.area AS `rooms.area`,\n"
						+ " `rooms.house`.id AS `rooms.house.id`,\n"
						+ " `rooms.house`.address AS `rooms.house.address`,\n"
						+ " `rooms.windows`.id AS `rooms.windows.id`,\n"
						+ " `rooms.windows`.width AS `rooms.windows.width`,\n"
						+ " `rooms.bedroom`.id AS `rooms.bedroom.id`,\n"
						+ " `rooms.bedroom.beds`.id AS `rooms.bedroom.beds.id`,\n"
						+ " `rooms.bedroom.beds`.width AS `rooms.bedroom.beds.width`,\n"
						+ " `rooms.kitchen`.id AS `rooms.kitchen.id`,\n"
						+ " `rooms.kitchen`.hasDishWasher AS `rooms.kitchen.hasDishWasher`\n"
						+ "FROM `apartment` AS `apartment`\n"
						+ " LEFT JOIN `room` AS `rooms` ON `apartment`.id = `rooms`.apartment_id\n"
						+ " LEFT JOIN `house` AS `rooms.house` ON `rooms`.house_id = `rooms.house`.id\n"
						+ " LEFT JOIN `window` AS `rooms.windows` ON `rooms`.id = `rooms.windows`.room_id\n"
						+ " LEFT JOIN `bedroom` AS `rooms.bedroom` ON `rooms.bedroom`.id = `rooms`.id\n"
						+ " LEFT JOIN `bed` AS `rooms.bedroom.beds` ON `rooms.bedroom`.id = `rooms.bedroom.beds`.bedroom_id\n"
						+ " LEFT JOIN `kitchen` AS `rooms.kitchen` ON `rooms.kitchen`.id = `rooms`.id"),
				norm(sql));
	}
	
}
