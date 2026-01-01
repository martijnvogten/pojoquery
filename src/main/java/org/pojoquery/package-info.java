/**
 * Core PojoQuery API for building and executing type-safe SQL queries.
 * 
 * <p>PojoQuery uses POJOs (Plain Old Java Objects) to define query results,
 * automatically generating SQL with proper joins based on field relationships.
 * The result class defines <em>what you want to retrieve</em>, not how data is stored.</p>
 * 
 * <h2>Main Entry Points</h2>
 * <ul>
 *   <li>{@link org.pojoquery.PojoQuery} - Build and execute type-safe queries</li>
 *   <li>{@link org.pojoquery.DB} - Low-level database utilities for raw SQL</li>
 *   <li>{@link org.pojoquery.DbContext} - Database dialect configuration</li>
 *   <li>{@link org.pojoquery.SqlExpression} - Parameterized SQL fragments</li>
 * </ul>
 * 
 * <h2>Quick Start</h2>
 * <pre>{@code
 * // 1. Configure your database dialect (once at startup)
 * DbContext.setDefault(DbContext.forDialect(Dialect.MYSQL));
 * 
 * // 2. Define an entity class
 * @Table("users")
 * public class User {
 *     @Id Long id;
 *     String name;
 *     String email;
 * }
 * 
 * // 3. Query
 * List<User> users = PojoQuery.build(User.class)
 *     .addWhere("{users}.name LIKE ?", "%John%")
 *     .execute(dataSource);
 * 
 * // 4. Insert
 * User newUser = new User();
 * newUser.name = "Jane";
 * PojoQuery.insert(dataSource, newUser);
 * }</pre>
 * 
 * @see org.pojoquery.annotations
 * @see org.pojoquery.schema
 */
package org.pojoquery;
