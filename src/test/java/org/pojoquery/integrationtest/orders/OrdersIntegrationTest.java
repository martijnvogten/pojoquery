package org.pojoquery.integrationtest.orders;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.integrationtest.DbContextExtension;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.schema.SchemaGenerator;

@ExtendWith(DbContextExtension.class)
public class OrdersIntegrationTest {

	@Test
	public void testOrdersQueries() {
		DataSource dataSource = initOrdersDatabase();
		DB.withConnection(dataSource, (conn) -> {
			var q = new CustomerOrderWithLineItemsQuery();
			q.where().discount.gt(0)
				.and(q.lineItems.quantity.gt(10)
					.or().lineItems.vendorPart.price.lt(BigDecimal.valueOf(1.00)))
				.and().status.in(OrderStatus.PENDING, OrderStatus.CANCELLED)
				.orderBy().lastUpdate.desc()
				.list(conn).forEach(co -> {
					System.out.printf("Order ID: %d, Status: %s%n", co.getId(), co.getStatus());
					for (var li : co.getLineItems()) {
						System.out.printf("  Line Item ID: %d, Item ID: %d, Quantity: %d%n",
								li.getId(), li.getItemId(), li.getQuantity());
						VendorPart vp = li.getVendorPart();
						Part p = vp.getPart();
						Vendor v = vp.getVendor();
						System.out.printf("    Part: %s (%s), Vendor: %s, Price: %s%n",
								p.getPartNumber(), p.getDescription(), v.getName(), vp.getPrice());
					}
				});
		});
	}

	private void insertTestData(java.sql.Connection conn) {
		// Create vendors
		Vendor acme = new Vendor("Acme Corp", "123 Industrial Way, Detroit, MI 48201", "John Smith", "555-0100");
		PojoQuery.insert(conn, acme);

		Vendor techSupplies = new Vendor("Tech Supplies Inc", "456 Electronics Blvd, San Jose, CA 95110", "Jane Doe",
				"555-0200");
		PojoQuery.insert(conn, techSupplies);

		Vendor globalParts = new Vendor("Global Parts Ltd", "789 Commerce St, Chicago, IL 60601", "Bob Wilson",
				"555-0300");
		PojoQuery.insert(conn, globalParts);

		// Create parts
		Part bolt = new Part("BOLT-001", 1, "Standard hex bolt M8x20", LocalDate.of(2025, 1, 15));
		PojoQuery.insert(conn, bolt);

		Part nut = new Part("NUT-001", 2, "Hex nut M8", LocalDate.of(2025, 3, 10));
		PojoQuery.insert(conn, nut);

		Part washer = new Part("WASH-001", 1, "Flat washer M8", LocalDate.of(2024, 11, 5));
		PojoQuery.insert(conn, washer);

		Part circuitBoard = new Part("PCB-100", 3, "Main circuit board v3", LocalDate.of(2025, 6, 20));
		PojoQuery.insert(conn, circuitBoard);

		Part sensor = new Part("SENS-050", 1, "Temperature sensor module", LocalDate.of(2025, 2, 28));
		PojoQuery.insert(conn, sensor);

		// Create vendor parts (linking parts to vendors with prices)
		VendorPart vpBoltAcme = new VendorPart("Acme Hex Bolt M8x20", new BigDecimal("0.25"), bolt, acme);
		PojoQuery.insert(conn, vpBoltAcme);

		VendorPart vpNutAcme = new VendorPart("Acme Hex Nut M8", new BigDecimal("0.15"), nut, acme);
		PojoQuery.insert(conn, vpNutAcme);

		VendorPart vpWasherGlobal = new VendorPart("Global Flat Washer M8", new BigDecimal("0.08"), washer,
				globalParts);
		PojoQuery.insert(conn, vpWasherGlobal);

		VendorPart vpCircuitTech = new VendorPart("Tech Supplies PCB v3", new BigDecimal("45.99"), circuitBoard,
				techSupplies);
		PojoQuery.insert(conn, vpCircuitTech);

		VendorPart vpSensorTech = new VendorPart("Tech Supplies Temp Sensor", new BigDecimal("12.50"), sensor,
				techSupplies);
		PojoQuery.insert(conn, vpSensorTech);

		// Create customer orders
		CustomerOrder order1 = new CustomerOrder(OrderStatus.PENDING, 10);
		PojoQuery.insert(conn, order1);

		CustomerOrder order2 = new CustomerOrder(OrderStatus.SHIPPED, 0);
		PojoQuery.insert(conn, order2);

		CustomerOrder order3 = new CustomerOrder(OrderStatus.CANCELLED, 15);
		PojoQuery.insert(conn, order3);

		// Create line items for order 1 (hardware order)
		PojoQuery.insert(conn, new LineItem(order1, 1, 100, vpBoltAcme));
		PojoQuery.insert(conn, new LineItem(order1, 2, 100, vpNutAcme));
		PojoQuery.insert(conn, new LineItem(order1, 3, 200, vpWasherGlobal));

		// Create line items for order 2 (electronics order)
		PojoQuery.insert(conn, new LineItem(order2, 1, 5, vpCircuitTech));
		PojoQuery.insert(conn, new LineItem(order2, 2, 10, vpSensorTech));

		// Create line items for order 3 (mixed order)
		PojoQuery.insert(conn, new LineItem(order3, 1, 50, vpBoltAcme));
		PojoQuery.insert(conn, new LineItem(order3, 2, 2, vpCircuitTech));
		PojoQuery.insert(conn, new LineItem(order3, 3, 8, vpSensorTech));
	}

	private DataSource initOrdersDatabase() {
		DataSource dataSource = TestDatabaseProvider.getDataSource();

		SchemaGenerator.createTables(dataSource,
				CustomerOrder.class,
				CustomerOrderWithLineItems.class,
				LineItem.class,
				Part.class,
				PartRef.class,
				Vendor.class,
				VendorPart.class,
				VendorWithParts.class);

		DB.withConnection(dataSource, (conn) -> {
			insertTestData(conn);
		});
		return dataSource;
	}

}
