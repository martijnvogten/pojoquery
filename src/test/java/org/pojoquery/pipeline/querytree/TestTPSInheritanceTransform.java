package org.pojoquery.pipeline.querytree;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Table;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.pipeline.querytree.transforms.CreateRootTransform;
import org.pojoquery.pipeline.querytree.transforms.TPSInheritanceTransform;

/**
 * Tests for TPSInheritanceTransform (Table Per Subclass inheritance).
 * 
 * Verifies that the transform:
 * - Creates JoinedNode (EmptyTableNode with joinInfo) for each table in the hierarchy
 * - Sets correct join conditions (shared primary key)
 * - Uses INNER JOIN for superclasses of root, LEFT JOIN for subclasses
 * - Handles multi-level hierarchies recursively
 */
public class TestTPSInheritanceTransform {

	// ===========================================
	// Simple hierarchy: Entity -> Room -> BedRoom
	// ===========================================
	
	@Table("entity")
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
	@SubClasses({MasterBedRoom.class})
	static class BedRoom extends Room {
		Integer numberOfBeds;
	}

	@Table("kitchen")
	static class Kitchen extends Room {
		Boolean hasOven;
	}

	@Table("masterbedroom")
	static class MasterBedRoom extends BedRoom {
		Boolean hasEnsuite;
	}

	@Table("house")
	static class House {
		@Id
		Long id;
		String address;
	}

	// ===========================================
	// Tests
	// ===========================================

	@Test
	public void testSimpleSuperclass() {
		// Room extends Entity: should add Entity as INNER JOIN superclass
		QueryTree tree = applyTransform(Room.class);
		EmptyTableNode root = (EmptyTableNode) tree.root();
		
		assertEquals("room", root.alias());
		assertEquals(1, countSuperclassChildren(root)); // Entity superclass
		
		EmptyTableNode entityNode = findChildByTableName(root, "entity");
		assertNotNull("Should have entity superclass", entityNode);
		assertTrue("Entity should be marked as superclass", entityNode.isSuperClass());
		assertFalse("Entity should not be marked as subclass", entityNode.isSubClass());
		
		// Superclass of root should use INNER JOIN
		assertNotNull("Entity should have joinInfo", entityNode.joinInfo());
		assertEquals(JoinType.INNER, entityNode.joinInfo().joinType());
		assertEquals("entity", entityNode.joinInfo().childTable().tableName());
	}

	@Test
	public void testSimpleSubclasses() {
		// Room has @SubClasses({BedRoom, Kitchen}): should add both as LEFT JOIN
		QueryTree tree = applyTransform(Room.class);
		EmptyTableNode root = (EmptyTableNode) tree.root();
		
		assertEquals(3, countSubclassChildren(root)); // BedRoom, Kitchen, MasterBedRoom (nested subclass)
		
		EmptyTableNode bedroomNode = findChildByTableName(root, "bedroom");
		assertNotNull("Should have bedroom subclass", bedroomNode);
		assertTrue("BedRoom should be marked as subclass", bedroomNode.isSubClass());
		assertFalse("BedRoom should not be marked as superclass", bedroomNode.isSuperClass());
		assertEquals(JoinType.LEFT, bedroomNode.joinInfo().joinType());
		
		EmptyTableNode kitchenNode = findChildByTableName(root, "kitchen");
		assertNotNull("Should have kitchen subclass", kitchenNode);
		assertTrue(kitchenNode.isSubClass());
		assertEquals(JoinType.LEFT, kitchenNode.joinInfo().joinType());
	}

	@Test
	public void testRecursiveSuperclasses() {
		// BedRoom extends Room extends Entity: should add both Room and Entity
		QueryTree tree = applyTransform(BedRoom.class);
		EmptyTableNode root = (EmptyTableNode) tree.root();
		
		assertEquals("bedroom", root.alias());
		assertEquals(2, countSuperclassChildren(root)); // Room, Entity
		
		EmptyTableNode roomNode = findChildByTableName(root, "room");
		assertNotNull("Should have room superclass", roomNode);
		assertTrue(roomNode.isSuperClass());
		
		EmptyTableNode entityNode = findChildByTableName(root, "entity");
		assertNotNull("Should have entity superclass", entityNode);
		assertTrue(entityNode.isSuperClass());
	}

	@Test
	public void testRecursiveSubclasses() {
		// Room -> BedRoom -> MasterBedRoom: should add nested subclass
		QueryTree tree = applyTransform(Room.class);
		EmptyTableNode root = (EmptyTableNode) tree.root();
		
		// Root should have BedRoom and Kitchen as direct subclasses
		EmptyTableNode bedroomNode = findChildByTableName(root, "bedroom");
		assertNotNull(bedroomNode);
		
		// BedRoom has @SubClasses({MasterBedRoom}), so we should find masterbedroom
		EmptyTableNode masterNode = findChildByTableName(root, "masterbedroom");
		assertNotNull("Should have masterbedroom as recursive subclass", masterNode);
		assertTrue(masterNode.isSubClass());
	}

	@Test
	public void testJoinConditionIsSharedPrimaryKey() {
		QueryTree tree = applyTransform(Room.class);
		EmptyTableNode root = (EmptyTableNode) tree.root();
		
		EmptyTableNode entityNode = findChildByTableName(root, "entity");
		assertNotNull(entityNode.joinInfo().joinCondition());
		assertTrue("Join condition should be SharedPrimaryKey", 
			entityNode.joinInfo().joinCondition() instanceof JoinCondition.SharedPrimaryKey);
		
		JoinCondition.SharedPrimaryKey condition = (JoinCondition.SharedPrimaryKey) entityNode.joinInfo().joinCondition();
		assertEquals("id", condition.parentColumn());
	}

	@Test
	public void testIdempotency() {
		QueryTree tree1 = applyTransform(Room.class);
		QueryTree tree2 = new TPSInheritanceTransform().apply(tree1);
		
		// Should produce same structure when applied twice
		assertEquals(countAllChildren(tree1.root()), countAllChildren(tree2.root()));
	}

	@Test
	public void testNoInheritance() {
		// House has no superclass with @Table, no @SubClasses
		QueryTree tree = applyTransform(House.class);
		EmptyTableNode root = (EmptyTableNode) tree.root();
		
		assertEquals("house", root.alias());
		assertEquals(0, countSuperclassChildren(root));
		assertEquals(0, countSubclassChildren(root));
	}

	@Test
	public void testAliasNaming() {
		QueryTree tree = applyTransform(Room.class);
		EmptyTableNode root = (EmptyTableNode) tree.root();
		
		// Check alias naming follows pattern: rootAlias$tableName
		EmptyTableNode entityNode = findChildByTableName(root, "entity");
		assertEquals("room.entity", entityNode.alias());
		
		EmptyTableNode bedroomNode = findChildByTableName(root, "bedroom");
		assertEquals("room.bedroom", bedroomNode.alias());
	}

	// ===========================================
	// Helper methods
	// ===========================================

	private QueryTree applyTransform(Class<?> clazz) {
		QueryTree initial = new CreateRootTransform().apply(QueryTree.of(clazz));
		return new TPSInheritanceTransform().apply(initial);
	}

	private int countSuperclassChildren(QueryNode node) {
		return (int) node.children().stream()
			.filter(c -> c instanceof EmptyTableNode e && e.isSuperClass())
			.count();
	}

	private int countSubclassChildren(QueryNode node) {
		return (int) node.children().stream()
			.filter(c -> c instanceof EmptyTableNode e && e.isSubClass())
			.count();
	}

	private int countAllChildren(QueryNode node) {
		return node.children().size() + 
			node.children().stream().mapToInt(this::countAllChildren).sum();
	}

	private EmptyTableNode findChildByTableName(QueryNode parent, String tableName) {
		// Search recursively through all children
		for (QueryNode child : parent.children()) {
			if (child instanceof EmptyTableNode e) {
				if (e.joinInfo() != null && e.joinInfo().childTable() != null 
					&& tableName.equals(e.joinInfo().childTable().tableName())) {
					return e;
				}
				// Recurse
				EmptyTableNode found = findChildByTableName(e, tableName);
				if (found != null) return found;
			}
		}
		return null;
	}
}
