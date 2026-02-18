package org.pojoquery.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a collection field for cascading operations.
 * 
 * <p>When present on a collection field, the {@link org.pojoquery.CascadingUpdater}
 * will automatically cascade insert, update, and delete operations to the
 * target entities.</p>
 * 
 * <p>The {@code CascadingUpdater.update()} method will:</p>
 * <ul>
 *   <li>Insert new items (items with null or zero ID)</li>
 *   <li>Update existing items (items with non-null ID that exist in database)</li>
 *   <li>Delete orphaned items (items in database but not in the collection)</li>
 * </ul>
 * 
 * <h2>Usage with Collections</h2>
 * <pre>{@code
 * @Table("orders")
 * public class Order {
 *     @Id Long id;
 *     String orderNumber;
 *     
 *     @Cascade
 *     List<LineItem> lineItems;
 * }
 * 
 * @Table("line_item")
 * public class LineItem {
 *     @Id Long id;
 *     Long order_id;  // Foreign key to Order
 *     String productName;
 *     Integer quantity;
 * }
 * 
 * // Usage:
 * Order order = loadOrder(orderId);
 * order.lineItems.add(newLineItem);
 * order.lineItems.remove(0);  // This item will be deleted
 * 
 * CascadingUpdater.update(connection, order);
 * }</pre>
 * 
 * <h2>Orphan Removal</h2>
 * <p>Items that exist in the database but are not present in the collection
 * will be deleted. This is similar to JPA's {@code orphanRemoval = true} behavior.</p>
 * 
 * @see org.pojoquery.CascadingUpdater
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Cascade {
}
