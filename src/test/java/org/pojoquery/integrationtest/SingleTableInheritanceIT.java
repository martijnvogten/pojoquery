package org.pojoquery.integrationtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.DiscriminatorColumn;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Other;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.pipeline.AQTRowProcessor;
import org.pojoquery.pipeline.AQTTransformer;
import org.pojoquery.pipeline.AbstractQueryTree.CustomQueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.EmptyFieldNode;
import org.pojoquery.pipeline.AbstractQueryTree.QueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.DefaultSqlQuery;
import org.pojoquery.pipeline.SqlQuery;
import org.pojoquery.pipeline.TransformPipeline;
import org.pojoquery.pipeline.TransformPipeline.RecursiveTransform;
import org.pojoquery.pipeline.Transforms;
import org.pojoquery.pipeline.Transforms.AddDeclaredFields;
import org.pojoquery.schema.SchemaGenerator;
import org.pojoquery.typemodel.ReflectionTypeModel;

/**
 * Integration tests for Single Table Inheritance (STI) using @DiscriminatorColumn.
 * 
 * <p>Tests that:
 * <ul>
 *   <li>All subclasses are stored in a single table with a discriminator column</li>
 *   <li>Querying returns the correct subclass type based on discriminator value</li>
 *   <li>Custom columns not mapped to fields appear in @Other maps</li>
 *   <li>Discriminator column does NOT appear in @Other maps</li>
 * </ul>
 */
public class SingleTableInheritanceIT {

	// ========== Entity Classes ==========
	
	@Table("vehicle")
	@DiscriminatorColumn(name = "vehicle_type")
	@SubClasses({Car.class, Motorcycle.class})
	public static class Vehicle {
		@Id
		Long id;
		
		String brand;
		int year;
		
		@Other
		Map<String, Object> extras;
	}
	
	public static class Car extends Vehicle {
		Integer numberOfDoors;
		Boolean hasAirConditioning;
	}
	
	public static class Motorcycle extends Vehicle {
		Integer engineCC;
		Boolean hasSidecar;
	}
	
	// ========== Tests ==========
	
	@Test
	public void testInsertAndQuerySubclasses() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, (Connection c) -> {
			// Insert a Car (using DB.insert with explicit discriminator value)
			DB.insert(c, "vehicle", Map.of(
				"brand", "Toyota",
				"year", 2024,
				"vehicle_type", "Car",
				"numberOfDoors", 4,
				"hasAirConditioning", true
			));
			
			// Insert a Motorcycle
			DB.insert(c, "vehicle", Map.of(
				"brand", "Honda",
				"year", 2023,
				"vehicle_type", "Motorcycle",
				"engineCC", 650,
				"hasSidecar", false
			));
			
			// Insert a base Vehicle
			DB.insert(c, "vehicle", Map.of(
				"brand", "Generic",
				"year", 2022,
				"vehicle_type", "Vehicle"
			));
			
			// Query all vehicles - should return correct subclass types
			List<Vehicle> vehicles = PojoQuery.build(Vehicle.class)
				.addOrderBy("{vehicle.id}")
				.execute(c);
			
			assertEquals(3, vehicles.size());
			
			// First should be Car
			assertTrue(vehicles.get(0) instanceof Car, "First vehicle should be Car");
			Car loadedCar = (Car) vehicles.get(0);
			assertEquals("Toyota", loadedCar.brand);
			assertEquals(2024, loadedCar.year);
			assertEquals((Integer) 4, loadedCar.numberOfDoors);
			assertEquals(Boolean.TRUE, loadedCar.hasAirConditioning);
			
			// Second should be Motorcycle
			assertTrue(vehicles.get(1) instanceof Motorcycle, "Second vehicle should be Motorcycle");
			Motorcycle loadedMoto = (Motorcycle) vehicles.get(1);
			assertEquals("Honda", loadedMoto.brand);
			assertEquals(2023, loadedMoto.year);
			assertEquals((Integer) 650, loadedMoto.engineCC);
			assertEquals(Boolean.FALSE, loadedMoto.hasSidecar);
			
			// Third should be base Vehicle
			assertEquals(Vehicle.class, vehicles.get(2).getClass(), "Third should be base Vehicle");
			assertEquals("Generic", vehicles.get(2).brand);
		});
	}
	
	@Test
	public void testCustomColumnInOtherMap() {
		DataSource db = initDatabase();
		
		// Add a custom column 'mileage' that is not mapped to any field
		DB.executeDDL(db, """
			ALTER TABLE "vehicle" ADD COLUMN "mileage" INT
			""");
		
		DB.withConnection(db, (Connection c) -> {
			// Insert a car with mileage
			DB.insert(c, "vehicle", Map.of(
				"brand", "Ford",
				"year", 2020,
				"vehicle_type", "Car",
				"numberOfDoors", 2,
				"hasAirConditioning", true,
				"mileage", 50000
			));
			
			// Insert a motorcycle with mileage
			DB.insert(c, "vehicle", Map.of(
				"brand", "Yamaha",
				"year", 2021,
				"vehicle_type", "Motorcycle",
				"engineCC", 1000,
				"hasSidecar", false,
				"mileage", 25000
			));
			
			// Query with the custom mileage column included
			TransformPipeline pipeline = TransformPipeline.defaultPipeline()
				.insertAfter(AddDeclaredFields.class, MileageFields.class);
			RootNode root = AQTTransformer.buildQueryTreeForType(new ReflectionTypeModel(Vehicle.class), pipeline);

			SqlQuery<?> query = new DefaultSqlQuery(DbContext.getDefault());
			AQTTransformer.toSql(root, query);

			query.addOrderBy("{vehicle.id}");
			AQTTransformer.buildQueryTreeForType(new ReflectionTypeModel(Vehicle.class), pipeline);

			try {
				List<Vehicle> vehicles = AQTRowProcessor.processRows(root, DB.queryRows(c, query.toStatement()));
				
				assertEquals(2, vehicles.size());
				
				// Car should have mileage in @Other map
				Car car = (Car) vehicles.get(0);
				assertNotNull(car.extras, "Car should have extras map");
				assertEquals(50000, car.extras.get("mileage"), "Car mileage should be in extras");
				
				// Motorcycle should have mileage in @Other map
				Motorcycle moto = (Motorcycle) vehicles.get(1);
				assertNotNull(moto.extras, "Motorcycle should have extras map");
				assertEquals(25000, moto.extras.get("mileage"), "Motorcycle mileage should be in extras");
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}
	
	@Test
	public void testDiscriminatorColumnNotInOtherMap() {
		DataSource db = initDatabase();
		
		// Add a custom column 'color' that is not mapped to any field
		DB.executeDDL(db, """
			ALTER TABLE "vehicle" ADD COLUMN "color" VARCHAR(50)
			""");
		
		DB.withConnection(db, (Connection c) -> {
			// Insert a car with color
			DB.insert(c, "vehicle", Map.of(
				"brand", "BMW",
				"year", 2024,
				"vehicle_type", "Car",
				"numberOfDoors", 4,
				"color", "Red"
			));
			
			// Query with both the discriminator column and color column in results
			PojoQuery<Vehicle> query = PojoQuery.build(
				DbContext.getDefault(), 
				TransformPipeline.defaultPipeline().insertAfter(AddDeclaredFields.class, ColorFields.class),
				Vehicle.class);
			List<Vehicle> vehicles = query.execute(c);
			
			assertEquals(1, vehicles.size());
			Car car = (Car) vehicles.get(0);
			
			assertNotNull(car.extras, "Car should have extras map");
			
			// Custom column 'color' SHOULD be in extras
			assertEquals("Red", car.extras.get("color"), "Color should be in extras map");
			
			// Discriminator column 'vehicle_type' should NOT be in extras
			assertFalse(car.extras.containsKey("vehicle_type"), 
				"Discriminator column 'vehicle_type' should NOT be in extras map");
		});
	}
	
	@Test
	public void testBaseVehicleWithOtherExtras() {
		DataSource db = initDatabase();
		
		// Add a custom column 'notes' 
		DB.executeDDL(db, """
			ALTER TABLE "vehicle" ADD COLUMN "notes" VARCHAR(255)
			""");
		
		DB.withConnection(db, (Connection c) -> {
			// Insert a base vehicle (not a subclass) with notes
			DB.insert(c, "vehicle", Map.of(
				"brand", "Unknown",
				"year", 2000,
				"vehicle_type", "Vehicle",
				"notes", "Classic collectible"
			));
			
			// Query with notes column included
			PojoQuery<Vehicle> query = PojoQuery.build(DbContext.getDefault(), TransformPipeline.defaultPipeline().insertAfter(AddDeclaredFields.class, NotesFields.class), Vehicle.class);
			List<Vehicle> vehicles = query.execute(c);
			
			assertEquals(1, vehicles.size());
			
			// Should be base Vehicle class (not subclass)
			assertEquals(Vehicle.class, vehicles.get(0).getClass());
			
			Vehicle vehicle = vehicles.get(0);
			assertNotNull(vehicle.extras, "Vehicle should have extras map");
			assertEquals("Classic collectible", vehicle.extras.get("notes"), "Notes should be in extras");
			
			// Discriminator should NOT be in extras
			assertFalse(vehicle.extras.containsKey("vehicle_type"),
				"Discriminator column should NOT be in extras map for base class either");
		});
	}
	
	@Test  
	public void testFindByIdReturnsCorrectSubclass() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, (Connection c) -> {
			// Insert a Car with explicit discriminator
			Long carId = DB.insert(c, "vehicle", Map.of(
				"brand", "Audi",
				"year", 2025,
				"vehicle_type", "Car",
				"numberOfDoors", 4
			));
			
			// Insert a Motorcycle with explicit discriminator
			Long motoId = DB.insert(c, "vehicle", Map.of(
				"brand", "Ducati",
				"year", 2025,
				"vehicle_type", "Motorcycle",
				"engineCC", 1200
			));

			assertNotNull(carId);
			
			// findById should return the correct subclass type
			Vehicle foundCar = PojoQuery.build(Vehicle.class).findById(c, carId).orElseThrow();
			assertTrue(foundCar instanceof Car, "findById should return Car instance");
			assertEquals("Audi", foundCar.brand);
			
			Vehicle foundMoto = PojoQuery.build(Vehicle.class).findById(c, motoId).orElseThrow();
			assertTrue(foundMoto instanceof Motorcycle, "findById should return Motorcycle instance");
			assertEquals("Ducati", foundMoto.brand);
		});
	}
	
	// ========== Helper Methods ==========
	
	private static DataSource initDatabase() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, Vehicle.class);
		return db;
	}

	public static class MileageFields extends MyCustomTransform {
		public MileageFields() {
			super(List.of("mileage"));
		}
	}

	public static class ColorFields extends MyCustomTransform {
		public ColorFields() {
			super(List.of("color"));
		}
	}

	public static class NotesFields extends MyCustomTransform {
		public NotesFields() {
			super(List.of("notes"));
		}
	}

	public abstract static class MyCustomTransform implements RecursiveTransform {

		private final List<String> extraFields;

		public MyCustomTransform(List<String> extraFields) {
			this.extraFields = extraFields;
		}	

		@Override
		public QueryNode transform(QueryNode node) {
			return Transforms.transformChildren(
				node, 
				child -> child instanceof EmptyFieldNode emptyFieldNode && emptyFieldNode.field().hasAnnotation(Other.class), 
				(TableNode tableNode, EmptyFieldNode child) -> {
					if (tableNode.type().isSameType(Vehicle.class)) {
						CustomQueryNode customNode = new CustomQueryNode() {
							@Override
							public void applyToSqlQuery(TableNode parentNode, SqlQuery<?> sqlQuery) {
								for (String extraField : extraFields) {
									sqlQuery.addField(new SqlExpression("{" + parentNode.alias() + "." + extraField + "}"), parentNode.alias() + "." + extraField);
								}
							}

							@Override
							public void applyRowResultToEntity(TableNode parentNode, Object targetEntity, Map<String, Object> fullRow) {
								Map<String, Object> extras = extraFields.stream()
									.collect(Collectors.toMap(
										field -> field,
										field -> fullRow.get(parentNode.alias() + "." + field)
									));
								AQTRowProcessor.setFieldValue(targetEntity, child.field(), extras);
							}
						};
						return customNode;
					}
					return child;
				});
			}
	}
	

}
