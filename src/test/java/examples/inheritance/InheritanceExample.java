package examples.inheritance;

import java.math.BigDecimal;
import java.sql.Connection;
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
	
	// tag::entities[]
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
	// end::entities[]

	public static void main(String[] args) {
		DataSource db = TestDatabase.dropAndRecreate();
		createTables(db);
		
		DB.withConnection(db, (Connection c) -> {
			BedRoom br = insertData(c);

			// tag::query[]
			PojoQuery<Room> q = PojoQuery.build(Room.class).addWhere("{room}.area > ?", 40.0);
			System.out.println(q.toSql());
			
			List<Room> rooms = q.execute(c);
			for(Room r : rooms) {
				if (r instanceof BedRoom) {
					BedRoom bedroom = (BedRoom)r;
					System.out.println("Bedroom with " + bedroom.numberOfBeds + " beds.");
				}
			}
			// end::query[]
			
			// tag::find-by-id[]
			BedRoom foundBr = PojoQuery.build(BedRoom.class).findById(c, 1L);
			System.out.println("Bedroom with " + foundBr.numberOfBeds + " beds.");
			// end::find-by-id[]
			
			PojoQuery.update(c, br);
		});
	}

	private static BedRoom insertData(Connection c) {
		BedRoom br = new BedRoom();
		br.area = BigDecimal.valueOf(100L);
		br.numberOfBeds = 2;
		
		PojoQuery.insert(c, br);
		return br;
	}
	
	private static void createTables(DataSource db) {
		for (String ddl : SchemaGenerator.generateCreateTableStatements(Room.class)) {
			DB.executeDDL(db, ddl);
		}
	}
	
}
