package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.pojoquery.TestUtils.norm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.JoinCondition;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Table;

public class TestInheritanceWithMoreJoins {

	@BeforeEach
	public void setup() {
		DbContext.setDefault(DbContext.forDialect(Dialect.MYSQL));
	}
	
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
		
		@JoinCondition("{this.id} = {windows}.`room_id`")
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
		PojoQuery<BedRoom> qb = PojoQuery.build(BedRoom.class);
		String sql = qb.toStatement().getSql();
		assertEquals(
				norm("""
					SELECT
					`beds`.`id` AS `beds.id`,
					`beds`.`width` AS `beds.width`,
					`bedroom.room`.`id` AS `bedroom.room.id`,
					`bedroom.room`.`area` AS `bedroom.room.area`,
					`bedroom.room.house`.`id` AS `bedroom.room.house.id`,
					`bedroom.room.house`.`address` AS `bedroom.room.house.address`,
					`bedroom.room.windows`.`id` AS `bedroom.room.windows.id`,
					`bedroom.room.windows`.`width` AS `bedroom.room.windows.width`
					FROM `bedroom` AS `bedroom`
					LEFT JOIN `bed` AS `beds` ON `beds`.`bedroom_id` = `bedroom`.`id`
					LEFT JOIN `room` AS `bedroom.room` ON `bedroom`.`id` = `bedroom.room`.`id`
					LEFT JOIN `house` AS `bedroom.room.house` ON `bedroom.room`.`house_id` = `bedroom.room.house`.`id`
					LEFT JOIN `window` AS `bedroom.room.windows` ON `bedroom.room.windows`.`room_id` = `bedroom.room`.`id`
					"""),
				norm(sql));
	}
	
	@Test
	public void testApartment() {
		PojoQuery<Apartment> qb = PojoQuery.build(Apartment.class);
		String sql = qb.toStatement().getSql();
		assertEquals(
				norm("""
					SELECT
					`apartment`.`id` AS `apartment.id`,
					`rooms`.`id` AS `rooms.id`,
					`rooms`.`area` AS `rooms.area`,
					`rooms.house`.`id` AS `rooms.house.id`,
					`rooms.house`.`address` AS `rooms.house.address`,
					`rooms.windows`.`id` AS `rooms.windows.id`,
					`rooms.windows`.`width` AS `rooms.windows.width`,
					`rooms.bedroom`.`id` AS `rooms.bedroom.id`,
					`rooms.bedroom.beds`.`id` AS `rooms.bedroom.beds.id`,
					`rooms.bedroom.beds`.`width` AS `rooms.bedroom.beds.width`,
					`rooms.kitchen`.`id` AS `rooms.kitchen.id`,
					`rooms.kitchen`.`hasDishWasher` AS `rooms.kitchen.hasDishWasher`
					FROM `apartment` AS `apartment`
					LEFT JOIN `room` AS `rooms` ON `rooms`.`apartment_id` = `apartment`.`id`
					LEFT JOIN `house` AS `rooms.house` ON `rooms`.`house_id` = `rooms.house`.`id`
					LEFT JOIN `window` AS `rooms.windows` ON `rooms.windows`.`room_id` = `rooms`.`id`
					LEFT JOIN `bedroom` AS `rooms.bedroom` ON `rooms`.`id` = `rooms.bedroom`.`id`
					LEFT JOIN `bed` AS `rooms.bedroom.beds` ON `rooms.bedroom.beds`.`bedroom_id` = `rooms.bedroom`.`id`
					LEFT JOIN `kitchen` AS `rooms.kitchen` ON `rooms`.`id` = `rooms.kitchen`.`id`
					"""),
				norm(sql));
	}
	
}
