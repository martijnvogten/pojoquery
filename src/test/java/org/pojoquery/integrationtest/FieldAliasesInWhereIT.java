package org.pojoquery.integrationtest;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.schema.SchemaGenerator;

public class FieldAliasesInWhereIT {

	@Table("room")
	public static class Room {
		@Id
		Long id;
		
		BigDecimal area;
		
		House house;
	}
	
	@Table("house")
	public static class House {
		@Id
		Long id;
		String address;
		
		Person owner;
	}
	
	@Table("person")
	public static class Person {
		@Id
		Long id;
		
		String name;
	}
	
	@Test
	public void testBasic() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, (Connection c) -> {
			Person john = new Person();
			john.name = "John Lennon";
			PojoQuery.insert(c, john);
			
			House h = new House();
			h.address = "Abbey Road 1";
			h.owner = john;
			PojoQuery.insert(c, h);
			
			Room room = new Room();
			room.area = new BigDecimal(25);
			room.house = h;
			PojoQuery.insert(c, room);
			Assertions.assertEquals((Long)1L, room.id);
			
			Room loaded = PojoQuery.build(Room.class).findById(c, room.id).orElseThrow();
			Assertions.assertNotNull(loaded.house);
			
			{
				List<Room> results = PojoQuery.build(Room.class)
					.addWhere(SqlExpression.sql("{house.owner}.name = ?", "John Lennon"))
					.execute(c);
				
				Assertions.assertEquals(1, results.size());
			}
			
			{
				List<Room> results = PojoQuery.build(Room.class)
						.addWhere(SqlExpression.sql("{house.owner.name} = ?", "John Lennon"))
						.execute(c);
				
				Assertions.assertEquals(1, results.size());
			}
		});
	}
	

	private static DataSource initDatabase() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, Room.class, House.class, Person.class);
		return db;
	}

}
