package org.pojoquery.integrationtest;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Integration tests for Table-Per-Subclass (TPS) inheritance with insert and update operations.
 * 
 * TPS inheritance uses separate tables for each class in the hierarchy, joined by primary key.
 * For example:
 *   - Vehicle table: id, brand
 *   - Car table: id (FK to Vehicle), numberOfDoors
 *   - Motorcycle table: id (FK to Vehicle), hasSidecar
 */
public class TPSInheritanceIT {

	@Table("vehicle")
	@SubClasses({Car.class, Motorcycle.class})
	public static class Vehicle {
		@Id
		Long id;
		String brand;
	}
	
	@Table("car")
	public static class Car extends Vehicle {
		Integer numberOfDoors;
	}
	
	@Table("motorcycle")
	public static class Motorcycle extends Vehicle {
		Boolean hasSidecar;
	}

	@Test
	public void testInsertBaseClass() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, (Connection c) -> {
			Vehicle v = new Vehicle();
			v.brand = "Generic";
			PojoQuery.insert(c, v);
			
			Assertions.assertNotNull(v.id, "Vehicle id should be set after insert");
			
			// Verify we can read it back
			Vehicle loaded = PojoQuery.build(Vehicle.class).findById(c, v.id).orElseThrow();
			Assertions.assertEquals("Generic", loaded.brand);
		});
	}
	
	@Test
	public void testInsertSubclass() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, (Connection c) -> {
			Car car = new Car();
			car.brand = "Toyota";
			car.numberOfDoors = 4;
			PojoQuery.insert(c, car);
			
			Assertions.assertNotNull(car.id, "Car id should be set after insert");
			
			// Verify we can read it back as Car
			Car loadedCar = PojoQuery.build(Car.class).findById(c, car.id).orElseThrow();
			Assertions.assertEquals("Toyota", loadedCar.brand);
			Assertions.assertEquals(4, loadedCar.numberOfDoors);
		});
	}
	
	@Test
	public void testInsertMultipleSubclassVariants() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, (Connection c) -> {
			Car car = new Car();
			car.brand = "Honda";
			car.numberOfDoors = 2;
			PojoQuery.insert(c, car);
			
			Motorcycle moto = new Motorcycle();
			moto.brand = "Harley";
			moto.hasSidecar = true;
			PojoQuery.insert(c, moto);
			
			Assertions.assertNotNull(car.id, "Car id should be set");
			Assertions.assertNotNull(moto.id, "Motorcycle id should be set");
			Assertions.assertNotEquals(car.id, moto.id, "IDs should be different");
			
			// Verify both can be loaded
			Car loadedCar = PojoQuery.build(Car.class).findById(c, car.id).orElseThrow();
			Assertions.assertEquals("Honda", loadedCar.brand);
			Assertions.assertEquals(2, loadedCar.numberOfDoors);
			
			Motorcycle loadedMoto = PojoQuery.build(Motorcycle.class).findById(c, moto.id).orElseThrow();
			Assertions.assertEquals("Harley", loadedMoto.brand);
			Assertions.assertEquals(true, loadedMoto.hasSidecar);
		});
	}
	
	@Test
	public void testUpdateSubclass() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, (Connection c) -> {
			// Insert
			Car car = new Car();
			car.brand = "Toyota";
			car.numberOfDoors = 4;
			PojoQuery.insert(c, car);
			
			Assertions.assertNotNull(car.id, "Car id should be set after insert");
			
			// Update
			car.brand = "Lexus";
			car.numberOfDoors = 2;
			int updated = PojoQuery.update(c, car);
			
			Assertions.assertEquals(1, updated, "Should update one row");
			
			// Verify changes persisted
			Car loadedCar = PojoQuery.build(Car.class).findById(c, car.id).orElseThrow();
			Assertions.assertEquals("Lexus", loadedCar.brand);
			Assertions.assertEquals(2, loadedCar.numberOfDoors);
		});
	}
	
	@Test
	public void testQueryBaseClassReturnsAllSubclasses() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, (Connection c) -> {
			Vehicle v = new Vehicle();
			v.brand = "Generic";
			PojoQuery.insert(c, v);
			
			Car car = new Car();
			car.brand = "BMW";
			car.numberOfDoors = 4;
			PojoQuery.insert(c, car);
			
			Motorcycle moto = new Motorcycle();
			moto.brand = "Ducati";
			moto.hasSidecar = false;
			PojoQuery.insert(c, moto);
			
			// Query for all vehicles
			List<Vehicle> vehicles = PojoQuery.build(Vehicle.class).execute(c);
			
			Assertions.assertEquals(3, vehicles.size(), "Should find 3 vehicles");
			
			// Verify polymorphic loading
			long carCount = vehicles.stream().filter(v2 -> v2 instanceof Car).count();
			long motoCount = vehicles.stream().filter(v2 -> v2 instanceof Motorcycle).count();
			long plainVehicleCount = vehicles.stream()
				.filter(v2 -> v2.getClass() == Vehicle.class).count();
			
			Assertions.assertEquals(1, carCount, "Should have 1 Car");
			Assertions.assertEquals(1, motoCount, "Should have 1 Motorcycle");
			Assertions.assertEquals(1, plainVehicleCount, "Should have 1 plain Vehicle");
		});
	}
	
	@Test
	public void testDeleteSubclass() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, (Connection c) -> {
			Car car = new Car();
			car.brand = "Toyota";
			car.numberOfDoors = 4;
			PojoQuery.insert(c, car);
			
			Assertions.assertNotNull(car.id, "Car id should be set");
			Long carId = car.id;
			
			// Delete the car
			PojoQuery.delete(c, car);
			
			// Verify it's gone
			Assertions.assertTrue(
				PojoQuery.build(Car.class).findById(c, carId).isEmpty(),
				"Car should no longer exist"
			);
		});
	}

	private static DataSource initDatabase() {
		DataSource db = TestDatabaseProvider.getDataSource();
		// Create tables for all classes - SchemaGenerator handles inheritance
		SchemaGenerator.createTables(db, Vehicle.class, Car.class, Motorcycle.class);
		return db;
	}
}
