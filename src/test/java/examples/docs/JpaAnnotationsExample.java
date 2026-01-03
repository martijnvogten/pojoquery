package examples.docs;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Example demonstrating JPA annotation compatibility with PojoQuery.
 * PojoQuery supports jakarta.persistence and javax.persistence annotations
 * as alternatives to its native annotations.
 */
public class JpaAnnotationsExample {

    // tag::jpa-entity[]
    @Table(name = "customers")
    public static class Customer {
        @Id
        Long id;

        @Column(name = "full_name", length = 100, nullable = false)
        String name;

        @Column(unique = true)
        String email;

        @Lob
        String biography;

        @Transient
        String temporaryData;

        @JoinColumn(name = "primary_address_id")
        Address primaryAddress;

        // Getters
        public Long getId() { return id; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getBiography() { return biography; }
        public Address getPrimaryAddress() { return primaryAddress; }
    }
    // end::jpa-entity[]

    @Table(name = "addresses")
    public static class Address {
        @Id
        Long id;

        String street;
        String city;

        public Long getId() { return id; }
        public String getStreet() { return street; }
        public String getCity() { return city; }
    }

    // tag::pojoquery-entity[]
    @org.pojoquery.annotations.Table("customers")
    public static class CustomerNative {
        @org.pojoquery.annotations.Id
        Long id;

        @org.pojoquery.annotations.FieldName("full_name")
        @org.pojoquery.annotations.Column(length = 100, nullable = false)
        String name;

        @org.pojoquery.annotations.Column(unique = true)
        String email;

        @org.pojoquery.annotations.Lob
        String biography;

        @org.pojoquery.annotations.Transient
        String temporaryData;

        @org.pojoquery.annotations.Link(linkfield = "primary_address_id")
        Address primaryAddress;

        // Getters
        public Long getId() { return id; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getBiography() { return biography; }
        public Address getPrimaryAddress() { return primaryAddress; }
    }
    // end::pojoquery-entity[]

    public static void main(String[] args) {
        DataSource dataSource = createDatabase();
        createTables(dataSource);
        insertTestData(dataSource);

        // Both JPA and PojoQuery annotated entities work identically
        Customer customer = PojoQuery.build(Customer.class)
            .addWhere("customers.id = ?", 1L)
            .execute(dataSource)
            .stream().findFirst().orElse(null);

        if (customer != null) {
            System.out.println("Customer: " + customer.getName());
            System.out.println("Email: " + customer.getEmail());
            if (customer.getPrimaryAddress() != null) {
                System.out.println("Address: " + customer.getPrimaryAddress().getStreet());
            }
        }
    }

    private static void createTables(DataSource db) {
        DB.executeDDL(db, """
            CREATE TABLE addresses (
                id BIGINT IDENTITY PRIMARY KEY,
                street VARCHAR(255),
                city VARCHAR(100)
            )
            """);
        DB.executeDDL(db, """
            CREATE TABLE customers (
                id BIGINT IDENTITY PRIMARY KEY,
                full_name VARCHAR(100) NOT NULL,
                email VARCHAR(255) UNIQUE,
                biography CLOB,
                primary_address_id BIGINT,
                FOREIGN KEY (primary_address_id) REFERENCES addresses(id)
            )
            """);
    }

    private static void insertTestData(DataSource db) {
        DB.executeDDL(db, """
            INSERT INTO addresses (street, city) VALUES ('123 Main St', 'Springfield')
            """);
        DB.executeDDL(db, """
            INSERT INTO customers (full_name, email, biography, primary_address_id)
            VALUES ('John Doe', 'john@example.com', 'A software developer.', 1)
            """);
    }

    private static DataSource createDatabase() {
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:jpa_example");
        ds.setUser("SA");
        ds.setPassword("");
        DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.HSQLDB));
        return ds;
    }
}
