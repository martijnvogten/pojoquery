package examples.users;

import java.util.Date;

import javax.sql.DataSource;

import nl.pojoquery.DB;
import nl.pojoquery.PojoQuery;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Table;


public class UsersExample {

	public static class Entity {
		@Id
		public Long id;
		public Long modifiedBy_id;
		public Long createdBy_id;
		public Date modificationDate;
		public Date creationDate;
	}
	
	@Table("user")
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
		public Long id;
		public String firstName;
		public String lastName;
		public String email;
	}
	
	public static void run(DataSource db) {
		DB.executeDDL(db, "DELETE FROM user");
		
		User john = new User();
		john.firstName = "John";
		john.lastName = "Ewbank";
		john.email = "john@ewbank.nl";
		john.id = PojoQuery.insertOrUpdate(db, john);
		
		john.modifiedBy_id = john.id;
		john.modificationDate = john.modificationDate;
		PojoQuery.insertOrUpdate(db, john);
		
		PojoQuery<UserWithAudit> q = PojoQuery.create(UserWithAudit.class);
		System.out.println(q.toSql());
		
		for(UserWithAudit u : q.execute(db)) {
			System.out.println(u.modifiedBy.email);
		}
	}
}
