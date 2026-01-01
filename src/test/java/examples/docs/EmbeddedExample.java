package examples.docs;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

/**
 * Example demonstrating embedded objects - mapping multiple columns to nested POJOs.
 */
public class EmbeddedExample {

    // tag::address[]
    // Value object (not an entity - no @Table)
    public static class Address {
        String street;
        String city;
        String zip;

        public String getStreet() { return street; }
        public String getCity() { return city; }
        public String getZip() { return zip; }
    }
    // end::address[]

    // tag::customer[]
    @Table("customer")
    public static class Customer {
        @Id Long id;
        String name;

        @Embedded(prefix = "ship_")
        Address shippingAddress;

        @Embedded(prefix = "bill_")
        Address billingAddress;

        public Long getId() { return id; }
        public String getName() { return name; }
        public Address getShippingAddress() { return shippingAddress; }
        public Address getBillingAddress() { return billingAddress; }
    }
    // end::customer[]

    public static void main(String[] args) {
        DataSource dataSource = createDatabase();
        createTable(dataSource);
        insertTestData(dataSource);

        // tag::query[]
        Customer customer = PojoQuery.build(Customer.class)
            .addWhere("customer.id = ?", 1L)
            .execute(dataSource)
            .stream().findFirst().orElse(null);

        if (customer != null) {
            System.out.println("Customer: " + customer.getName());
            System.out.println("Ship to: " + customer.getShippingAddress().getStreet() 
                + ", " + customer.getShippingAddress().getCity());
            System.out.println("Bill to: " + customer.getBillingAddress().getStreet() 
                + ", " + customer.getBillingAddress().getCity());
        }
        // end::query[]
    }

    // tag::schema[]
    private static void createTable(DataSource db) {
        DB.executeDDL(db, """
            CREATE TABLE customer (
                id BIGINT IDENTITY PRIMARY KEY,
                name VARCHAR(255),
                ship_street VARCHAR(255),
                ship_city VARCHAR(100),
                ship_zip VARCHAR(20),
                bill_street VARCHAR(255),
                bill_city VARCHAR(100),
                bill_zip VARCHAR(20)
            )
            """);
    }
    // end::schema[]

    private static void insertTestData(DataSource db) {
        DB.executeDDL(db, """
            INSERT INTO customer (name, ship_street, ship_city, ship_zip, bill_street, bill_city, bill_zip)
            VALUES ('Acme Corp', '123 Warehouse Ave', 'Seattle', '98101', '456 Finance Blvd', 'New York', '10001')
            """);
    }

    private static DataSource createDatabase() {
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:embedded_example");
        ds.setUser("SA");
        ds.setPassword("");
        DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.HSQLDB));
        return ds;
    }
}
