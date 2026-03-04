package org.pojoquery.pipeline.querytree;

import org.pojoquery.SqlExpression;

/**
 * Sealed interface representing different types of join conditions.
 * Each variant captures the structural relationship between tables,
 * making it unambiguous where foreign key columns should exist.
 */
public sealed interface JoinCondition {
    
    /**
     * Foreign key column is in the child (joined) table, pointing to parent's column.
     * Used for: one-to-many relationships, one-to-one with FK in child.
     * 
     * <p>Example: Comment has article_id pointing to Article.id
     * <pre>
     * SELECT * FROM article a
     * LEFT JOIN comment c ON a.id = c.article_id
     * </pre>
     * 
     * @param foreignKeyColumn The FK column name in the child table (e.g., "article_id")
     * @param referencedColumn The referenced column name in the parent table (e.g., "id")
     */
    record ForeignKeyInChild(
        String foreignKeyColumn,
        String referencedColumn
    ) implements JoinCondition {
        public ForeignKeyInChild {
            if (foreignKeyColumn == null || foreignKeyColumn.isEmpty()) {
                throw new IllegalArgumentException("foreignKeyColumn must not be null or empty");
            }
            if (referencedColumn == null || referencedColumn.isEmpty()) {
                throw new IllegalArgumentException("referencedColumn must not be null or empty");
            }
        }
    }
    
    /**
     * Foreign key column is in the parent table, pointing to child's column.
     * Used for: many-to-one / entity reference relationships.
     * 
     * <p>Example: Article has author_id pointing to Author.id
     * <pre>
     * SELECT * FROM article a
     * LEFT JOIN author au ON a.author_id = au.id
     * </pre>
     * 
     * @param foreignKeyColumn The FK column name in the parent table (e.g., "author_id")
     * @param referencedColumn The referenced column name in the child table (e.g., "id")
     */
    record ForeignKeyInParent(
        String foreignKeyColumn,
        String referencedColumn
    ) implements JoinCondition {
        public ForeignKeyInParent {
            if (foreignKeyColumn == null || foreignKeyColumn.isEmpty()) {
                throw new IllegalArgumentException("foreignKeyColumn must not be null or empty");
            }
            if (referencedColumn == null || referencedColumn.isEmpty()) {
                throw new IllegalArgumentException("referencedColumn must not be null or empty");
            }
        }
    }
    
    /**
     * Shared primary key relationship (table-per-class inheritance).
     * Both tables share the same primary key value.
     * 
     * <p>Example: BedRoom extends Room, joined on shared id
     * <pre>
     * SELECT * FROM bedroom b
     * LEFT JOIN room r ON b.id = r.id
     * </pre>
     * 
     * @param parentColumn The column name in the parent table (typically "id")
     * @param childColumn The column name in the child table (typically "id")
     */
    record SharedPrimaryKey(
        String parentColumn,
        String childColumn
    ) implements JoinCondition {
        public SharedPrimaryKey {
            if (parentColumn == null || parentColumn.isEmpty()) {
                throw new IllegalArgumentException("parentColumn must not be null or empty");
            }
            if (childColumn == null || childColumn.isEmpty()) {
                throw new IllegalArgumentException("childColumn must not be null or empty");
            }
        }
    }
    
    /**
     * Custom SQL join condition (escape hatch for complex joins).
     * Use this when the other variants don't capture the relationship.
     * 
     * @param condition The SQL expression for the join condition
     */
    record Custom(SqlExpression condition) implements JoinCondition {
        public Custom {
            if (condition == null) {
                throw new IllegalArgumentException("condition must not be null");
            }
        }
    }
    
    /**
     * Converts this join condition to a SQL expression.
     * 
     * @param parentAlias The alias for the parent (left) table in the join
     * @param childAlias The alias for the child (right) table in the join
     * @return The SQL join condition expression
     */
    default SqlExpression toSqlExpression(String parentAlias, String childAlias) {
        if (this instanceof ForeignKeyInChild fkChild) {
            return new SqlExpression(
                "{" + parentAlias + "." + fkChild.referencedColumn() + "} = {" + 
                childAlias + "." + fkChild.foreignKeyColumn() + "}"
            );
        } else if (this instanceof ForeignKeyInParent fkParent) {
            return new SqlExpression(
                "{" + parentAlias + "." + fkParent.foreignKeyColumn() + "} = {" + 
                childAlias + "." + fkParent.referencedColumn() + "}"
            );
        } else if (this instanceof SharedPrimaryKey shared) {
            // For inheritance joins, the convention is {child}.{id} = {parent}.{id}
            return new SqlExpression(
                "{" + childAlias + "." + shared.childColumn() + "} = {" + 
                parentAlias + "." + shared.parentColumn() + "}"
            );
        } else if (this instanceof Custom custom) {
            return custom.condition();
        } else {
            throw new IllegalStateException("Unknown JoinCondition type: " + this.getClass());
        }
    }
}
