package org.pojoquery.integrationtest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Other;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.pipeline.AbstractQueryTree.CustomQueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.EmptyFieldNode;
import org.pojoquery.pipeline.AbstractQueryTree.QueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.AQTTransformer;
import org.pojoquery.pipeline.TransformPipeline;
import org.pojoquery.pipeline.TransformPipeline.RecursiveTransform;
import org.pojoquery.pipeline.Transforms;
import org.pojoquery.pipeline.Transforms.AddDeclaredFields;
import org.pojoquery.schema.SchemaGenerator;

public class OtherIT {

	@Table("room")
	@SubClasses({BedRoom.class})
	public static class Room {
		@Id
		Long id;
		
		@Other
		Map<String,Object> specs;
	}
	
	@Table("bedroom")
	public static class BedRoom extends Room {
		Integer numberOfBeds;
	}
	
	/**
	 * Custom transform that adds the 'area' column to Room queries and populates the @Other specs map.
	 */
	public static class AddAreaColumnTransform implements RecursiveTransform {
		@Override
		public QueryNode transform(QueryNode node) {
			return Transforms.transformChildren(
				node,
				child -> child instanceof EmptyFieldNode emptyFieldNode &&
					emptyFieldNode.field().hasAnnotation(Other.class),
				(TableNode tableNode, EmptyFieldNode child) -> {
					// Only add area column for the Room table (check table name, not alias)
					if ("room".equals(tableNode.tableInfo().tableName())) {
						return new CustomQueryNode() {
							@Override
							public void applyToSqlQuery(TableNode parentNode, AQTTransformer.PlainQueryBuilder sqlQuery) {
								sqlQuery.addField(new SqlExpression("{" + parentNode.alias() + ".area}"), parentNode.alias() + ".area");
							}

							@Override
							public void applyRowResultToEntity(TableNode parentNode, Object targetEntity, Map<String, Object> fullRow) {
								Object areaValue = fullRow.get(parentNode.alias() + ".area");
								if (areaValue != null && targetEntity instanceof Room room) {
									if (room.specs == null) {
										room.specs = new HashMap<>();
									}
									room.specs.put("area", areaValue);
								}
							}
						};
					}
					return child;
				});
		}
	}
	
	@Test
	public void testBasic() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, (Connection c) -> {
			// Insert room without specs (area is set via direct SQL)
			Room u = new Room();
			PojoQuery.insert(c, u);
			Assertions.assertNotNull(u.id, "Room id should be set after insert");
			
			// Set the area value directly in the database
			try (PreparedStatement ps = c.prepareStatement("UPDATE \"room\" SET \"area\" = ? WHERE \"id\" = ?")) {
				ps.setInt(1, 25);
				ps.setLong(2, u.id);
				ps.executeUpdate();
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
			
			PojoQuery<Room> build = PojoQuery.build(DbContext.getDefault(),
				TransformPipeline.defaultPipeline()
					.insertAfter(AddDeclaredFields.class, AddAreaColumnTransform.class),
				Room.class);
			Room loaded = build.findById(c, u.id).orElseThrow();
			Assertions.assertNotNull(loaded.specs);
			Assertions.assertEquals(25, loaded.specs.get("area"));
		});
	}
	
	@Test
	public void testInheritance() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, (Connection c) -> {
			// Insert bedroom without specs (area is set via direct SQL)
			BedRoom bedroom = new BedRoom();
			bedroom.numberOfBeds = 2;
			PojoQuery.insert(c, bedroom);
			
			Assertions.assertNotNull(bedroom.id, "BedRoom id should be set after insert");
			
			// Set the area value directly in the database
			try (PreparedStatement ps = c.prepareStatement("UPDATE \"room\" SET \"area\" = ? WHERE \"id\" = ?")) {
				ps.setInt(1, 25);
				ps.setLong(2, bedroom.id);
				ps.executeUpdate();
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
			
			PojoQuery<BedRoom> build = PojoQuery.build(DbContext.getDefault(),
				TransformPipeline.defaultPipeline()
					.insertAfter(AddDeclaredFields.class, AddAreaColumnTransform.class),
				BedRoom.class);
			Room loaded = build.findById(c, bedroom.id).orElseThrow();
			Assertions.assertNotNull(loaded.specs);
			Assertions.assertEquals(25, loaded.specs.get("area"));
		});
	}
	

	private static DataSource initDatabase() {
		DataSource db = TestDatabaseProvider.getDataSource();
		// BedRoom extends Room, so only pass BedRoom (Room table is created automatically)
		SchemaGenerator.createTables(db, BedRoom.class);
		// Add custom field 'area' as a column in the room table
		DB.executeDDL(db, "ALTER TABLE \"room\" ADD COLUMN \"area\" INT");
		return db;
	}

}
