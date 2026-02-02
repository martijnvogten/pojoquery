package org.pojoquery.annotations;

/**
 * Defines cascade operation types that can be applied to entity relationships.
 * 
 * <p>When an entity operation is performed, the specified cascade types determine
 * which operations should also be applied to related entities.</p>
 * 
 * <h2>Cascade Types</h2>
 * <ul>
 *   <li>{@link #PERSIST} - Cascade insert operations</li>
 *   <li>{@link #MERGE} - Cascade update operations (insert, update, or delete)</li>
 *   <li>{@link #REMOVE} - Cascade delete operations</li>
 *   <li>{@link #ALL} - Cascade all operations</li>
 * </ul>
 * 
 * <h2>Example</h2>
 * <pre>{@code
 * @Table("orders")
 * public class Order {
 *     @Id Long id;
 *     String orderNumber;
 *     
 *     @Cascade(CascadeType.ALL)
 *     List<LineItem> lineItems;
 * }
 * }</pre>
 * 
 * @see Cascade
 * @see org.pojoquery.CascadingUpdater
 */
public enum CascadeType {
    
    /**
     * Cascade insert operations to the target of the association.
     */
    PERSIST,
    
    /**
     * Cascade merge (update) operations to the target of the association.
     * This includes inserting new items, updating existing items, and
     * deleting removed items (orphan removal).
     */
    MERGE,
    
    /**
     * Cascade delete operations to the target of the association.
     */
    REMOVE,
    
    /**
     * Cascade all operations (PERSIST, MERGE, REMOVE) to the target.
     */
    ALL
}
