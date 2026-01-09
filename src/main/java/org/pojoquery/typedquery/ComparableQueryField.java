package org.pojoquery.typedquery;

/**
 * A QueryField for comparable types, enabling type-safe comparison operations.
 *
 * <p>This subclass of QueryField is generated for fields whose type implements
 * {@link Comparable}, allowing methods like {@code greaterThan()}, {@code lessThan()},
 * and {@code between()} to be used with compile-time type safety.
 *
 * @param <E> the entity type this field belongs to
 * @param <T> the Java type of the field (must be Comparable)
 */
public class ComparableQueryField<E, T extends Comparable<? super T>> extends QueryField<E, T> {

    /**
     * Creates a new ComparableQueryField.
     *
     * @param alias      the table alias (e.g., "employee")
     * @param columnName the database column name
     * @param fieldName  the Java field name
     * @param fieldType  the Java type of the field
     */
    public ComparableQueryField(String alias, String columnName, String fieldName, Class<T> fieldType) {
        super(alias, columnName, fieldName, fieldType);
    }
}
