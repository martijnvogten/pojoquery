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
* Support for inheritance mapping (`@SubClasses`)
* Support for embedded objects (`@Embedded`)
* Handling of dynamic columns (`@Other`)

## Quick Example

Define your POJOs.

```java
// Field access modifiers and getters/setters omitted for brevity

@Table("article")
class Article {
    @Id Long id;
    String title;
    String content;
    User author; // Automatically joins based on author_id
    List<Comment> comments; // Automatically joins based on article_id in comment table

	// ...
}

@Table("user")
class User {
    @Id Long id;
    String firstName;
    String lastName;
    String email;

	// ...
}

@Table("comment")
class Comment {
    @Id Long id;
    Long article_id; // Foreign key
    String comment;
    Date submitdate;
    User author; // Join based on author_id in comment table

	// ...
}
```

Build and execute the query:

```java
// Assuming 'connection' is the current JDBC Connection (javax.sql.Connection)
List<Article> articles = PojoQuery.build(Article.class)
    .addWhere("article.id = ?", 123L)
    .addOrderBy("comments.submitdate DESC")
    .execute(connection); // Execute against the current connection/transaction

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

