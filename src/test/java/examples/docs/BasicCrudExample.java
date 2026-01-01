package examples.docs;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

/**
 * Example demonstrating basic CRUD operations: Insert, Update, Delete.
 */
public class BasicCrudExample {

    // tag::entity[]
    @Table("user")
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
        createTable(dataSource);

        // tag::insert[]
        // --- Insert ---
        User newUser = new User();
        newUser.setFirstName("Jane");
        newUser.setLastName("Doe");
        newUser.setEmail("jane.doe@example.com");
        PojoQuery.insert(dataSource, newUser);
        // newUser.getId() is now populated if auto-generated
        System.out.println("Inserted user with ID: " + newUser.getId());
        // end::insert[]

        // tag::query[]
        // --- Query ---
        User existingUser = PojoQuery.build(User.class)
            .addWhere("user.id = ?", newUser.getId())
            .execute(dataSource)
            .stream().findFirst().orElse(null);
        // end::query[]

        // tag::update[]
        // --- Update ---
        if (existingUser != null) {
            existingUser.setEmail("jane.d@example.com");
            int updatedRows = PojoQuery.update(dataSource, existingUser);
            System.out.println("Updated rows: " + updatedRows);
        }
        // end::update[]

        // tag::delete[]
        // --- Delete ---
        // Delete by entity instance
        PojoQuery.delete(dataSource, existingUser);
        
        // Or delete by ID directly
        // PojoQuery.deleteById(dataSource, User.class, existingUser.getId());
        // end::delete[]

        System.out.println("CRUD operations completed successfully");
    }

    private static void createTable(DataSource db) {
        DB.executeDDL(db, """
            CREATE TABLE user (
                id BIGINT IDENTITY PRIMARY KEY,
                first_name VARCHAR(255),
                last_name VARCHAR(255),
                email VARCHAR(255)
            )
            """);
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
