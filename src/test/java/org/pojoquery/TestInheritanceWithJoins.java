package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pojoquery.TestUtils.norm;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.UseDialect;
import org.pojoquery.pipeline.AQTRowProcessor;
import org.pojoquery.pipeline.AQTTransformer;
import org.pojoquery.pipeline.AbstractQueryTree.FieldNode;
import org.pojoquery.pipeline.AbstractQueryTree.QueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.AbstractQueryTree.TPSSuperClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.DefaultSqlQuery;
import org.pojoquery.util.RecordIndenter;

@UseDialect(Dialect.MYSQL)
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
		
		// Room: single table with fields id, area, and EntityReference for house
		RootNode roomTree = AQTTransformer.buildQueryTreeForType(Room.class);
		Assertions.assertEquals("room", roomTree.tableInfo().tableName());
		Assertions.assertEquals(List.of("id", "area", "house"), getFieldNames(roomTree));
		// house is an EntityReference, verify it exists

		// BedRoom: bedroom table (numberOfBeds) + TPSSuperClassNode for room (id, area, house reference)
		RootNode bedroomTree = AQTTransformer.buildQueryTreeForType(BedRoom.class);
		Assertions.assertEquals("bedroom", bedroomTree.tableInfo().tableName());
		Assertions.assertEquals(List.of("numberOfBeds"), getFieldNames(bedroomTree));
		
		// Verify TPSSuperClassNode for room table exists with id, area fields
		TPSSuperClassNode roomSuperClass = findChild(bedroomTree, TPSSuperClassNode.class);
		Assertions.assertNotNull(roomSuperClass);
		Assertions.assertEquals("room", roomSuperClass.tableInfo().tableName());
		Assertions.assertEquals(List.of("id", "area", "house"), getFieldNames(roomSuperClass));

		// LuxuryBedRoom: extends BedRoom, so bedroom table has (numberOfBeds, tvScreenSize)
		RootNode luxuryTree = AQTTransformer.buildQueryTreeForType(LuxuryBedRoom.class);
		Assertions.assertEquals("bedroom", luxuryTree.tableInfo().tableName());
		Assertions.assertEquals(List.of("numberOfBeds", "tvScreenSize"), getFieldNames(luxuryTree));
	}
	
	@Test
	public void testSubClasses() {
		RootNode b = PojoQuery.buildAQT(Room.class);
		String sql = toSql(b);
		
		assertEquals(
				norm("""
					SELECT
					`room`.`id` AS `room.id`,
					`room`.`area` AS `room.area`,
					`house`.`id` AS `house.id`,
					`house`.`address` AS `house.address`,
					`room.bedroom`.`id` AS `room.bedroom.id`,
					`room.bedroom`.`numberOfBeds` AS `room.bedroom.numberOfBeds`,
					`room.kitchen`.`id` AS `room.kitchen.id`,
					`room.kitchen`.`hasDishWasher` AS `room.kitchen.hasDishWasher`
					FROM `room` AS `room`
					LEFT JOIN `house` AS `house` ON `room`.`house_id` = `house`.`id`
					LEFT JOIN `bedroom` AS `room.bedroom` ON `room`.`id` = `room.bedroom`.`id`
					LEFT JOIN `kitchen` AS `room.kitchen` ON `room`.`id` = `room.kitchen`.`id`
					"""),
				norm(sql));
		
		List<Map<String, Object>> result = TestUtils.resultSet(new String[] {
					"room.id", "room.area", "house.id", "house.address",  "room.bedroom.id", "room.bedroom.numberOfBeds", "room.kitchen.id", "room.kitchen.hasDishWasher" }, 
				     1L,        100.0,       1L,        "Unity Street 1", 1L,                1,                           null,         null,
				     2L,        40.0,        1L,        "Unity Street 1", null,              null,                        2L,           true);
		
		List<Room> rooms = AQTRowProcessor.processRows(DbContext.getDefault(), b, result);
		System.out.println("tree: " + RecordIndenter.indent(b.toString()));
		assertTrue(rooms.get(0) instanceof BedRoom);
		assertEquals(2, rooms.size());
		assertEquals((Object)1, ((BedRoom)rooms.get(0)).numberOfBeds);
		assertEquals(Boolean.TRUE, ((Kitchen)rooms.get(1)).hasDishWasher);
		assertEquals((Object)2L, rooms.get(1).id);
		assertEquals("Unity Street 1", rooms.get(1).house.address);
	}
	
	private String toSql(RootNode b) {
		DefaultSqlQuery query = new DefaultSqlQuery(DbContext.getDefault());
		AQTTransformer.toSql(b, query);
		return query.toStatement().getSql();
	}

	@Test
	public void testSuperclasses() {
		RootNode t = PojoQuery.buildAQT(BedRoom.class);
		String sql = toSql(t);
		System.out.println(sql);
		assertEquals(
				norm("""
					SELECT
					`bedroom`.`numberOfBeds` AS `bedroom.numberOfBeds`,
					`bedroom.room`.`id` AS `bedroom.room.id`,
					`bedroom.room`.`area` AS `bedroom.room.area`,
					`bedroom.room.house`.`id` AS `bedroom.room.house.id`,
					`bedroom.room.house`.`address` AS `bedroom.room.house.address`
					FROM `bedroom` AS `bedroom`
					LEFT JOIN `room` AS `bedroom.room` ON `bedroom`.`id` = `bedroom.room`.`id`
					LEFT JOIN `house` AS `bedroom.room.house` ON `bedroom.room`.`house_id` = `bedroom.room.house`.`id`
					"""),
				norm(sql));
		
		List<Map<String, Object>> result = TestUtils.resultSet(new String[] {
				"bedroom.room.id", "bedroom.room.area", "bedroom.numberOfBeds", "bedroom.room.house.id", "bedroom.room.house.address" }, 
			     1L,           100.0,          1                    ,  1L       , "Unity Street 1");
		
		List<BedRoom> list = AQTRowProcessor.processRows(DbContext.getDefault(), t, result);
		Assertions.assertEquals(1, list.size());
		BedRoom bedroom = list.get(0);
		Assertions.assertTrue(bedroom instanceof BedRoom);
		Assertions.assertEquals(100.0F, bedroom.area, 0.1F);
		Assertions.assertEquals((Integer)1, bedroom.numberOfBeds);
		Assertions.assertEquals("Unity Street 1", bedroom.house.address);
	}
	
	@Test
	public void testSuperClassOfLinked() {
		RootNode tree = PojoQuery.buildAQT(ApartmentWithSpecificProperties.class);
		String sql = toSql(tree);

		Assert.assertEquals(List.of("numberOfBeds"), 
			getFieldNames(tree.children().stream()
				.filter(it -> it instanceof TableNode tableNode && tableNode.alias().equals("bedrooms"))
				.map(TableNode.class::cast).findFirst().orElseThrow()));

		System.out.println("tree:" + RecordIndenter.indent(tree.toString()));
		assertEquals(
			norm("""
				SELECT
				`apartment`.`id` AS `apartment.id`,
				`bedrooms`.`numberOfBeds` AS `bedrooms.numberOfBeds`,
				`bedrooms.room`.`id` AS `bedrooms.room.id`,
				`bedrooms.room`.`area` AS `bedrooms.room.area`,
				`bedrooms.room.house`.`id` AS `bedrooms.room.house.id`,
				`bedrooms.room.house`.`address` AS `bedrooms.room.house.address`
				FROM `apartment` AS `apartment`
				LEFT JOIN `bedroom` AS `bedrooms` ON `bedrooms`.`apartment_id` = `apartment`.`id`
				LEFT JOIN `room` AS `bedrooms.room` ON `bedrooms`.`id` = `bedrooms.room`.`id`
				LEFT JOIN `house` AS `bedrooms.room.house` ON `bedrooms.room`.`house_id` = `bedrooms.room.house`.`id`
				"""), 
			norm(sql));
	}
	
	@Test
	public void testDeeper() {
		System.out.println(RecordIndenter.indent(AQTTransformer.buildQueryTreeForType(Apartment.class).toString()));

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
					`rooms.bedroom`.`id` AS `rooms.bedroom.id`,
					`rooms.bedroom`.`numberOfBeds` AS `rooms.bedroom.numberOfBeds`,
					`rooms.kitchen`.`id` AS `rooms.kitchen.id`,
					`rooms.kitchen`.`hasDishWasher` AS `rooms.kitchen.hasDishWasher`
					FROM `apartment` AS `apartment`
					LEFT JOIN `room` AS `rooms` ON `rooms`.`apartment_id` = `apartment`.`id`
					LEFT JOIN `house` AS `rooms.house` ON `rooms`.`house_id` = `rooms.house`.`id`
					LEFT JOIN `bedroom` AS `rooms.bedroom` ON `rooms`.`id` = `rooms.bedroom`.`id`
					LEFT JOIN `kitchen` AS `rooms.kitchen` ON `rooms`.`id` = `rooms.kitchen`.`id`
					"""),
				norm(sql));
		List<Map<String, Object>> result = TestUtils.resultSet(new String[] {
				"apartment.id", "rooms.id", "rooms.area", "rooms.house.id", "rooms.house.address", "rooms.bedroom.id", "rooms.bedroom.numberOfBeds", "rooms.kitchen.id", "rooms.kitchen.hasDishWasher" } 
			     ,1L            ,1L         ,100.0        ,1L               ,"Unity Street 1"      ,1L                 ,2                            ,null               ,null
			     );
		List<Apartment> list = qb.processRows(result);
		
		Room[] rooms = list.get(0).rooms;
		Assertions.assertEquals(1, rooms.length);
		Assertions.assertTrue(rooms[0] instanceof BedRoom);
		BedRoom bedroom = (BedRoom) rooms[0];
		Assertions.assertEquals((Double)100.0, bedroom.area);
		Assertions.assertEquals((Integer)2, bedroom.numberOfBeds);
		
	}
	
	private List<String> getFieldNames(TableNode node) {
		return node.children().stream()
				.filter(FieldNode.class::isInstance)
				.map(FieldNode.class::cast)
				.map(fn -> fn.field().getName())
				.toList();
	}
	
	@SuppressWarnings("unchecked")
	private <T extends QueryNode> T findChild(TableNode node, Class<T> type) {
		return (T) node.children().stream()
				.filter(type::isInstance)
				.findFirst()
				.orElse(null);
	}
	
}
