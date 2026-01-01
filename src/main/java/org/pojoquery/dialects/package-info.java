/**
 * Database dialect implementations for different database systems.
 * 
 * <p>Each dialect handles database-specific SQL syntax, type mappings,
 * and identifier quoting. Use {@link org.pojoquery.DbContext#forDialect(org.pojoquery.DbContext.Dialect)}
 * to get a pre-configured context for your database.</p>
 * 
 * <h2>Supported Dialects</h2>
 * <ul>
 *   <li>{@link org.pojoquery.dialects.MysqlDbContext} - MySQL and MariaDB</li>
 *   <li>{@link org.pojoquery.dialects.PostgresDbContext} - PostgreSQL</li>
 *   <li>{@link org.pojoquery.dialects.HsqldbDbContext} - HSQLDB (for testing)</li>
 * </ul>
 * 
 * <h2>Creating Custom Dialects</h2>
 * <p>Extend an existing dialect to customize behavior:</p>
 * <pre>{@code
 * public class MyCustomContext extends PostgresDbContext {
 *     @Override
 *     public String mapJavaTypeToSql(Field field) {
 *         if (field.getType() == UUID.class) {
 *             return "UUID";
 *         }
 *         return super.mapJavaTypeToSql(field);
 *     }
 * }
 * }</pre>
 * 
 * @see org.pojoquery.DbContext
 * @see org.pojoquery.DbContextBuilder
 */
package org.pojoquery.dialects;
