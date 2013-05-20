package nl.pojoquery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.SubClasses;
import nl.pojoquery.annotations.Table;

import org.junit.Test;

public class TestInheritance {
	@Table("room")
	@SubClasses({BedRoom.class, Kitchen.class})
	static class Room {
		@Id
		Long id;
		Double area;
	}
	
	@Table("bedroom")
	static class BedRoom extends Room {
		Integer numberOfBeds;
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
	public void testBasics() {
		String sql = PojoQuery.build(Room.class).toSql();
		
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
	public void testQuerySubType() {
		PojoQuery<BedRoom> q = PojoQuery.build(BedRoom.class);
		String sql = q.toSql();
		assertEquals(
				"SELECT" +
				" `room`.id `room.id`," +
				" `room`.area `room.area`," +
				" `bedroom`.id `bedroom.id`," +
				" `bedroom`.numberOfBeds `bedroom.numberOfBeds`" +
				" FROM bedroom" +
				" INNER JOIN room `room` ON `room`.id=`bedroom`.id",
				TestUtils.norm(sql));
		
		List<Map<String, Object>> result = TestUtils.resultSet(new String[] {
				"room.id", "room.area", "bedroom.id", "bedroom.numberOfBeds" }, 
			     1L,        100.0,       1L,           1);
		
		List<BedRoom> list = PojoQuery.processRows(result, BedRoom.class);
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
