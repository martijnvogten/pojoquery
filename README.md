PojoQuery
=========

[![Maven Central](https://img.shields.io/maven-central/v/org.pojoquery/pojoquery)](https://search.maven.org/artifact/org.pojoquery/pojoquery)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue)](https://openjdk.org/)

PojoQuery is a lightweight Java library for working with relational databases. Instead of writing SQL queries in plain text, PojoQuery uses Plain Old Java Objects (POJOs) to define the shape of your result set — including which tables to join and which columns to fetch.

## Why PojoQuery?

Unlike traditional ORMs where classes define how data is *stored*, PojoQuery uses classes to define what data you want to *retrieve*. This simple shift eliminates lazy loading, proxy objects, and session management complexity. Your POJO is essentially a type-safe SQL query.

This approach scales to rich domain models — PojoQuery has been used to port the [DDD Sample cargo tracking system](https://github.com/martijnvogten/dddsample-core), demonstrating it handles complex domain-driven designs without compromise.

See [this article](https://martijnvogten.github.io/2025/04/16/the-basic-mistake-all-orms-make-and-how-to-fix-it.html) for the full rationale behind this approach.

## Key Features

**Query Building**
- Type-safe database queries using POJOs with full IDE support
- Automatic SQL generation from POJO structure
- Convention-based relationship mapping (one-to-one, one-to-many, many-to-many)
- Custom joins via `@Link`, `@Join`, and `@JoinCondition`
- Fluent query API with `addWhere()`, `addOrderBy()`, `setLimit()`, `addJoin()`
- Alias resolution with curly braces `{table.field}` for readable conditions

**CRUD Operations**
- Insert, update, and delete with automatic multi-table inheritance handling
- `@NoUpdate` to exclude fields from updates
- Transaction support via `DB.runInTransaction()`

**Schema Generation**
- Generate `CREATE TABLE` statements from entity classes
- Generate `ALTER TABLE` migration statements for schema updates
- `@Column` for length, precision, scale, nullable, and unique constraints

**Advanced Mapping**
- Table-per-subclass inheritance (`@SubClasses`)
- Embedded objects (`@Embedded`) with optional column prefix
- Dynamic columns (`@Other`) for schemaless patterns
- Custom SQL expressions (`@Select`) and aggregation (`@GroupBy`)
- Large objects (`@Lob`) for CLOB/BLOB support

**Performance & Flexibility**
- Streaming API for large result sets without memory issues
- No lazy loading, no proxies—predictable SQL, predictable behavior
- Multi-dialect support (MySQL, PostgreSQL, HSQLDB)
- JPA annotation compatibility (`jakarta.persistence` and `javax.persistence`)

## Quick Example

Define POJOs that describe the data you want to retrieve. PojoQuery automatically generates the SQL with proper joins.

```java
@Table("order")
class Order {
    @Id Long id;
    LocalDate orderDate;
    Customer customer;           // Joins on customer_id → customer.id
    List<OrderLine> lines;       // Joins on order.id → order_line.order_id
}

@Table("customer")
class Customer {
    @Id Long id;
    String name;
    String email;
}

@Table("order_line")
class OrderLine {
    @Id Long id;
    Product product;             // Joins on product_id → product.id
    int quantity;
    BigDecimal unitPrice;
}

@Table("product")  
class Product {
    @Id Long id;
    String name;
    String sku;
}
```

Generate the database schema from your entities:

```java
SchemaGenerator.createTables(dataSource, Order.class, Customer.class, OrderLine.class, Product.class);
```

Query with a fluent API:

```java
List<Order> orders = PojoQuery.build(Order.class)
    .addWhere("{customer}.name LIKE ?", "%Acme%")
    .addWhere("{lines.product}.sku = ?", "WIDGET-42")
    .addOrderBy("{order}.orderDate DESC")
    .setLimit(10)
    .execute(dataSource);

// All data is eagerly loaded—no lazy loading surprises
for (Order order : orders) {
    System.out.println(order.customer.name + " ordered on " + order.orderDate);
    for (OrderLine line : order.lines) {
        System.out.println("  " + line.quantity + "x " + line.product.name);
    }
}
```

PojoQuery generates proper `CREATE TABLE` statements with foreign keys, and queries join all four tables automatically with proper quoting and aliases.

## Getting Started

**Requirements:** Java 17 or later.

Add PojoQuery to your project:

**Maven:**
```xml
<dependency>
    <groupId>org.pojoquery</groupId>
    <artifactId>pojoquery</artifactId>
    <version>4.1.0-BETA</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'org.pojoquery:pojoquery:4.1.0-BETA'
```

For more details, see the [PojoQuery docs](https://pojoquery.org).

## Building from Source

To build PojoQuery from the source code:

1.  **Prerequisites:** Ensure you have JDK 17 or later installed.
2.  **Clone the repository:** `git clone https://github.com/martijnvogten/pojoquery.git`
3.  **Navigate to the project directory:** `cd pojoquery`
4.  **Build with Maven Wrapper:**
    *   On Linux/macOS: `./mvnw clean install`
    *   On Windows: `mvnw.cmd clean install`

This will compile the code, run tests, and install the artifact into your local Maven repository.

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

## License

PojoQuery is released under the [MIT License](LICENSE).