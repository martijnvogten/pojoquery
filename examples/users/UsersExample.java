package users;

import java.util.Date;

import javax.sql.DataSource;

import nl.pojoquery.DB;
import nl.pojoquery.PojoQuery;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.integrationtest.db.TestDatabase;


public class UsersExample {

	public static class Entity {
		@Id
		public Long id;
		public Long modifiedBy_id;
		public Long createdBy_id;
		public Date modificationDate;
		public Date creationDate;
	}
	
	public static class UserWithAudit extends User {
		public UserRef modifiedBy; 
		public UserRef createdBy; 
	}
	
	@Table("user")
	public static class User extends Entity {
		public String firstName;
		public String lastName;
		public String email;
		public String password;
	}
	
	@Table("user")
	public static class UserRef {
		@Id
		public Long id;
		public String firstName;
		public String lastName;
		public String email;
	}
	
	public static void main(String[] args) {
		DataSource db = TestDatabase.dropAndRecreate();
		createTables(db);
		
		User john = new User();
		john.firstName = "John";
		john.lastName = "Ewbank";
		john.email = "john@ewbank.nl";
		john.id = PojoQuery.insert(db, john);
		
		john.modifiedBy_id = john.id;
		john.modificationDate = john.modificationDate;
		PojoQuery.update(db, john);
		
		PojoQuery<UserWithAudit> q = PojoQuery.build(UserWithAudit.class);
		System.out.println(q.toSql());
		
		for(UserWithAudit u : q.execute(db)) {
			System.out.println(u.modifiedBy.email);
		}
	}

	private static void createTables(DataSource db) {
		DB.executeDDL(db, "CREATE TABLE `user` (\n" + 
				"  `id` bigint(20) NOT NULL AUTO_INCREMENT,\n" + 
				"  `modifiedBy_id` bigint(20) DEFAULT NULL,\n" + 
				"  `createdBy_id` bigint(20) DEFAULT NULL,\n" + 
				"  `firstName` varchar(255) DEFAULT NULL,\n" + 
				"  `lastName` varchar(255) DEFAULT NULL,\n" + 
				"  `password` varchar(255) DEFAULT NULL,\n" + 
				"  `email` varchar(255) DEFAULT NULL,\n" + 
				"  `modificationDate` datetime DEFAULT NULL,\n" + 
				"  `creationDate` datetime DEFAULT NULL,\n" + 
				"  PRIMARY KEY (`id`)\n" + 
				")");
	}
}
