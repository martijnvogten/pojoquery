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
- Fluent query API with `addWhere()`, `addOrderBy()`, `setLimit()`, `addJoin()`, `addGroupBy()`
- Alias resolution with curly braces `{table.field}` for readable conditions
- `findById()` for convenient single-record lookups

**Type-Safe Query Generation** (compile-time)
- `@GenerateQuery` annotation generates type-safe query builders
- Compile-time field references with `where()` and `orderBy()` methods
- Fluent condition API: `eq()`, `ne()`, `gt()`, `lt()`, `between()`, `in()`, `like()`, `isNull()`, etc.
- Composable conditions with `and()`, `or()`, and `begin()...end()` grouping

**CRUD Operations**
- Insert, update, upsert, and delete with automatic multi-table inheritance handling
- `@NoUpdate` to exclude fields from updates
- Transaction support via `DB.runInTransaction()`
- Optimistic locking via `HasVersion` interface with `StaleObjectException`

**Schema Generation & Migration**
- Generate `CREATE TABLE` statements from entity classes with `SchemaGenerator.createTables()`
- Generate `ALTER TABLE` migration statements with `SchemaGenerator.generateMigrationStatements()`
- Automatic foreign key constraint generation
- `@Column` for length, precision, scale, nullable, and unique constraints

**Advanced Mapping**
- Table-per-subclass inheritance (`@SubClasses`)
- Single table inheritance (`@SubClasses` + `@DiscriminatorColumn`)
- Embedded objects (`@Embedded`) with optional column prefix
- Dynamic columns (`@Other`) for schemaless patterns
- Custom SQL expressions (`@Select`) and aggregation (`@GroupBy`)
- Large objects (`@Lob`) for CLOB/BLOB support
- Field name mapping (`@FieldName`) for column name customization
- `@Transient` to exclude fields from persistence

**Performance & Flexibility**
- Streaming API (`executeStreaming()`) for large result sets without memory issues
- No lazy loading, no proxies—predictable SQL, predictable behavior
- Multi-dialect support (MySQL, PostgreSQL, HSQLDB)
- JPA annotation compatibility (`jakarta.persistence` and `javax.persistence`)
- Custom type mapping via `FieldMapping` and `DbContextBuilder`

## Quick Example

Define POJOs that describe the data you want to retrieve. PojoQuery automatically generates the SQL with proper joins.

```java
@Table("order")
class Order {
    @Id Long id;
    LocalDate orderDate;
    Customer customer;           // Joins on customer_id → customer.id
}

class OrderDetail extends Order {
    List<OrderLine> lines;       // Put association in subclass to prevent cycles
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
    Order order;
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
SchemaGenerator.createTables(dataSource, OrderDetail.class, Customer.class, OrderLine.class, Product.class);
```
PojoQuery generates proper `CREATE TABLE` statements with foreign keys, and queries join all tables automatically with proper quoting and aliases.

Query with a fluent API:

```java
List<Order> orders = PojoQuery.build(OrderDetail.class)
    .addWhere("{customer}.name LIKE ?", "%Acme%")
    .addWhere("{lines.product}.sku = ?", "WIDGET-42")
    .addOrderBy("{order}.orderDate DESC")
    .setLimit(10)
    .execute(dataSource);

// All data is eagerly loaded—no lazy loading surprises
for (OrderDetail order : orders) {
    System.out.println(order.customer.name + " ordered on " + order.orderDate);
    for (OrderLine line : order.lines) {
        System.out.println("  " + line.quantity + "x " + line.product.name);
    }
}
```

Or use generated type-safe queries with `@GenerateQuery`:

```java
@Table("employee")
@GenerateQuery
public class Employee {
    @Id Long id;
    String lastName;
    BigDecimal salary;
    Department department;
}

// Type-safe queries with fluent API
EmployeeQuery q = new EmployeeQuery();

List<Employee> results = q
    .where().salary.gt(new BigDecimal("50000")).and().lastName.like("S%")
    .orderBy().salary.desc()
    .list(connection);
```

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

## Additional Features

### Schema Migration

Generate ALTER TABLE statements by comparing entity classes against the current database schema:

```java
SchemaInfo schemaInfo = SchemaInfo.fromDataSource(dataSource);
List<String> migrations = SchemaGenerator.generateMigrationStatements(schemaInfo, Employee.class);
for (String ddl : migrations) {
    DB.executeDDL(dataSource, ddl);
}
```

### Streaming Large Result Sets

Process results one at a time without loading everything into memory:

```java
PojoQuery.build(Order.class)
    .addOrderBy("{order}.id")
    .executeStreaming(dataSource, order -> {
        processOrder(order);  // Called as each Order completes
    });
```

### Transactions

```java
DB.runInTransaction(dataSource, connection -> {
    PojoQuery.insert(connection, newOrder);
    PojoQuery.update(connection, existingCustomer);
});
```

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