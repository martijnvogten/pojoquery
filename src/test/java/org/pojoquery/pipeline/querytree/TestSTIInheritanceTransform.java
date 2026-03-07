package org.pojoquery.pipeline.querytree;

import static org.junit.Assert.*;

import org.junit.Test;
import org.pojoquery.annotations.DiscriminatorColumn;
import org.pojoquery.annotations.DiscriminatorValue;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Table;
import org.pojoquery.pipeline.querytree.transforms.CreateRootTransform;
import org.pojoquery.pipeline.querytree.transforms.STIInheritanceTransform;

/**
 * Tests for STIInheritanceTransform (Single Table Inheritance).
 * 
 * Verifies that the transform:
 * - Creates EmbeddedNode (EmptyTableNode with embedInfo) for each class in hierarchy
 * - All nodes share the same sourceAlias (root's alias) since they're in one table
 * - No JOIN is added (all classes are in the same table)
 * - Handles multi-level hierarchies recursively
 */
public class TestSTIInheritanceTransform {

	// ===========================================
	// STI hierarchy: Entity -> Room -> BedRoom/Kitchen
	// All in one "room" table with discriminator
	// ===========================================
	
	@Table("entity")
	static class Entity {
		@Id
		Long id;
	}

	@Table("room")
	@DiscriminatorColumn(name = "room_type")
	@SubClasses({BedRoom.class, Kitchen.class})
	static class Room extends Entity {
		Double area;
	}

	@DiscriminatorValue("BedRoom")
	@SubClasses({MasterBedRoom.class})
	static class BedRoom extends Room {
		Integer numberOfBeds;
	}

	@DiscriminatorValue("Kitchen")
	static class Kitchen extends Room {
		Boolean hasOven;
	}

	@DiscriminatorValue("MasterBedRoom")
	static class MasterBedRoom extends BedRoom {
		Boolean hasEnsuite;
	}

	// Deeper hierarchy: root has @DiscriminatorColumn, all levels share same table
	@Table("vehicle")
	@DiscriminatorColumn
	@SubClasses({Car.class, Truck.class})
	static class Vehicle {
		@Id
		Long id;
		String brand;
	}

	@DiscriminatorValue("Car")
	@SubClasses({SportsCar.class})
	static class Car extends Vehicle {
		Integer doors;
	}

	@DiscriminatorValue("SportsCar")
	static class SportsCar extends Car {
		Integer topSpeed;
	}

	@DiscriminatorValue("Truck")
	static class Truck extends Vehicle {
		Integer loadCapacity;
	}

	// ===========================================
	// Tests
	// ===========================================

	@Test
	public void testSubclassesAreEmbedded() {
		// Room with @DiscriminatorColumn: BedRoom and Kitchen should be EmbeddedNodes
		QueryTree tree = applyTransform(Room.class);
		EmptyTableNode root = (EmptyTableNode) tree.root();
		
		assertEquals("room", root.alias());
		
		// Should have embedded subclasses, not joined
		EmptyTableNode bedroomNode = findEmbeddedChild(root, BedRoom.class);
		assertNotNull("Should have BedRoom as embedded child", bedroomNode);
		assertTrue("BedRoom should be embedded (have embedInfo)", bedroomNode.isEmbedded());
		assertNull("BedRoom should NOT have joinInfo", bedroomNode.joinInfo());
		assertTrue("BedRoom should be marked as subclass", bedroomNode.isSubClass());
		
		EmptyTableNode kitchenNode = findEmbeddedChild(root, Kitchen.class);
		assertNotNull("Should have Kitchen as embedded child", kitchenNode);
		assertTrue(kitchenNode.isEmbedded());
	}

	@Test
	public void testSourceAliasIsShared() {
		// All STI nodes should share the root's sourceAlias
		QueryTree tree = applyTransform(Room.class);
		EmptyTableNode root = (EmptyTableNode) tree.root();
		String rootAlias = root.alias();
		
		EmptyTableNode bedroomNode = findEmbeddedChild(root, BedRoom.class);
		assertEquals("BedRoom sourceAlias should be root alias", rootAlias, bedroomNode.embedInfo().sourceAlias());
		
		EmptyTableNode kitchenNode = findEmbeddedChild(root, Kitchen.class);
		assertEquals("Kitchen sourceAlias should be root alias", rootAlias, kitchenNode.embedInfo().sourceAlias());
	}

	@Test
	public void testRecursiveSubclasses() {
		// Room -> BedRoom -> MasterBedRoom: all should be embedded with same sourceAlias
		QueryTree tree = applyTransform(Room.class);
		EmptyTableNode root = (EmptyTableNode) tree.root();
		
		// Find MasterBedRoom (nested subclass)
		EmptyTableNode masterNode = findEmbeddedChildRecursive(root, MasterBedRoom.class);
		assertNotNull("Should have MasterBedRoom as nested embedded child", masterNode);
		assertTrue(masterNode.isEmbedded());
		assertTrue(masterNode.isSubClass());
		assertEquals("MasterBedRoom should share root's sourceAlias", root.alias(), masterNode.embedInfo().sourceAlias());
	}

	@Test
	public void testSuperclassWithDiscriminator() {
		// When querying a subclass directly (BedRoom), parent Room should be embedded
		// NOT YET NEEDED? Or should superclass traversal happen?
		// For now we just verify subclasses work
	}

	@Test
	public void testNoDiscriminatorSkipsTransform() {
		// A class without @DiscriminatorColumn should not be processed
		QueryTree tree = applyTransform(Entity.class);
		EmptyTableNode root = (EmptyTableNode) tree.root();
		
		assertEquals("entity", root.alias());
		assertEquals(0, root.children().size()); // No embedded children added
	}

	@Test
	public void testIdempotency() {
		QueryTree tree1 = applyTransform(Room.class);
		QueryTree tree2 = new STIInheritanceTransform().apply(tree1);
		
		// Should produce same structure when applied twice
		assertEquals(countAllChildren(tree1.root()), countAllChildren(tree2.root()));
	}

	@Test
	public void testAliasNaming() {
		QueryTree tree = applyTransform(Room.class);
		EmptyTableNode root = (EmptyTableNode) tree.root();
		
		EmptyTableNode bedroomNode = findEmbeddedChild(root, BedRoom.class);
		// Alias should contain class name to make it unique
		assertTrue("Subclass alias should contain class identifier", 
			bedroomNode.alias().contains("BedRoom") || bedroomNode.alias().contains("$"));
	}

	@Test
	public void testDeeperHierarchy() {
		// Vehicle -> Car -> SportsCar, Vehicle -> Truck
		QueryTree tree = applyTransform(Vehicle.class);
		EmptyTableNode root = (EmptyTableNode) tree.root();
		
		assertEquals("vehicle", root.alias());
		
		// Direct subclasses
		assertNotNull(findEmbeddedChild(root, Car.class));
		assertNotNull(findEmbeddedChild(root, Truck.class));
		
		// Nested subclass
		EmptyTableNode sportsCarNode = findEmbeddedChildRecursive(root, SportsCar.class);
		assertNotNull("Should have SportsCar as nested subclass", sportsCarNode);
		assertEquals("SportsCar should share root sourceAlias", root.alias(), sportsCarNode.embedInfo().sourceAlias());
	}

	@Test
	public void testEmbedInfoHasNoFieldPrefix() {
		// STI embedded nodes don't need a field prefix (columns aren't prefixed)
		QueryTree tree = applyTransform(Room.class);
		EmptyTableNode root = (EmptyTableNode) tree.root();
		
		EmptyTableNode bedroomNode = findEmbeddedChild(root, BedRoom.class);
		assertEquals("STI should have empty field prefix", "", bedroomNode.embedInfo().fieldPrefix());
	}

	@Test
	public void testSuperTypeIsRecorded() {
		// The embedInfo should record the parent type for later discriminator resolution
		QueryTree tree = applyTransform(Room.class);
		EmptyTableNode root = (EmptyTableNode) tree.root();
		
		EmptyTableNode bedroomNode = findEmbeddedChild(root, BedRoom.class);
		assertNotNull("EmbedInfo should record superType", bedroomNode.embedInfo().superType());
		assertEquals(Room.class.getName(), bedroomNode.embedInfo().superType().getQualifiedName());
	}

	// ===========================================
	// Helper methods
	// ===========================================

	private QueryTree applyTransform(Class<?> clazz) {
		QueryTree initial = new CreateRootTransform().apply(QueryTree.of(clazz));
		return new STIInheritanceTransform().apply(initial);
	}

	private int countAllChildren(QueryNode node) {
		return node.children().size() + 
			node.children().stream().mapToInt(this::countAllChildren).sum();
	}

	private EmptyTableNode findEmbeddedChild(QueryNode parent, Class<?> type) {
		for (QueryNode child : parent.children()) {
			if (child instanceof EmptyTableNode e && e.isEmbedded()) {
				if (type.getName().equals(e.type().getQualifiedName())) {
					return e;
				}
			}
		}
		return null;
	}

	private EmptyTableNode findEmbeddedChildRecursive(QueryNode parent, Class<?> type) {
		for (QueryNode child : parent.children()) {
			if (child instanceof EmptyTableNode e) {
				if (type.getName().equals(e.type().getQualifiedName())) {
					return e;
				}
				EmptyTableNode found = findEmbeddedChildRecursive(e, type);
				if (found != null) return found;
			}
		}
		return null;
	}
}
