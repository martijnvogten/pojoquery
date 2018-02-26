package nl.pojoquery.integrationtest;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.Assert;
import org.junit.Test;

import nl.pojoquery.DB;
import nl.pojoquery.PojoQuery;
import nl.pojoquery.SqlExpression;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Other;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.integrationtest.db.TestDatabase;

public class TestOther {

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
		build.addField(SqlExpression.sql("`room`.area"), "room.area");
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
		build.addField(SqlExpression.sql("`room`.area"), "bedroom.area");
		Room loaded = build.findById(db, bedroom.id);
		Assert.assertNotNull(loaded.specs);
		Assert.assertEquals(25, loaded.specs.get("area"));
	}
	

	private static DataSource initDatabase() {
		DataSource db = TestDatabase.dropAndRecreate();
		DB.executeDDL(db, "CREATE TABLE room (id BIGINT NOT NULL AUTO_INCREMENT, area INT, PRIMARY KEY (id))");
		DB.executeDDL(db, "CREATE TABLE bedroom (id BIGINT NOT NULL AUTO_INCREMENT, numberOfBeds INT, PRIMARY KEY (id))");
		return db;
	}

}
