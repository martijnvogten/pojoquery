/**
 * Annotations for mapping Java classes to database tables and columns.
 * 
 * <h2>Entity Mapping</h2>
 * <ul>
 *   <li>{@link org.pojoquery.annotations.Table} - Maps a class to a database table</li>
 *   <li>{@link org.pojoquery.annotations.Id} - Marks the primary key field</li>
 *   <li>{@link org.pojoquery.annotations.FieldName} - Custom column name mapping</li>
 *   <li>{@link org.pojoquery.annotations.Column} - Column constraints (length, nullable, unique)</li>
 * </ul>
 * 
 * <h2>Computed Fields</h2>
 * <ul>
 *   <li>{@link org.pojoquery.annotations.Select} - Custom SQL expressions for computed columns</li>
 *   <li>{@link org.pojoquery.annotations.Transient} - Exclude field from queries</li>
 * </ul>
 * 
 * <h2>Relationships</h2>
 * <ul>
 *   <li>{@link org.pojoquery.annotations.Link} - Configure many-to-many relationships</li>
 *   <li>{@link org.pojoquery.annotations.JoinCondition} - Custom join conditions</li>
 *   <li>{@link org.pojoquery.annotations.Embedded} - Embed value objects</li>
 * </ul>
 * 
 * <h2>Inheritance</h2>
 * <ul>
 *   <li>{@link org.pojoquery.annotations.SubClasses} - Define table-per-subclass inheritance</li>
 * </ul>
 * 
 * <h2>Example</h2>
 * <pre>{@code
 * @Table("users")
 * public class User {
 *     @Id 
 *     Long id;
 *     
 *     @FieldName("first_name")
 *     @Column(length = 50, nullable = false)
 *     String firstName;
 *     
 *     @Select("CONCAT({this}.first_name, ' ', {this}.last_name)")
 *     String fullName;
 *     
 *     @Embedded(prefix = "address_")
 *     Address address;
 * }
 * }</pre>
 * 
 * @see org.pojoquery.PojoQuery
 */
package org.pojoquery.annotations;
