package users;

import java.util.Date;

import javax.sql.DataSource;

import system.db.DB;
import system.sql.Query;
import system.sql.annotations.Id;
import system.sql.annotations.Table;

public class Main {

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
	
	public static void main(String[] args) {
		DataSource db = DB.getDataSource("jdbc:mysql://localhost/users", "root", "");
		DB.executeDDL(db, "DELETE FROM user");
		
		User john = new User();
		john.firstName = "John";
		john.lastName = "Ewbank";
		john.email = "john@ewbank.nl";
		john.id = Query.insertOrUpdate(db, john);
		
		john.modifiedBy_id = john.id;
		john.modificationDate = john.modificationDate;
		Query.insertOrUpdate(db, john);
		
		Query<UserWithAudit> q = Query.buildQuery(UserWithAudit.class);
		System.out.println(q.toSql());
		
		for(UserWithAudit u : q.execute(db)) {
			System.out.println(u.modifiedBy.email);
		}
	}
}
