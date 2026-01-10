package org.pojoquery.typedquery;

/**
 * A QueryField for comparable types, enabling type-safe comparison operations.
 *
 * <p>This subclass of QueryField is generated for fields whose type implements
 * {@link Comparable}, allowing methods like {@code gt()}, {@code lt()},
 * and {@code between()} to be used with compile-time type safety.
 *
 * <p>Example:
 * <pre>{@code
 * Condition<Employee> filter = salary.gt(50000).and(salary.lt(100000));
 * // or using between:
 * Condition<Employee> filter = salary.between(50000, 100000);
 * }</pre>
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

    // === Comparison condition methods ===

    /**
     * Creates a greater-than condition: field &gt; value
     * @param value the value to compare
     * @return the condition
     */
    public Condition<E> gt(T value) {
        return new Condition<>(getQualifiedColumn() + " > ?", value);
    }

    /**
     * Creates a greater-than-or-equal condition: field &gt;= value
     * @param value the value to compare
     * @return the condition
     */
    public Condition<E> ge(T value) {
        return new Condition<>(getQualifiedColumn() + " >= ?", value);
    }

    /**
     * Creates a less-than condition: field &lt; value
     * @param value the value to compare
     * @return the condition
     */
    public Condition<E> lt(T value) {
        return new Condition<>(getQualifiedColumn() + " < ?", value);
    }

    /**
     * Creates a less-than-or-equal condition: field &lt;= value
     * @param value the value to compare
     * @return the condition
     */
    public Condition<E> le(T value) {
        return new Condition<>(getQualifiedColumn() + " <= ?", value);
    }

    /**
     * Creates a BETWEEN condition: field BETWEEN low AND high
     * @param low the lower bound
     * @param high the upper bound
     * @return the condition
     */
    public Condition<E> between(T low, T high) {
        return new Condition<>(getQualifiedColumn() + " BETWEEN ? AND ?", low, high);
    }

    /**
     * Creates a NOT BETWEEN condition: field NOT BETWEEN low AND high
     * @param low the lower bound
     * @param high the upper bound
     * @return the condition
     */
    public Condition<E> notBetween(T low, T high) {
        return new Condition<>(getQualifiedColumn() + " NOT BETWEEN ? AND ?", low, high);
    }
}
