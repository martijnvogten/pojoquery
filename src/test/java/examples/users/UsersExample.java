package examples.users;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.Set;
import java.util.Map;

import javax.sql.DataSource;

import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabase;
import org.pojoquery.schema.SchemaGenerator;


public class UsersExample {

	// tag::enum[]
	public enum Role {
		USER,
		ADMIN
	}
	// end::enum[]

	// tag::base-entity[]
	public static class Entity {
		@Id
		public Long id;
		public UserRef modifiedBy; 
		public UserRef createdBy; 
		public Date modificationDate;
		public Date creationDate;
	}
	// end::base-entity[]
	
	// tag::user-entity[]
	@Table("user")
	public static class User extends Entity {
		public String firstName;
		public String lastName;
		public String email;
		public String password;

		public UserRef getRef() {
			UserRef ref = new UserRef();
			ref.id = this.id;
			ref.firstName = this.firstName;
			ref.lastName = this.lastName;
			ref.email = this.email;
			return ref;
		}
	}
	
	@Table("user")
	public static class UserRef {
		@Id
		public Long id;
		public String firstName;
		public String lastName;
		public String email;
	}
	// end::user-entity[]

	// tag::role-entity[]
	@Table("user_role")
	public static class UserRole {
		@Id
		public Long user_id;
		@Id
		public Role role;
	}

	public static class UserWithRoles extends User {
		@Link(linktable = "user_role", fetchColumn = "role")
		public Set<Role> roles;
	}
	// end::role-entity[]
	
	public static void main(String[] args) throws SQLException {
		// Ensure HSQLDB context is set (may have been changed by other tests)
		TestDatabase.initDbContext();
		DataSource db = TestDatabase.dropAndRecreate();
		createTables(db);
		
		try (Connection con = db.getConnection()) {
			// tag::insert-users[]
			// Create regular user John
			User john = new User();
			john.firstName = "John";
			john.lastName = "Ewbank";
			john.email = "john@ewbank.nl";
			john.id = PojoQuery.insert(con, john);
			
			// Give John the USER role
			addRole(con, john.getRef(), Role.USER);

			// Create admin user Alice
			User alice = new User();
			alice.firstName = "Alice";
			alice.lastName = "Admin";
			alice.email = "alice@admin.com";
			alice.createdBy = john.getRef();
			alice.creationDate = new Date();
			alice.id = PojoQuery.insert(con, alice);

			// Give Alice both USER and ADMIN roles
			addRole(con, alice.getRef(), Role.USER);
			addRole(con, alice.getRef(), Role.ADMIN);
			// end::insert-users[]

			// tag::update-user[]
			// Alice modifies John's record
			john.modifiedBy = alice.getRef();
			john.modificationDate = new Date();
			PojoQuery.update(con, john);
			// end::update-user[]
			
			// tag::query-with-roles[]
			// Query all users with their roles
			PojoQuery<UserWithRoles> q = PojoQuery.build(UserWithRoles.class);
			System.out.println(q.toSql());
			
			for (UserWithRoles u : q.execute(con)) {
				System.out.println(u.firstName + " " + u.lastName + " - roles: " + u.roles);
				if (u.modifiedBy != null) {
					System.out.println("  Modified by: " + u.modifiedBy.email);
				}
				if (u.createdBy != null) {
					System.out.println("  Created by: " + u.createdBy.email);
				}
			}
			// end::query-with-roles[]
		}
	}

	private static void addRole(Connection con, UserRef user, Role role) {
		DB.insert(con, "user_role", Map.of("user_id", user.id, "role", role));
	}

	private static void createTables(DataSource db) {
		SchemaGenerator.createTables(db, User.class, UserRole.class);
	}
}
