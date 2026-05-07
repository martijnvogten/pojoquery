package org.pojoquery.pipeline.querytree.transforms;

/**
 * Helper methods for determining join aliases in the query tree.
 */
public final class AliasNaming {
    
    private AliasNaming() {}
    
    /**
     * Determines the alias for a child node based on parent alias and field name.
     * At root level, uses just the field name. Otherwise, uses "parent.field" format.
     * 
     * @param parentAlias The parent node's alias
     * @param rootAlias The root table alias
     * @param fieldName The field name creating the join
     * @return The child alias
     */
    public static String childAlias(String parentAlias, String rootAlias, String fieldName) {
        return parentAlias.equals(rootAlias) 
        ? fieldName 
        : parentAlias + "." + fieldName;
    }
    
    /**
     * Determines the alias for a child node based on whether the parent is the root.
     *
     * @param parentIsRoot whether the parent node is the root node
     * @param parentAlias the parent node's alias
     * @param fieldName the field name creating the join
     * @return the child alias
     */
    public static String childAlias(boolean parentIsRoot, String parentAlias, String fieldName) {
        return parentIsRoot ? fieldName : parentAlias + "." + fieldName;
    }
    
    /**
     * Determines the alias for a link table in a many-to-many relationship.
     * 
     * @param parentAlias The parent node's alias
     * @param fieldName The field name creating the join
     * @return The link table alias
     */
    public static String linkTableAlias(String parentAlias, String fieldName) {
        return parentAlias + "_" + fieldName;
    }
    
    /**
     * Determines the alias for a subclass table in table-per-subclass inheritance.
     * 
     * @param parentAlias The parent node's alias
     * @param subclassTableName The subclass table name
     * @return The subclass alias
     */
    public static String subclassAlias(String parentAlias, String subclassTableName) {
        return parentAlias + "." + subclassTableName;
    }
    
    /**
     * Determines the alias for a superclass table.
     * 
     * @param childAlias The child (most specific) table's alias
     * @param superTableName The superclass table name
     * @return The superclass alias
     */
    public static String superclassAlias(String childAlias, String superTableName) {
        return childAlias + "." + superTableName;
    }
    
    /**
     * Checks if the given alias is the root alias.
     *
     * @param alias the alias to check
     * @param rootAlias the root alias
     * @return {@code true} if the alias equals the root alias
     */
    public static boolean isRoot(String alias, String rootAlias) {
        return alias.equals(rootAlias);
    }
}
