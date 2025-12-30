package examples.inheritance;

import java.math.BigDecimal;
import java.util.List;

import javax.sql.DataSource;

import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabase;
import org.pojoquery.schema.SchemaGenerator;

public class InheritanceExample {
	
	@Table("room")
	@SubClasses({BedRoom.class, Kitchen.class})
	static class Room {
		@Id
		Long room_id;
		BigDecimal area;
	}
	
	@Table("bedroom")
	static class BedRoom extends Room {
		Integer numberOfBeds;
	}
	
	@Table("kitchen")
	static class Kitchen extends Room {
		Boolean hasDishWasher;
	}

	public static void main(String[] args) {
		DataSource db = TestDatabase.dropAndRecreate();
		createTables(db);
		insertData(db);

		PojoQuery<Room> q = PojoQuery.build(Room.class).addWhere("{room}.area > ?", 40.0);
		System.out.println(q.toSql());
		
		List<Room> rooms = q.execute(db);
		for(Room r : rooms) {
			if (r instanceof BedRoom) {
				BedRoom bedroom = (BedRoom)r;
				System.out.println("Bedroom with " + bedroom.numberOfBeds + " beds.");
			}
		}
		
		BedRoom br = PojoQuery.build(BedRoom.class).findById(db, 1L);
		System.out.println("Bedroom with " + br.numberOfBeds + " beds.");
		
		PojoQuery.update(db, br);
	}

	private static BedRoom insertData(DataSource db) {
		BedRoom br = new BedRoom();
		br.area = BigDecimal.valueOf(100L);
		br.numberOfBeds = 2;
		
		PojoQuery.insert(db, br);
		return br;
	}
	
	private static void createTables(DataSource db) {
		for (String ddl : SchemaGenerator.generateCreateTableStatements(Room.class)) {
			DB.executeDDL(db, ddl);
		}
	}
	
}
