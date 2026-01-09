package examples.docs;

import java.sql.Connection;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.pojoquery.DB;
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

        public Customer() {}

        public Customer(String name, String email, String biography) {
            this.name = name;
            this.email = email;
            this.biography = biography;
        }

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

        public Address() {}

        public Address(String street, String city) {
            this.street = street;
            this.city = city;
        }

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
        SchemaGenerator.createTables(dataSource, Address.class, Customer.class);

        DB.withConnection(dataSource, (Connection c) -> {
            // Insert test data
            Address address = new Address("123 Main St", "Springfield");
            PojoQuery.insert(c, address);

            Customer customer = new Customer("John Doe", "john@example.com", "A software developer.");
            customer.primaryAddress = address;
            PojoQuery.insert(c, customer);

            // Both JPA and PojoQuery annotated entities work identically
            Customer loaded = PojoQuery.build(Customer.class)
                .addWhere("{customers}.id = ?", customer.id)
                .execute(c)
                .stream().findFirst().orElse(null);

            if (loaded != null) {
                System.out.println("Customer: " + loaded.getName());
                System.out.println("Email: " + loaded.getEmail());
                if (loaded.getPrimaryAddress() != null) {
                    System.out.println("Address: " + loaded.getPrimaryAddress().getStreet());
                }
            }
        });
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
