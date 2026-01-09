package examples.docs;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.schema.SchemaGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
            .addWhere("{customers}.id = ?", 1L)
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
        SchemaGenerator.createTables(db, Address.class, Customer.class);
    }

    private static void insertTestData(DataSource db) {
        Address address = new Address();
        address.street = "123 Main St";
        address.city = "Springfield";
        PojoQuery.insert(db, address);

        Customer customer = new Customer();
        customer.name = "John Doe";
        customer.email = "john@example.com";
        customer.biography = "A software developer.";
        customer.primaryAddress = address;
        PojoQuery.insert(db, customer);
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
