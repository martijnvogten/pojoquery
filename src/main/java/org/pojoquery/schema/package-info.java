/**
 * Schema generation utilities for creating database tables from entity classes.
 * 
 * <p>The {@link org.pojoquery.schema.SchemaGenerator} can automatically generate
 * CREATE TABLE statements based on your annotated entity classes, making it easy
 * to keep your database schema in sync with your Java model.</p>
 * 
 * <h2>Basic Usage</h2>
 * <pre>{@code
 * // Generate CREATE TABLE SQL
 * List<String> statements = SchemaGenerator.generateCreateTableStatements(User.class);
 * 
 * // Or create tables directly
 * SchemaGenerator.createTables(dataSource, User.class, Order.class, Product.class);
 * }</pre>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li>Generates dialect-specific SQL based on {@link org.pojoquery.DbContext}</li>
 *   <li>Handles relationships and foreign keys</li>
 *   <li>Supports inheritance hierarchies</li>
 *   <li>Creates link tables for many-to-many relationships</li>
 * </ul>
 * 
 * @see org.pojoquery.schema.SchemaGenerator
 * @see org.pojoquery.DbContext
 */
package org.pojoquery.schema;
