package examples.docs;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Example demonstrating embedded objects - mapping multiple columns to nested POJOs.
 */
public class EmbeddedExample {

    // tag::address[]
    public static class Address {
        String street;
        String city;
        String zip;

        public Address() {}

        public Address(String street, String city, String zip) {
            this.street = street;
            this.city = city;
            this.zip = zip;
        }

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

        public Customer() {}

        public Customer(String name, Address shippingAddress, Address billingAddress) {
            this.name = name;
            this.shippingAddress = shippingAddress;
            this.billingAddress = billingAddress;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
        public Address getShippingAddress() { return shippingAddress; }
        public Address getBillingAddress() { return billingAddress; }
    }
    // end::customer[]

    public static void main(String[] args) {
        DataSource dataSource = createDatabase();
        SchemaGenerator.createTables(dataSource, Customer.class);
        insertTestData(dataSource);

        // tag::query[]
        Customer customer = PojoQuery.build(Customer.class)
            .addWhere("{customer}.id = ?", 1L)
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

    private static void insertTestData(DataSource db) {
        Customer customer = new Customer(
            "Acme Corp",
            new Address("123 Warehouse Ave", "Seattle", "98101"),
            new Address("456 Finance Blvd", "New York", "10001")
        );
        PojoQuery.insert(db, customer);
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
