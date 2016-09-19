package nl.pojoquery;

import static nl.pojoquery.TestUtils.norm;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.SubClasses;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.internal.TableMapping;
import nl.pojoquery.pipeline.QueryBuilder;

import org.junit.Assert;
import org.junit.Test;

public class TestInheritanceWithJoins {
	
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
		List<TableMapping> mapping = QueryBuilder.determineTableMapping(Room.class);
		Assert.assertEquals(1, mapping.size());
		Assert.assertEquals(Arrays.asList(Entity.class.getDeclaredField("id"), Room.class.getDeclaredField("area"), Room.class.getDeclaredField("house")), mapping.get(0).fields);
		
		List<TableMapping> bedroom = QueryBuilder.determineTableMapping(BedRoom.class);
		Assert.assertEquals(2, bedroom.size());
		Assert.assertEquals(Arrays.asList(Entity.class.getDeclaredField("id"), Room.class.getDeclaredField("area"), Room.class.getDeclaredField("house")), bedroom.get(0).fields);
		Assert.assertEquals(Arrays.asList(BedRoom.class.getDeclaredField("numberOfBeds")), bedroom.get(1).fields);
		
		List<TableMapping> luxury = QueryBuilder.determineTableMapping(LuxuryBedRoom.class);
		Assert.assertEquals(2, luxury.size());
		Assert.assertEquals(Arrays.asList(BedRoom.class.getDeclaredField("numberOfBeds"), LuxuryBedRoom.class.getDeclaredField("tvScreenSize")), luxury.get(1).fields);
	}
	
	@Test
	public void testSubClasses() {
		PojoQuery<Room> b = PojoQuery.build(Room.class);
		String sql = b.toSql();
		
		assertEquals(
				norm("SELECT\n" + 
						" `room`.id AS `room.id`,\n" + 
						" `room`.area AS `room.area`,\n" + 
						" `house`.id AS `house.id`,\n" + 
						" `house`.address AS `house.address`,\n" + 
						" `room.bedroom`.id AS `room.bedroom.id`,\n" + 
						" `room.bedroom`.numberOfBeds AS `room.bedroom.numberOfBeds`,\n" + 
						" `room.kitchen`.id AS `room.kitchen.id`,\n" + 
						" `room.kitchen`.hasDishWasher AS `room.kitchen.hasDishWasher`\n" + 
						"FROM room\n" + 
						" LEFT JOIN house AS `house` ON `room`.house_id = `house`.id\n" + 
						" LEFT JOIN bedroom AS `room.bedroom` ON `room.bedroom`.id = `room`.id\n" + 
						" LEFT JOIN kitchen AS `room.kitchen` ON `room.kitchen`.id = `room`.id"),
				norm(sql));
		
		List<Map<String, Object>> result = TestUtils.resultSet(new String[] {
					"room.id", "room.area", "house.id", "house.address", "room.bedroom.id", "room.bedroom.numberOfBeds", "room.kitchen.id", "room.kitchen.hasDishWasher" }, 
				     1L,        100.0,       1L,        "Unity Street 1",  1L,           1,                      null,         null,
				     2L,        40.0,        1L,        "Unity Street 1",  null,         null,                   2L,           true);
		
		List<Room> room = b.processRows(result);
		assertTrue(room.get(0) instanceof BedRoom);
		assertEquals((Object)1, ((BedRoom)room.get(0)).numberOfBeds);
		assertEquals(Boolean.TRUE, ((Kitchen)room.get(1)).hasDishWasher);
		assertEquals((Object)2L, room.get(1).id);
		assertEquals("Unity Street 1", room.get(1).house.address);
	}
	
	@Test
	public void testSuperclasses() {
		QueryBuilder<BedRoom> q = QueryBuilder.from(BedRoom.class);
		String sql = q.toStatement().getSql();
		System.out.println(sql);
		assertEquals(
				norm("SELECT\n" + 
						" `bedroom`.numberOfBeds AS `bedroom.numberOfBeds`,\n" + 
						" `room`.id AS `bedroom.id`,\n" + 
						" `room`.area AS `bedroom.area`,\n" + 
						" `house`.id AS `house.id`,\n" + 
						" `house`.address AS `house.address`\n" + 
						"FROM bedroom\n" + 
						" LEFT JOIN room AS `room` ON `room`.id = `bedroom`.id\n" + 
						" LEFT JOIN house AS `house` ON `room`.house_id = `house`.id"),
				norm(sql));
		
		List<Map<String, Object>> result = TestUtils.resultSet(new String[] {
				"bedroom.id", "bedroom.area", "bedroom.numberOfBeds", "house.id", "house.address" }, 
			     1L,           100.0,          1                    ,  1L       , "Unity Street 1");
		
		List<BedRoom> list = QueryBuilder.from(BedRoom.class).processRows(result);
		Assert.assertEquals(1, list.size());
		BedRoom bedroom = list.get(0);
		Assert.assertTrue(bedroom instanceof BedRoom);
		Assert.assertEquals(100.0F, bedroom.area, 0.1F);
		Assert.assertEquals((Integer)1, bedroom.numberOfBeds);
		Assert.assertEquals("Unity Street 1", bedroom.house.address);
	}
	
	@Test
	public void testSuperClassOfLinked() {
		String sql = QueryBuilder.from(ApartmentWithSpecificProperties.class).toStatement().getSql();
		System.out.println(sql);
		assertEquals(
			norm(
				"SELECT\n" + 
				" `apartment`.id AS `apartment.id`,\n" + 
				" `bedrooms`.numberOfBeds AS `bedrooms.numberOfBeds`,\n" + 
				" `bedrooms.room`.id AS `bedrooms.id`,\n" + 
				" `bedrooms.room`.area AS `bedrooms.area`,\n" + 
				" `bedrooms.house`.id AS `bedrooms.house.id`,\n" + 
				" `bedrooms.house`.address AS `bedrooms.house.address`\n" + 
				"FROM apartment\n" + 
				" LEFT JOIN bedroom AS `bedrooms` ON `apartment`.id = `bedrooms`.apartment_id\n" + 
				" LEFT JOIN room AS `bedrooms.room` ON `bedrooms.room`.id = `bedrooms`.id\n" + 
				" LEFT JOIN house AS `bedrooms.house` ON `bedrooms.room`.house_id = `bedrooms.house`.id"), 
			norm(sql));
	}
	
	@Test
	public void testDeeper() {
		QueryBuilder<Apartment> qb = QueryBuilder.from(Apartment.class);
		String sql = qb.toStatement().getSql();
		assertEquals(
				norm("SELECT\n" + 
						" `apartment`.id AS `apartment.id`,\n" + 
						" `rooms`.id AS `rooms.id`,\n" + 
						" `rooms`.area AS `rooms.area`,\n" + 
						" `rooms.house`.id AS `rooms.house.id`,\n" + 
						" `rooms.house`.address AS `rooms.house.address`,\n" + 
						" `rooms.bedroom`.id AS `rooms.bedroom.id`,\n" + 
						" `rooms.bedroom`.numberOfBeds AS `rooms.bedroom.numberOfBeds`,\n" + 
						" `rooms.kitchen`.id AS `rooms.kitchen.id`,\n" + 
						" `rooms.kitchen`.hasDishWasher AS `rooms.kitchen.hasDishWasher`\n" + 
						"FROM apartment\n" + 
						" LEFT JOIN room AS `rooms` ON `apartment`.id = `rooms`.apartment_id\n" + 
						" LEFT JOIN house AS `rooms.house` ON `rooms`.house_id = `rooms.house`.id\n" + 
						" LEFT JOIN bedroom AS `rooms.bedroom` ON `rooms.bedroom`.id = `rooms`.id\n" + 
						" LEFT JOIN kitchen AS `rooms.kitchen` ON `rooms.kitchen`.id = `rooms`.id"),
				norm(sql));
		List<Map<String, Object>> result = TestUtils.resultSet(new String[] {
				"apartment.id", "rooms.id", "rooms.area", "rooms.house.id", "rooms.house.address", "rooms.bedroom.id", "rooms.bedroom.numberOfBeds", "rooms.kitchen.id", "rooms.kitchen.hasDishWasher" } 
			     ,1L            ,1L         ,100.0        ,1L               ,"Unity Street 1"      ,1L                 ,2                            ,null               ,null
			     );
		List<Apartment> list = qb.processRows(result);
		
		Room[] rooms = list.get(0).rooms;
		Assert.assertEquals(1, rooms.length);
		Assert.assertTrue(rooms[0] instanceof BedRoom);
		BedRoom bedroom = (BedRoom) rooms[0];
		Assert.assertEquals((Double)100.0, bedroom.area);
		Assert.assertEquals((Integer)2, bedroom.numberOfBeds);
		
	}
	
}
