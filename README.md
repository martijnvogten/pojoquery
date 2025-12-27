PojoQuery
=========

PojoQuery is a lightweight utility for working with relational databases in Java. Instead of writing SQL queries in plain text, PojoQuery leverages Plain Old Java Objects (POJOs) to define the set of fields and tables (joins) to fetch.

## Key Features

* Type-safe database queries using POJOs
* Automatic SQL generation from POJO structure
* Support for complex joins and relationships via `@Link` and `@Join`
* Customizable through annotations (`@Table`, `@Id`, `@FieldName`, `@Select`, etc.)
* No lazy loading, no proxies, no session management complexity
* Easily adaptable to different database engines or SQL dialects (tested with MySQL, PostgreSQL, HSQL)
* Support for table-per-subclass inheritance mapping (`@SubClasses`)
* Support for embedded objects (`@Embedded`)
* Handling of dynamic columns (`@Other`)

## Rationale

The main difference with conventional Object Relational Mapping (ORM) is that types (Java classes) do not double as table definitions but rather as *query definitions*. More precisely, the POJO defines the shape of the resultset. This implies that type definitions must be *cycle-free*. The principle is the key to avoiding lazy loading and other complexities of conventional ORM. See [this article](https://martijnvogten.github.io/2025/04/16/the-basic-mistake-all-orms-make-and-how-to-fix-it.html) about model-driven ORM.

## Quick Example

Define your POJOs to represent the data structure you want to retrieve. Access modifiers and getters/setters are omitted for brevity.

```java
// Define the main entity, mapping to the 'article' table
@Table("article")
class Article {
    @Id Long id;
    String title;
    String content;
    User author; // Automatically joins with the 'user' table based on 'author_id'
    List<Comment> comments; // Automatically joins with the 'comment' table based on 'article_id'

    // Getters and setters...
}

// Define the related 'user' entity
@Table("user")
class User {
    @Id Long id;
    String firstName;
    String lastName;
    String email;

    // Getters and setters...
}

// Define the related 'comment' entity
@Table("comment")
class Comment {
    @Id Long id;
    Long article_id; // Foreign key linking back to Article
    String comment;
    Date submitdate;
    User author; // Automatically joins with the 'user' table based on 'author_id'

    // Getters and setters...
}
```

Build and execute the query using PojoQuery:

```java
// Assuming 'connection' is your active JDBC Connection (java.sql.Connection)
List<Article> articles = PojoQuery.build(Article.class) // Start building a query for Article
    .addWhere("article.id = ?", 123L) // Filter by article ID
    .addOrderBy("comments.submitdate DESC") // Order comments by submission date
    .execute(connection); // Execute the query against the database connection

// Process the results
for (Article article : articles) {
    System.out.println("Article Title: " + article.getTitle());
    System.out.println("Author: " + article.getAuthor().getFirstName());
    System.out.println("Number of comments: " + article.getComments().size());
}
```

This generates a predictable SQL query, joining `article`, `user` (for article author), `comment`, and `user` (for comment author) tables.

## Getting Started

To begin using PojoQuery in your project, see the Getting started section in the [PojoQuery docs](https://pojoquery.org).

## Building from Source

To build PojoQuery from the source code:

1.  **Prerequisites:** Ensure you have JDK 17 or later installed.
2.  **Clone the repository:** `git clone https://github.com/martijnvogten/pojoquery.git`
3.  **Navigate to the project directory:** `cd pojoquery`
4.  **Build with Maven Wrapper:**
    *   On Linux/macOS: `./mvnw clean install`
    *   On Windows: `mvnw.cmd clean install`

This will compile the code, run tests, and install the artifact into your local Maven repository.

