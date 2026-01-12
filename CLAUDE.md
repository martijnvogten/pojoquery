# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PojoQuery is a model-driven ORM library for Java where POJOs define the *shape of query results* rather than table structures. This avoids lazy loading, proxies, and session management. Type hierarchies must be cycle-free.

## Build Commands

```bash
# Build and test (uses HSQLDB in-memory database)
./mvnw clean install

# Run only unit tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=TestBasics

# Run a single test method
./mvnw test -Dtest=TestBasics#testSimpleQuery

# Integration tests with specific databases (requires Docker)
./mvnw verify -P postgres
./mvnw verify -P mysql
./mvnw verify -P all-databases

# Build without tests
./mvnw package -DskipTests
```

## Architecture

### Core Components

- **`PojoQuery<T>`** (`src/main/java/org/pojoquery/PojoQuery.java`): Main entry point. Fluent API for building queries and static CRUD methods.
- **`DB`**: Database utilities for executing queries and managing transactions.
- **`DbContext`**: Database dialect abstraction (quote styles, type mappings, auto-increment syntax).
- **`QueryBuilder`** / **`CustomizableQueryBuilder`**: Transforms POJO structure into SQL SELECT statements.

### Dialect Support

Three `DbContext` implementations in `src/main/java/org/pojoquery/dialects/`:
- `MysqlDbContext`: MySQL/MariaDB (backticks, AUTO_INCREMENT)
- `PostgresDbContext`: PostgreSQL (ANSI quotes, SERIAL)
- `HsqldbDbContext`: HSQLDB (ANSI quotes, IDENTITY)

### Key Annotations (`org.pojoquery.annotations`)

- `@Table`: Maps class to database table
- `@Id`: Primary key field
- `@Link`: Many-to-many relationships via junction table
- `@Join`: Custom join definitions
- `@Embedded`: Embedded objects (no separate table)
- `@Select`: Custom SQL expressions for computed fields
- `@Other`: Dynamic columns (JSON or prefixed fields)
- `@SubClasses`: Inheritance mapping (declares subclasses)
- `@DiscriminatorColumn`: Single table inheritance (all subclasses in one table with discriminator)

### Query Alias Syntax

Use curly braces `{alias}` in WHERE/ORDER BY clauses. PojoQuery resolves these to properly quoted identifiers:
```java
PojoQuery.build(Article.class)
    .addWhere("{article}.id = ?", 123L)
    .addOrderBy("{comments}.submitdate DESC")
```

## Testing

- **Unit tests** (`Test*.java`): Test SQL generation without database - use HSQLDB context
- **Integration tests** (`*IT.java`): Test actual database operations - use TestContainers for MySQL/PostgreSQL
- **Test utilities**: `TestUtils.norm()` normalizes SQL for comparison

## Inheritance Strategies

Two strategies for mapping class hierarchies:

**Table-per-subclass** (`@SubClasses` only): Each class has its own table, joined by ID.
```java
@Table("room") @SubClasses({BedRoom.class, Kitchen.class})
class Room { @Id Long id; Double area; }

@Table("bedroom")
class BedRoom extends Room { Integer numberOfBeds; }
```

**Single table inheritance** (`@SubClasses` + `@DiscriminatorColumn`): All classes in one table with a discriminator column.
```java
@Table("room") @DiscriminatorColumn @SubClasses({BedRoom.class, Kitchen.class})
class Room { @Id Long id; Double area; }

@Table("room")  // Same table
class BedRoom extends Room { Integer numberOfBeds; }
```

## Key Design Decisions

1. **Cycle-free hierarchies**: Enforced to prevent infinite SELECT generation
2. **Eager loading only**: All relationships loaded in single SELECT with JOINs
3. **Parameterized queries**: All user input goes through `SqlExpression` parameters
4. **DbContext for all SQL**: Never write dialect-specific SQL directly; use DbContext methods
