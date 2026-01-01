package examples.docs;

import java.sql.Connection;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Example demonstrating basic CRUD operations: Insert, Update, Delete.
 */
public class BasicCrudExample {

    // tag::entity[]
    @Table("app_user")
    public static class User {
        @Id Long id;
        String firstName;
        String lastName;
        String email;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }
    // end::entity[]

    public static void main(String[] args) {
        DataSource dataSource = createDatabase();
        SchemaGenerator.createTables(dataSource, User.class);

        // tag::crud[]
        // Use runInTransaction to ensure all operations are atomic
        DB.runInTransaction(dataSource, (Connection c) -> {
            // --- Insert ---
            User newUser = new User();
            newUser.setFirstName("Jane");
            newUser.setLastName("Doe");
            newUser.setEmail("jane.doe@example.com");
            PojoQuery.insert(c, newUser);
            // newUser.getId() is now populated if auto-generated
            System.out.println("Inserted user with ID: " + newUser.getId());

            // --- Query ---
            User existingUser = PojoQuery.build(User.class)
                .addWhere("{app_user}.id = ?", newUser.getId())
                .execute(c)
                .stream().findFirst().orElse(null);

            // --- Update ---
            if (existingUser != null) {
                existingUser.setEmail("jane.d@example.com");
                int updatedRows = PojoQuery.update(c, existingUser);
                System.out.println("Updated rows: " + updatedRows);
            }

            // --- Delete ---
            // Delete by entity instance
            PojoQuery.delete(c, existingUser);
            
            // Or delete by ID directly
            // PojoQuery.deleteById(c, User.class, existingUser.getId());
        });
        // end::crud[]

        System.out.println("CRUD operations completed successfully");
    }

    private static DataSource createDatabase() {
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:crud_example");
        ds.setUser("SA");
        ds.setPassword("");
        DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.HSQLDB));
        return ds;
    }
}
