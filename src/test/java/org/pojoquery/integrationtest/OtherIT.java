package org.pojoquery.integrationtest;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.Assert;
import org.junit.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Other;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabase;
import org.pojoquery.schema.SchemaGenerator;

public class OtherIT {

	@Table("room")
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
	
	
	@Test
	public void testBasic() {
		DataSource db = initDatabase();
		
		Room u = new Room();
		u.specs = new HashMap<String,Object>();
		u.specs.put("area", 25);
		PojoQuery.insert(db, u);
		Assert.assertEquals((Long)1L, u.id);
		
		PojoQuery<Room> build = PojoQuery.build(Room.class);
		build.addField(SqlExpression.sql("{room}.area"), "room.area");
		Room loaded = build.findById(db, u.id);
		Assert.assertNotNull(loaded.specs);
		Assert.assertEquals(25, loaded.specs.get("area"));
	}
	
	@Test
	public void testInheritance() {
		DataSource db = initDatabase();
		
		BedRoom bedroom = new BedRoom();
		bedroom.specs = new HashMap<String,Object>();
		bedroom.specs.put("area", 25);
		bedroom.numberOfBeds = 2;
		PojoQuery.insert(db, bedroom);
		
		Assert.assertEquals((Long)1L, bedroom.id);
		
		PojoQuery<BedRoom> build = PojoQuery.build(BedRoom.class);
		build.addField(SqlExpression.sql("{room}.area"), "bedroom.area");
		Room loaded = build.findById(db, bedroom.id);
		Assert.assertNotNull(loaded.specs);
		Assert.assertEquals(25, loaded.specs.get("area"));
	}
	

	private static DataSource initDatabase() {
		DataSource db = TestDatabase.dropAndRecreate();
		// BedRoom extends Room, so only pass BedRoom (Room table is created automatically)
		SchemaGenerator.createTables(db, BedRoom.class);
		// Add custom field 'area' as a column in the room table
		DB.executeDDL(db, "ALTER TABLE room ADD COLUMN area INT");
		return db;
	}

}
