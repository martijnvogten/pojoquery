package nl.pojoquery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import nl.pojoquery.PojoQuery.TableMapping;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.SubClasses;
import nl.pojoquery.annotations.Table;

import org.junit.Assert;
import org.junit.Test;

public class TestInheritance {
	
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
		Long id;
		Room[] rooms;
	}
	
	@Test
	public void testBuildTableHierarchy() throws Exception {
		// When querying a superclasses, we want a list of all tables
		// and fields per table
		List<TableMapping> mapping = PojoQuery.determineTableMapping(Room.class);
		Assert.assertEquals(1, mapping.size());
		Assert.assertEquals(Arrays.asList(Entity.class.getDeclaredField("id"), Room.class.getDeclaredField("area")), mapping.get(0).fields);
		
		List<TableMapping> bedroom = PojoQuery.determineTableMapping(BedRoom.class);
		Assert.assertEquals(2, bedroom.size());
		Assert.assertEquals(Arrays.asList(Entity.class.getDeclaredField("id"), Room.class.getDeclaredField("area")), bedroom.get(0).fields);
		Assert.assertEquals(Arrays.asList(BedRoom.class.getDeclaredField("numberOfBeds")), bedroom.get(1).fields);
		
		List<TableMapping> luxury = PojoQuery.determineTableMapping(LuxuryBedRoom.class);
		Assert.assertEquals(2, luxury.size());
		Assert.assertEquals(Arrays.asList(BedRoom.class.getDeclaredField("numberOfBeds"), LuxuryBedRoom.class.getDeclaredField("tvScreenSize")), luxury.get(1).fields);
	}
	
	@Test
	public void testSubClasses() {
		String sql = PojoQuery.build(Room.class).toSql();
		
		System.out.println(sql);
		
		assertEquals(
				"SELECT" +
				" `room`.id `room.id`," +
				" `room`.area `room.area`," +
				" `bedroom`.id `bedroom.id`," +
				" `bedroom`.numberOfBeds `bedroom.numberOfBeds`," +
				" `kitchen`.id `kitchen.id`," +
				" `kitchen`.hasDishWasher `kitchen.hasDishWasher`" +
				" FROM room" +
				" LEFT JOIN bedroom `bedroom` ON `bedroom`.id=`room`.id" +
				" LEFT JOIN kitchen `kitchen` ON `kitchen`.id=`room`.id",
				TestUtils.norm(sql));
		
		List<Map<String, Object>> result = TestUtils.resultSet(new String[] {
					"room.id", "room.area", "bedroom.id", "bedroom.numberOfBeds", "kitchen.id", "kitchen.hasDishWasher" }, 
				     1L,        100.0,       1L,           1,                      null,         null,
				     2L,        40.0,        null,         null,                   2L,           true);
		
		List<Room> room = PojoQuery.processRows(result, Room.class);
		assertTrue(room.get(0) instanceof BedRoom);
		assertEquals((Object)1, ((BedRoom)room.get(0)).numberOfBeds);
		assertEquals(Boolean.TRUE, ((Kitchen)room.get(1)).hasDishWasher);
		assertEquals((Object)2L, room.get(1).id);
	}
	
	@Test
	public void testSuperclasses() {
		PojoQuery<BedRoom> q = PojoQuery.build(BedRoom.class);
		String sql = q.toSql();
		assertEquals(
				"SELECT" +
				" `room`.id `room.id`," +
				" `room`.area `room.area`," +
				" `bedroom`.id `bedroom.id`," +
				" `bedroom`.numberOfBeds `bedroom.numberOfBeds`" +
				" FROM room" +
				" INNER JOIN bedroom `bedroom` ON `bedroom`.id=`room`.id",
				TestUtils.norm(sql));
		
		List<Map<String, Object>> result = TestUtils.resultSet(new String[] {
				"room.id", "room.area", "bedroom.id", "bedroom.numberOfBeds" }, 
			     1L,        100.0,       1L,           1);
		
		List<BedRoom> list = PojoQuery.processRows(result, BedRoom.class);
		Assert.assertTrue(list.get(0) instanceof BedRoom);
		
	}
	
	@Test
	public void testDeeper() {
		String sql = PojoQuery.build(Apartment.class).toSql();
		
		assertEquals(
				"SELECT" +
				" `apartment`.id `apartment.id`," +
				" `rooms`.id `rooms.id`," +
				" `rooms`.area `rooms.area`," +
				" `rooms.bedroom`.id `rooms.bedroom.id`," +
				" `rooms.bedroom`.numberOfBeds `rooms.bedroom.numberOfBeds`," +
				" `rooms.kitchen`.id `rooms.kitchen.id`," +
				" `rooms.kitchen`.hasDishWasher `rooms.kitchen.hasDishWasher`" +
				" FROM apartment" +
				" LEFT JOIN room `rooms` ON `rooms`.apartment_id=`apartment`.id" +
				" LEFT JOIN bedroom `rooms.bedroom` ON `rooms.bedroom`.id=`rooms`.id" +
				" LEFT JOIN kitchen `rooms.kitchen` ON `rooms.kitchen`.id=`rooms`.id",
				TestUtils.norm(sql));
	}

}
