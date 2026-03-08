package org.pojoquery.schema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.annotations.Column;
import org.pojoquery.annotations.Link;
import org.pojoquery.pipeline.querytree.EmbeddedNode;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.FieldSelectionBase;
import org.pojoquery.pipeline.querytree.JoinCondition;
import org.pojoquery.pipeline.querytree.JoinInfo;
import org.pojoquery.pipeline.querytree.JoinTableInfo;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.QueryTreeBuilder;
import org.pojoquery.pipeline.querytree.TableInfo;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates CREATE TABLE statements based on entity classes using QueryTree.
 * 
 * <p>This implementation traverses the QueryTree structure instead of using
 * PojoMetadata or AnnotationHelper directly. All information is extracted
 * from the tree nodes (JoinedNode, EmbeddedNode, FieldSelection, JoinInfo).</p>
 * 
 * <p>Example usage:</p>
 * <pre>
 * String createTableSql = SchemaGenerator.generateCreateTable(MyEntity.class);
 * </pre>
 */
public class SchemaGenerator {
    
    private static final Logger LOG = LoggerFactory.getLogger(SchemaGenerator.class);

    /**
     * Generates a list of CREATE TABLE statements for the given entity class using default DbContext.
     */
    public static List<String> generateCreateTableStatements(Class<?> entityClass) {
        return generateCreateTableStatements(entityClass, DbContext.getDefault());
    }
    
    /**
     * Generates a list of CREATE TABLE statements for the given entity class with custom DbContext.
     */
    public static List<String> generateCreateTableStatements(Class<?> entityClass, DbContext dbContext) {
        return generateCreateTableStatements(new ReflectionTypeModel(entityClass), dbContext);
    }
    
    /**
     * Generates a list of CREATE TABLE statements for the given entity type with custom DbContext.
     */
    public static List<String> generateCreateTableStatements(TypeModel entityType, DbContext dbContext) {
        LOG.debug("Generating CREATE TABLE statements for {}", entityType.getSimpleName());
        
        // Build the QueryTree
        QueryTree tree = QueryTreeBuilder.from(entityType);
        
        // Collect schema info from tree
        SchemaCollector collector = new SchemaCollector(dbContext);
        collector.collectFromTree(tree);
        
        return collector.generateStatements();
    }
    
    /**
     * Generates a list of CREATE TABLE statements for multiple entity classes.
     */
    public static List<String> generateCreateTableStatements(Class<?>... entityClasses) {
        return generateCreateTableStatements(DbContext.getDefault(), entityClasses);
    }
    
    /**
     * Generates a list of CREATE TABLE statements for multiple entity classes with custom DbContext.
     */
    public static List<String> generateCreateTableStatements(DbContext dbContext, Class<?>... entityClasses) {
        SchemaCollector collector = new SchemaCollector(dbContext);
        
        for (Class<?> entityClass : entityClasses) {
            QueryTree tree = QueryTreeBuilder.from(entityClass);
            collector.collectFromTree(tree);
        }
        
        return collector.generateStatements();
    }
    
    /**
     * Creates tables in the database for the given entity classes.
     */
    public static void createTables(javax.sql.DataSource db, Class<?>... classes) {
        DB.runInTransaction(db, c -> {
            for (String ddl : generateCreateTableStatements(classes)) {
                DB.executeDDL(c, ddl);
            }
        });
    }
    
    // ========== Schema Migration Methods ==========
    
    /**
     * Generates DDL statements (CREATE TABLE or ALTER TABLE) based on the existing schema.
     * If a table doesn't exist, generates CREATE TABLE.
     * If a table exists but has missing columns, generates ALTER TABLE ADD COLUMN.
     * 
     * @param schemaInfo the existing schema information
     * @param entityClasses the entity classes to generate DDL for
     * @return list of DDL statements
     */
    public static List<String> generateMigrationStatements(SchemaInfo schemaInfo, Class<?>... entityClasses) {
        return generateMigrationStatements(schemaInfo, DbContext.getDefault(), entityClasses);
    }
    
    /**
     * Generates DDL statements (CREATE TABLE or ALTER TABLE) based on the existing schema.
     * If a table doesn't exist, generates CREATE TABLE.
     * If a table exists but has missing columns, generates ALTER TABLE ADD COLUMN.
     * 
     * @param schemaInfo the existing schema information
     * @param dbContext the database context for dialect-specific generation
     * @param entityClasses the entity classes to generate DDL for
     * @return list of DDL statements
     */
    public static List<String> generateMigrationStatements(SchemaInfo schemaInfo, DbContext dbContext, Class<?>... entityClasses) {
        MigrationCollector collector = new MigrationCollector(dbContext, schemaInfo);
        
        for (Class<?> entityClass : entityClasses) {
            QueryTree tree = QueryTreeBuilder.from(entityClass);
            collector.collectFromTree(tree);
        }
        
        return collector.generateStatements();
    }
    
    /**
     * Generates DDL statements (CREATE TABLE or ALTER TABLE) based on the existing schema.
     * Uses TypeModel for better flexibility.
     * 
     * @param schemaInfo the existing schema information
     * @param dbContext the database context for dialect-specific generation
     * @param entityTypes the entity types to generate DDL for
     * @return list of DDL statements
     */
    public static List<String> generateMigrationStatements(SchemaInfo schemaInfo, DbContext dbContext, TypeModel... entityTypes) {
        MigrationCollector collector = new MigrationCollector(dbContext, schemaInfo);
        
        for (TypeModel entityType : entityTypes) {
            QueryTree tree = QueryTreeBuilder.from(entityType);
            collector.collectFromTree(tree);
        }
        
        return collector.generateStatements();
    }
    
    /**
     * Migrates the database schema: creates new tables and adds missing columns.
     * 
     * @param db the data source to execute the statements on
     * @param classes the entity classes to migrate
     */
    public static void migrateSchema(javax.sql.DataSource db, Class<?>... classes) {
        DB.runInTransaction(db, c -> {
            SchemaInfo schemaInfo = SchemaInfo.fromConnection(c);
            DbContext dbContext = DbContext.getDefault();
            for (String ddl : generateMigrationStatements(schemaInfo, dbContext, classes)) {
                DB.executeDDL(c, ddl);
            }
        });
    }
    
    // ========== Internal Schema Collection ==========
    
    /**
     * Represents a deferred FK column to be added to a table after all tables are collected.
     */
    private record DeferredFkColumn(
        TableInfo targetTable,
        String columnName,
        TableInfo refTable,
        String refColumn
    ) {}
    
    /**
     * Collects schema information from QueryTree and generates DDL statements.
     * Only generates tables for explicitly requested classes, not for joined entities.
     */
    private static class SchemaCollector {
        private final DbContext dbContext;
        private final Set<String> generatedTables = new HashSet<>();
        private final List<TableDefinition> tables = new ArrayList<>();
        private final List<LinkTableDefinition> linkTables = new ArrayList<>();
        private final List<ForeignKeyConstraint> foreignKeys = new ArrayList<>();
        private final List<DeferredFkColumn> deferredFkColumns = new ArrayList<>();
        
        SchemaCollector(DbContext dbContext) {
            this.dbContext = dbContext;
        }
        
        void collectFromTree(QueryTree tree) {
            if (tree.root() == null) {
                return;
            }
            // Only collect the root table - not joined entities
            if (tree.root() instanceof JoinedNode joined) {
                collectRootTable(joined);
            }
        }
        
        /**
         * Collects schema info for a root table (explicitly requested class).
         * Does NOT recursively create tables for joined entities.
         */
        private void collectRootTable(JoinedNode joined) {
            TableInfo tableInfo = joined.tableInfo();
            String fullTableName = getFullTableName(tableInfo);
            
            // Skip if already generated
            if (generatedTables.contains(fullTableName)) {
                return;
            }
            
            generatedTables.add(fullTableName);
            LOG.debug("Collecting table: {}", fullTableName);
            
            // Create table definition
            TableDefinition tableDef = new TableDefinition(tableInfo);
            
            // Collect columns from fields
            collectColumns(joined, tableDef, "");
            
            // Handle single-table inheritance discriminator
            if (joined.isSingleTableInheritance() && joined.discriminatorColumn() != null) {
                ColumnDefinition discCol = new ColumnDefinition(
                    joined.discriminatorColumn(),
                    "VARCHAR(255)",
                    false, false, true, false
                );
                tableDef.addColumnIfAbsent(discCol);
            }
            
            tables.add(tableDef);
            
            // Process children for FK columns and link tables (but don't create their tables)
            processChildrenForFKs(joined, tableDef);
            
            // For table-per-subclass inheritance, also generate tables for superclasses and subclasses
            collectInheritanceTables(joined);
        }
        
        /**
         * Recursively collects tables for inheritance nodes (superclass and subclass) 
         * in table-per-subclass inheritance.
         */
        private void collectInheritanceTables(JoinedNode parent) {
            for (QueryNode child : parent.children()) {
                if (child instanceof JoinedNode childJoined) {
                    // Handle superclass tables
                    if (childJoined.isSuperClass()) {
                        collectInheritanceTable(childJoined);
                    }
                    // Handle subclass tables
                    if (childJoined.isSubClass()) {
                        collectInheritanceTable(childJoined);
                    }
                }
            }
        }
        
        /**
         * Collects schema info for an inheritance table (superclass or subclass) 
         * in table-per-subclass inheritance.
         */
        private void collectInheritanceTable(JoinedNode inheritanceNode) {
            TableInfo tableInfo = inheritanceNode.tableInfo();
            String fullTableName = getFullTableName(tableInfo);
            
            // Skip if already generated
            if (generatedTables.contains(fullTableName)) {
                return;
            }
            
            generatedTables.add(fullTableName);
            LOG.debug("Collecting inheritance table: {}", fullTableName);
            
            // Create table definition
            TableDefinition tableDef = new TableDefinition(tableInfo);
            
            // Collect columns from fields
            collectColumns(inheritanceNode, tableDef, "");
            
            tables.add(tableDef);
            
            // Recursively process children that might also be inheritance nodes
            collectInheritanceTables(inheritanceNode);
        }
        
        /**
         * Process children to extract FK columns and link table info.
         * Does NOT create tables for joined entities.
         */
        private void processChildrenForFKs(JoinedNode parent, TableDefinition parentTableDef) {
            for (QueryNode child : parent.children()) {
                if (child instanceof JoinedNode childJoined) {
                    JoinInfo joinInfo = childJoined.joinInfo();
                    if (joinInfo == null) continue;
                    
                    // Handle many-to-many link tables
                    if (joinInfo.joinTableInfo() != null) {
                        collectLinkTable(parent, childJoined, joinInfo.joinTableInfo());
                    }
                    
                    // Handle FK in parent (entity reference like Order.customer)
                    if (joinInfo.joinCondition() instanceof JoinCondition.ForeignKeyInParent fkInParent) {
                        // Add FK column to parent table
                        boolean nullable = true;
                        boolean unique = false;
                        if (joinInfo.linkField() != null) {
                            var linkAnn = joinInfo.linkField().getAnnotation(Link.class);
                            if (linkAnn.isPresent()) {
                                nullable = linkAnn.get().getBooleanAttribute("nullable").orElse(true);
                                unique = linkAnn.get().getBooleanAttribute("unique").orElse(false);
                            }
                        }
                        
                        ColumnDefinition fkCol = new ColumnDefinition(
                            fkInParent.foreignKeyColumn(),
                            dbContext.getForeignKeyColumnType(),
                            false, false, !nullable, unique
                        );
                        parentTableDef.addColumnIfAbsent(fkCol);
                        
                        // Add FK constraint
                        foreignKeys.add(new ForeignKeyConstraint(
                            parent.tableInfo(),
                            fkInParent.foreignKeyColumn(),
                            childJoined.tableInfo(),
                            fkInParent.referencedColumn()
                        ));
                    }
                    
                    // Handle FK in child (one-to-many like Author.books)
                    // The FK column should go in the child table - defer until all tables collected
                    if (joinInfo.joinCondition() instanceof JoinCondition.ForeignKeyInChild fkInChild) {
                        deferredFkColumns.add(new DeferredFkColumn(
                            childJoined.tableInfo(),
                            fkInChild.foreignKeyColumn(),
                            parent.tableInfo(),
                            fkInChild.referencedColumn()
                        ));
                    }
                } else if (child instanceof EmbeddedNode embedded) {
                    // Check embedded children for FKs
                    for (QueryNode embeddedChild : embedded.children()) {
                        if (embeddedChild instanceof JoinedNode embeddedJoined) {
                            JoinInfo joinInfo = embeddedJoined.joinInfo();
                            if (joinInfo != null && joinInfo.joinCondition() instanceof JoinCondition.ForeignKeyInParent fkInParent) {
                                ColumnDefinition fkCol = new ColumnDefinition(
                                    embedded.embedInfo().fieldPrefix() + fkInParent.foreignKeyColumn(),
                                    dbContext.getForeignKeyColumnType(),
                                    false, false, false, false
                                );
                                parentTableDef.addColumnIfAbsent(fkCol);
                            }
                        }
                    }
                }
            }
        }
        
        private void collectColumns(TableNode tableNode, TableDefinition tableDef, String prefix) {
            boolean isCompositeKey = tableNode instanceof JoinedNode jn && jn.idFieldNames().size() > 1;
            Set<String> idFieldNames = tableNode instanceof JoinedNode jn 
                ? new HashSet<>(jn.idFieldNames()) 
                : Set.of();
            
            for (FieldSelectionBase fsb : tableNode.fields()) {
                if (!(fsb instanceof FieldSelection fs)) {
                    continue;
                }
                
                String columnName = fs.columnName();
                if (columnName == null) {
                    continue; // Skip computed fields without column name
                }
                columnName = prefix + columnName;
                
                FieldModel field = fs.field();
                boolean isPrimaryKey = field != null && idFieldNames.contains(field.getName());
                boolean autoIncrement = isPrimaryKey && !isCompositeKey;
                
                // Get column constraints from @Column annotation
                boolean nullable = true;
                boolean unique = false;
                if (field != null) {
                    var columnAnn = field.getAnnotation(Column.class);
                    if (columnAnn.isPresent()) {
                        nullable = columnAnn.get().getBooleanAttribute("nullable").orElse(true);
                        unique = columnAnn.get().getBooleanAttribute("unique").orElse(false);
                    }
                }
                
                String sqlType = getSqlType(field, autoIncrement);
                
                ColumnDefinition colDef = new ColumnDefinition(
                    columnName, sqlType, autoIncrement, isPrimaryKey,
                    !nullable && !autoIncrement, unique
                );
                tableDef.addColumnIfAbsent(colDef);
                
                if (isPrimaryKey) {
                    tableDef.addPrimaryKeyColumn(columnName);
                }
            }
            
            // Process embedded nodes for their columns
            // Note: EmbeddedNode fields already have the prefix in their columnName 
            // (added by SimpleFieldTransform), so we pass empty string for embedded
            for (QueryNode child : tableNode.children()) {
                if (child instanceof EmbeddedNode embedded) {
                    collectColumns(embedded, tableDef, "");
                }
            }
        }
        
        private String getSqlType(FieldModel field, boolean autoIncrement) {
            if (autoIncrement) {
                String autoIncType = dbContext.getAutoIncrementKeyColumnType();
                if (!autoIncType.equals("BIGINT")) {
                    return autoIncType; // e.g., BIGSERIAL for Postgres
                }
            }
            return field != null ? dbContext.mapJavaTypeToSql(field) : dbContext.getForeignKeyColumnType();
        }
        
        private void collectLinkTable(JoinedNode parent, JoinedNode child, JoinTableInfo joinTableInfo) {
            String linkTableFullName = getFullTableName(joinTableInfo.joinTable());
            
            if (generatedTables.contains(linkTableFullName)) {
                return;
            }
            generatedTables.add(linkTableFullName);
            
            LinkTableDefinition ltd = new LinkTableDefinition(
                joinTableInfo.joinTable(),
                joinTableInfo.parentFkColumn(),
                parent.tableInfo(),
                joinTableInfo.parentRefColumn(),
                joinTableInfo.targetFkColumn(),
                child.tableInfo(),
                joinTableInfo.targetRefColumn()
            );
            linkTables.add(ltd);
        }
        
        List<String> generateStatements() {
            List<String> statements = new ArrayList<>();
            
            // Process deferred FK columns (one-to-many relationships)
            // Add FK columns to target tables before generating CREATE TABLE
            for (DeferredFkColumn dfk : deferredFkColumns) {
                // Find the target table
                String targetFullName = getFullTableName(dfk.targetTable);
                TableDefinition targetTableDef = tables.stream()
                    .filter(td -> getFullTableName(td.tableInfo).equals(targetFullName))
                    .findFirst()
                    .orElse(null);
                
                if (targetTableDef != null) {
                    // Add the FK column to the target table
                    ColumnDefinition fkCol = new ColumnDefinition(
                        dfk.columnName,
                        dbContext.getForeignKeyColumnType(),
                        false, false, false, false
                    );
                    targetTableDef.addColumnIfAbsent(fkCol);
                    
                    // Add FK constraint
                    foreignKeys.add(new ForeignKeyConstraint(
                        dfk.targetTable,
                        dfk.columnName,
                        dfk.refTable,
                        dfk.refColumn
                    ));
                }
            }
            
            // Generate CREATE TABLE for regular tables
            for (TableDefinition td : tables) {
                statements.add(generateCreateTable(td));
            }
            
            // Generate CREATE TABLE for link tables
            for (LinkTableDefinition ltd : linkTables) {
                statements.add(generateCreateLinkTable(ltd));
            }
            
            // Generate ALTER TABLE for FK constraints
            Set<String> generatedFks = new HashSet<>();
            for (ForeignKeyConstraint fk : foreignKeys) {
                String fkKey = getFullTableName(fk.table) + "." + fk.column;
                if (!generatedFks.contains(fkKey.toLowerCase())) {
                    generatedFks.add(fkKey.toLowerCase());
                    statements.add(generateAlterTableAddForeignKey(fk));
                }
            }
            
            // Add FK constraints for link tables
            for (LinkTableDefinition ltd : linkTables) {
                statements.add(generateAlterTableAddForeignKey(new ForeignKeyConstraint(
                    ltd.linkTable, ltd.ownerColumn, ltd.ownerTable, ltd.ownerRefColumn
                )));
                statements.add(generateAlterTableAddForeignKey(new ForeignKeyConstraint(
                    ltd.linkTable, ltd.foreignColumn, ltd.foreignTable, ltd.foreignRefColumn
                )));
            }
            
            return statements;
        }
        
        private String generateCreateTable(TableDefinition td) {
            StringBuilder sb = new StringBuilder();
            sb.append("CREATE TABLE ");
            sb.append(getFullTableName(td.tableInfo));
            sb.append(" (\n");
            
            List<String> colDefs = new ArrayList<>();
            for (ColumnDefinition col : td.columns) {
                colDefs.add(formatColumnDefinition(col));
            }
            
            boolean hasPk = !td.primaryKeyColumns.isEmpty();
            for (int i = 0; i < colDefs.size(); i++) {
                sb.append("  ").append(colDefs.get(i));
                if (i < colDefs.size() - 1 || hasPk) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            
            if (hasPk) {
                sb.append("  PRIMARY KEY (");
                List<String> quotedPks = td.primaryKeyColumns.stream()
                    .map(dbContext::quoteObjectNames)
                    .toList();
                sb.append(String.join(", ", quotedPks));
                sb.append(")\n");
            }
            
            sb.append(")");
            
            String suffix = dbContext.getCreateTableSuffix();
            if (suffix != null && !suffix.isEmpty()) {
                sb.append(suffix);
            }
            sb.append(";");
            
            return sb.toString();
        }
        
        private String formatColumnDefinition(ColumnDefinition col) {
            StringBuilder sb = new StringBuilder();
            sb.append(dbContext.quoteObjectNames(col.name));
            sb.append(" ");
            sb.append(col.sqlType);
            
            // For auto-increment, add syntax if needed (and not already in type like BIGSERIAL)
            if (col.autoIncrement && dbContext.getAutoIncrementKeyColumnType().equals("BIGINT")) {
                String autoIncSyntax = dbContext.getAutoIncrementSyntax();
                if (!autoIncSyntax.isEmpty()) {
                    sb.append(" ").append(autoIncSyntax);
                }
            }
            
            if (col.notNull && !col.autoIncrement) {
                sb.append(" NOT NULL");
            }
            if (col.unique) {
                sb.append(" UNIQUE");
            }
            
            return sb.toString();
        }
        
        private String generateCreateLinkTable(LinkTableDefinition ltd) {
            StringBuilder sb = new StringBuilder();
            sb.append("CREATE TABLE ");
            sb.append(getFullTableName(ltd.linkTable));
            sb.append(" (\n");
            
            sb.append("  ").append(dbContext.quoteObjectNames(ltd.ownerColumn));
            sb.append(" ").append(dbContext.getForeignKeyColumnType());
            sb.append(",\n");
            
            sb.append("  ").append(dbContext.quoteObjectNames(ltd.foreignColumn));
            sb.append(" ").append(dbContext.getForeignKeyColumnType());
            sb.append(",\n");
            
            sb.append("  PRIMARY KEY (");
            sb.append(dbContext.quoteObjectNames(ltd.ownerColumn));
            sb.append(", ");
            sb.append(dbContext.quoteObjectNames(ltd.foreignColumn));
            sb.append(")\n");
            
            sb.append(")");
            
            String suffix = dbContext.getCreateTableSuffix();
            if (suffix != null && !suffix.isEmpty()) {
                sb.append(suffix);
            }
            sb.append(";");
            
            return sb.toString();
        }
        
        private String generateAlterTableAddForeignKey(ForeignKeyConstraint fk) {
            StringBuilder sb = new StringBuilder();
            sb.append("ALTER TABLE ").append(getFullTableName(fk.table));
            sb.append(" ADD FOREIGN KEY (").append(dbContext.quoteObjectNames(fk.column)).append(")");
            sb.append(" REFERENCES ").append(getFullTableName(fk.refTable));
            sb.append("(").append(dbContext.quoteObjectNames(fk.refColumn)).append(");");
            return sb.toString();
        }
        
        private String getFullTableName(TableInfo ti) {
            if (ti.schemaName() != null && !ti.schemaName().isEmpty()) {
                return dbContext.quoteObjectNames(ti.schemaName()) + "." + dbContext.quoteObjectNames(ti.tableName());
            }
            return dbContext.quoteObjectNames(ti.tableName());
        }
    }
    
    /**
     * Collects schema information from QueryTree and generates migration DDL statements.
     * Compares against existing schema to determine CREATE TABLE vs ALTER TABLE.
     * Maintains entity-processing order for statements.
     */
    private static class MigrationCollector {
        private final DbContext dbContext;
        private final SchemaInfo schemaInfo;
        private final Set<String> processedTables = new HashSet<>();
        // Combined list to maintain processing order: either TableDefinition (new) or AlterTableDefinition (existing)
        private final List<Object> tableOperations = new ArrayList<>();
        private final List<LinkTableDefinition> linkTables = new ArrayList<>();
        private final List<ForeignKeyConstraint> foreignKeys = new ArrayList<>();
        private final List<DeferredFkColumn> deferredFkColumns = new ArrayList<>();
        
        MigrationCollector(DbContext dbContext, SchemaInfo schemaInfo) {
            this.dbContext = dbContext;
            this.schemaInfo = schemaInfo;
        }
        
        void collectFromTree(QueryTree tree) {
            if (tree.root() == null) {
                return;
            }
            if (tree.root() instanceof JoinedNode joined) {
                collectRootTable(joined);
            }
        }
        
        private void collectRootTable(JoinedNode joined) {
            TableInfo tableInfo = joined.tableInfo();
            String fullTableName = getFullTableName(tableInfo);
            
            if (processedTables.contains(fullTableName)) {
                return;
            }
            processedTables.add(fullTableName);
            LOG.debug("Processing table for migration: {}", fullTableName);
            
            // Check if table exists
            SchemaInfo.TableInfo existingTable = schemaInfo.getTable(
                tableInfo.schemaName(), tableInfo.tableName());
            
            // Create table definition with all required columns
            TableDefinition tableDef = new TableDefinition(tableInfo);
            collectColumns(joined, tableDef, "");
            
            // Handle single-table inheritance discriminator
            if (joined.isSingleTableInheritance() && joined.discriminatorColumn() != null) {
                ColumnDefinition discCol = new ColumnDefinition(
                    joined.discriminatorColumn(),
                    "VARCHAR(255)",
                    false, false, true, false
                );
                tableDef.addColumnIfAbsent(discCol);
            }
            
            if (existingTable == null) {
                // Table doesn't exist - create it
                tableOperations.add(tableDef);
            } else {
                // Table exists - find missing columns
                List<ColumnDefinition> missingColumns = new ArrayList<>();
                for (ColumnDefinition col : tableDef.columns) {
                    if (!existingTable.hasColumn(col.name)) {
                        missingColumns.add(col);
                    }
                }
                if (!missingColumns.isEmpty()) {
                    tableOperations.add(new AlterTableDefinition(tableInfo, missingColumns));
                }
            }
            
            // Process children for FK columns and link tables
            processChildrenForFKs(joined, tableDef, existingTable);
        }
        
        private void processChildrenForFKs(JoinedNode parent, TableDefinition parentTableDef, 
                                          SchemaInfo.TableInfo existingParentTable) {
            for (QueryNode child : parent.children()) {
                if (child instanceof JoinedNode childJoined) {
                    JoinInfo joinInfo = childJoined.joinInfo();
                    if (joinInfo == null) continue;
                    
                    // Handle many-to-many link tables
                    if (joinInfo.joinTableInfo() != null) {
                        collectLinkTable(parent, childJoined, joinInfo.joinTableInfo());
                    }
                    
                    // Handle FK in parent
                    if (joinInfo.joinCondition() instanceof JoinCondition.ForeignKeyInParent fkInParent) {
                        boolean nullable = true;
                        boolean unique = false;
                        if (joinInfo.linkField() != null) {
                            var linkAnn = joinInfo.linkField().getAnnotation(Link.class);
                            if (linkAnn.isPresent()) {
                                nullable = linkAnn.get().getBooleanAttribute("nullable").orElse(true);
                                unique = linkAnn.get().getBooleanAttribute("unique").orElse(false);
                            }
                        }
                        
                        ColumnDefinition fkCol = new ColumnDefinition(
                            fkInParent.foreignKeyColumn(),
                            dbContext.getForeignKeyColumnType(),
                            false, false, !nullable, unique
                        );
                        
                        // Add to parent table if not already there
                        if (!parentTableDef.columnNames.contains(fkCol.name.toLowerCase())) {
                            parentTableDef.addColumnIfAbsent(fkCol);
                            
                            // For existing tables, add FK column to the alter definition
                            if (existingParentTable != null && !existingParentTable.hasColumn(fkCol.name)) {
                                addMissingColumnToAlterDef(parent.tableInfo(), fkCol);
                            }
                        }
                        
                        // Add FK constraint (only for new tables)
                        if (existingParentTable == null) {
                            foreignKeys.add(new ForeignKeyConstraint(
                                parent.tableInfo(),
                                fkInParent.foreignKeyColumn(),
                                childJoined.tableInfo(),
                                fkInParent.referencedColumn()
                            ));
                        }
                    }
                    
                    // Handle FK in child
                    if (joinInfo.joinCondition() instanceof JoinCondition.ForeignKeyInChild fkInChild) {
                        deferredFkColumns.add(new DeferredFkColumn(
                            childJoined.tableInfo(),
                            fkInChild.foreignKeyColumn(),
                            parent.tableInfo(),
                            fkInChild.referencedColumn()
                        ));
                    }
                } else if (child instanceof EmbeddedNode embedded) {
                    for (QueryNode embeddedChild : embedded.children()) {
                        if (embeddedChild instanceof JoinedNode embeddedJoined) {
                            JoinInfo joinInfo = embeddedJoined.joinInfo();
                            if (joinInfo != null && joinInfo.joinCondition() instanceof JoinCondition.ForeignKeyInParent fkInParent) {
                                ColumnDefinition fkCol = new ColumnDefinition(
                                    embedded.embedInfo().fieldPrefix() + fkInParent.foreignKeyColumn(),
                                    dbContext.getForeignKeyColumnType(),
                                    false, false, false, false
                                );
                                parentTableDef.addColumnIfAbsent(fkCol);
                            }
                        }
                    }
                }
            }
        }
        
        private void addMissingColumnToAlterDef(TableInfo tableInfo, ColumnDefinition fkCol) {
            // Find existing alter definition for this table
            for (Object op : tableOperations) {
                if (op instanceof AlterTableDefinition alterDef && alterDef.tableInfo.equals(tableInfo)) {
                    if (alterDef.missingColumns.stream().noneMatch(c -> c.name.equalsIgnoreCase(fkCol.name))) {
                        alterDef.missingColumns.add(fkCol);
                    }
                    return;
                }
            }
            // If no alter def exists yet, create one
            AlterTableDefinition alterDef = new AlterTableDefinition(tableInfo, new ArrayList<>());
            alterDef.missingColumns.add(fkCol);
            tableOperations.add(alterDef);
        }
        
        private void collectColumns(TableNode tableNode, TableDefinition tableDef, String prefix) {
            boolean isCompositeKey = tableNode instanceof JoinedNode jn && jn.idFieldNames().size() > 1;
            Set<String> idFieldNames = tableNode instanceof JoinedNode jn 
                ? new HashSet<>(jn.idFieldNames()) 
                : Set.of();
            
            for (FieldSelectionBase fsb : tableNode.fields()) {
                if (!(fsb instanceof FieldSelection fs)) {
                    continue;
                }
                
                String columnName = fs.columnName();
                if (columnName == null) {
                    continue;
                }
                columnName = prefix + columnName;
                
                FieldModel field = fs.field();
                boolean isPrimaryKey = field != null && idFieldNames.contains(field.getName());
                boolean autoIncrement = isPrimaryKey && !isCompositeKey;
                
                boolean nullable = true;
                boolean unique = false;
                if (field != null) {
                    var columnAnn = field.getAnnotation(Column.class);
                    if (columnAnn.isPresent()) {
                        nullable = columnAnn.get().getBooleanAttribute("nullable").orElse(true);
                        unique = columnAnn.get().getBooleanAttribute("unique").orElse(false);
                    }
                }
                
                String sqlType = getSqlType(field, autoIncrement);
                
                ColumnDefinition colDef = new ColumnDefinition(
                    columnName, sqlType, autoIncrement, isPrimaryKey,
                    !nullable && !autoIncrement, unique
                );
                tableDef.addColumnIfAbsent(colDef);
                
                if (isPrimaryKey) {
                    tableDef.addPrimaryKeyColumn(columnName);
                }
            }
            
            for (QueryNode child : tableNode.children()) {
                if (child instanceof EmbeddedNode embedded) {
                    String embeddedPrefix = prefix + embedded.embedInfo().fieldPrefix();
                    collectColumns(embedded, tableDef, embeddedPrefix);
                }
            }
        }
        
        private String getSqlType(FieldModel field, boolean autoIncrement) {
            if (autoIncrement) {
                String autoIncType = dbContext.getAutoIncrementKeyColumnType();
                if (!autoIncType.equals("BIGINT")) {
                    return autoIncType;
                }
            }
            return field != null ? dbContext.mapJavaTypeToSql(field) : dbContext.getForeignKeyColumnType();
        }
        
        private void collectLinkTable(JoinedNode parent, JoinedNode child, JoinTableInfo joinTableInfo) {
            String linkTableFullName = getFullTableName(joinTableInfo.joinTable());
            
            if (processedTables.contains(linkTableFullName)) {
                return;
            }
            processedTables.add(linkTableFullName);
            
            // Check if link table exists
            SchemaInfo.TableInfo existingLinkTable = schemaInfo.getTable(
                joinTableInfo.joinTable().schemaName(), joinTableInfo.joinTable().tableName());
            
            if (existingLinkTable == null) {
                LinkTableDefinition ltd = new LinkTableDefinition(
                    joinTableInfo.joinTable(),
                    joinTableInfo.parentFkColumn(),
                    parent.tableInfo(),
                    joinTableInfo.parentRefColumn(),
                    joinTableInfo.targetFkColumn(),
                    child.tableInfo(),
                    joinTableInfo.targetRefColumn()
                );
                linkTables.add(ltd);
            }
        }
        
        List<String> generateStatements() {
            List<String> statements = new ArrayList<>();
            
            // Process deferred FK columns
            for (DeferredFkColumn dfk : deferredFkColumns) {
                String targetFullName = getFullTableName(dfk.targetTable);
                
                // Check if target table exists in database
                SchemaInfo.TableInfo existingTargetTable = schemaInfo.getTable(
                    dfk.targetTable.schemaName(), dfk.targetTable.tableName());
                
                // Find target table in table operations (new tables)
                TableDefinition targetTableDef = null;
                for (Object op : tableOperations) {
                    if (op instanceof TableDefinition td && getFullTableName(td.tableInfo).equals(targetFullName)) {
                        targetTableDef = td;
                        break;
                    }
                }
                
                ColumnDefinition fkCol = new ColumnDefinition(
                    dfk.columnName,
                    dbContext.getForeignKeyColumnType(),
                    false, false, false, false
                );
                
                if (targetTableDef != null) {
                    // Target is a new table - add column to it
                    targetTableDef.addColumnIfAbsent(fkCol);
                    foreignKeys.add(new ForeignKeyConstraint(
                        dfk.targetTable,
                        dfk.columnName,
                        dfk.refTable,
                        dfk.refColumn
                    ));
                } else if (existingTargetTable != null && !existingTargetTable.hasColumn(dfk.columnName)) {
                    // Target exists but missing the FK column - add to alter tables
                    addMissingColumnToAlterDef(dfk.targetTable, fkCol);
                }
            }
            
            // Generate statements in entity-processing order
            for (Object op : tableOperations) {
                if (op instanceof TableDefinition td) {
                    statements.add(generateCreateTable(td));
                } else if (op instanceof AlterTableDefinition atd) {
                    for (ColumnDefinition col : atd.missingColumns) {
                        statements.add(generateAlterTableAddColumn(atd.tableInfo, col));
                    }
                }
            }
            
            // Generate CREATE TABLE for link tables (after main tables)
            for (LinkTableDefinition ltd : linkTables) {
                statements.add(generateCreateLinkTable(ltd));
            }
            
            // Generate ALTER TABLE for FK constraints (only for new tables)
            Set<String> generatedFks = new HashSet<>();
            for (ForeignKeyConstraint fk : foreignKeys) {
                String fkKey = getFullTableName(fk.table) + "." + fk.column;
                if (!generatedFks.contains(fkKey.toLowerCase())) {
                    generatedFks.add(fkKey.toLowerCase());
                    statements.add(generateAlterTableAddForeignKey(fk));
                }
            }
            
            // Add FK constraints for new link tables
            for (LinkTableDefinition ltd : linkTables) {
                statements.add(generateAlterTableAddForeignKey(new ForeignKeyConstraint(
                    ltd.linkTable, ltd.ownerColumn, ltd.ownerTable, ltd.ownerRefColumn
                )));
                statements.add(generateAlterTableAddForeignKey(new ForeignKeyConstraint(
                    ltd.linkTable, ltd.foreignColumn, ltd.foreignTable, ltd.foreignRefColumn
                )));
            }
            
            return statements;
        }
        
        private String generateCreateTable(TableDefinition td) {
            StringBuilder sb = new StringBuilder();
            sb.append("CREATE TABLE ");
            sb.append(getFullTableName(td.tableInfo));
            sb.append(" (\n");
            
            List<String> colDefs = new ArrayList<>();
            for (ColumnDefinition col : td.columns) {
                colDefs.add(formatColumnDefinition(col));
            }
            
            boolean hasPk = !td.primaryKeyColumns.isEmpty();
            for (int i = 0; i < colDefs.size(); i++) {
                sb.append("  ").append(colDefs.get(i));
                if (i < colDefs.size() - 1 || hasPk) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            
            if (hasPk) {
                sb.append("  PRIMARY KEY (");
                List<String> quotedPks = td.primaryKeyColumns.stream()
                    .map(dbContext::quoteObjectNames)
                    .toList();
                sb.append(String.join(", ", quotedPks));
                sb.append(")\n");
            }
            
            sb.append(")");
            
            String suffix = dbContext.getCreateTableSuffix();
            if (suffix != null && !suffix.isEmpty()) {
                sb.append(suffix);
            }
            sb.append(";");
            
            return sb.toString();
        }
        
        private String formatColumnDefinition(ColumnDefinition col) {
            StringBuilder sb = new StringBuilder();
            sb.append(dbContext.quoteObjectNames(col.name));
            sb.append(" ");
            sb.append(col.sqlType);
            
            if (col.autoIncrement && dbContext.getAutoIncrementKeyColumnType().equals("BIGINT")) {
                String autoIncSyntax = dbContext.getAutoIncrementSyntax();
                if (!autoIncSyntax.isEmpty()) {
                    sb.append(" ").append(autoIncSyntax);
                }
            }
            
            if (col.notNull && !col.autoIncrement) {
                sb.append(" NOT NULL");
            }
            if (col.unique) {
                sb.append(" UNIQUE");
            }
            
            return sb.toString();
        }
        
        private String generateCreateLinkTable(LinkTableDefinition ltd) {
            StringBuilder sb = new StringBuilder();
            sb.append("CREATE TABLE ");
            sb.append(getFullTableName(ltd.linkTable));
            sb.append(" (\n");
            
            sb.append("  ").append(dbContext.quoteObjectNames(ltd.ownerColumn));
            sb.append(" ").append(dbContext.getForeignKeyColumnType());
            sb.append(",\n");
            
            sb.append("  ").append(dbContext.quoteObjectNames(ltd.foreignColumn));
            sb.append(" ").append(dbContext.getForeignKeyColumnType());
            sb.append(",\n");
            
            sb.append("  PRIMARY KEY (");
            sb.append(dbContext.quoteObjectNames(ltd.ownerColumn));
            sb.append(", ");
            sb.append(dbContext.quoteObjectNames(ltd.foreignColumn));
            sb.append(")\n");
            
            sb.append(")");
            
            String suffix = dbContext.getCreateTableSuffix();
            if (suffix != null && !suffix.isEmpty()) {
                sb.append(suffix);
            }
            sb.append(";");
            
            return sb.toString();
        }
        
        private String generateAlterTableAddColumn(TableInfo table, ColumnDefinition col) {
            StringBuilder sb = new StringBuilder();
            sb.append("ALTER TABLE ");
            sb.append(getFullTableName(table));
            sb.append(" ADD COLUMN ");
            sb.append(dbContext.quoteObjectNames(col.name));
            sb.append(" ");
            sb.append(col.sqlType);
            // Note: Don't add AUTO_INCREMENT for ALTER TABLE as that requires PRIMARY KEY changes
            
            if (col.notNull) {
                sb.append(" NOT NULL");
            }
            if (col.unique) {
                sb.append(" UNIQUE");
            }
            sb.append(";");
            return sb.toString();
        }
        
        private String generateAlterTableAddForeignKey(ForeignKeyConstraint fk) {
            StringBuilder sb = new StringBuilder();
            sb.append("ALTER TABLE ").append(getFullTableName(fk.table));
            sb.append(" ADD FOREIGN KEY (").append(dbContext.quoteObjectNames(fk.column)).append(")");
            sb.append(" REFERENCES ").append(getFullTableName(fk.refTable));
            sb.append("(").append(dbContext.quoteObjectNames(fk.refColumn)).append(");");
            return sb.toString();
        }
        
        private String getFullTableName(TableInfo ti) {
            if (ti.schemaName() != null && !ti.schemaName().isEmpty()) {
                return dbContext.quoteObjectNames(ti.schemaName()) + "." + dbContext.quoteObjectNames(ti.tableName());
            }
            return dbContext.quoteObjectNames(ti.tableName());
        }
    }
    
    /**
     * Represents ALTER TABLE statements for adding missing columns.
     */
    private static class AlterTableDefinition {
        final TableInfo tableInfo;
        final List<ColumnDefinition> missingColumns;
        
        AlterTableDefinition(TableInfo tableInfo, List<ColumnDefinition> missingColumns) {
            this.tableInfo = tableInfo;
            this.missingColumns = missingColumns;
        }
    }
    
    // ========== Data Classes ==========
    
    private static class TableDefinition {
        final TableInfo tableInfo;
        final List<ColumnDefinition> columns = new ArrayList<>();
        final List<String> primaryKeyColumns = new ArrayList<>();
        final Set<String> columnNames = new HashSet<>();
        
        TableDefinition(TableInfo tableInfo) {
            this.tableInfo = tableInfo;
        }
        
        void addColumnIfAbsent(ColumnDefinition col) {
            if (!columnNames.contains(col.name.toLowerCase())) {
                columns.add(col);
                columnNames.add(col.name.toLowerCase());
            }
        }
        
        void addPrimaryKeyColumn(String colName) {
            if (!primaryKeyColumns.contains(colName)) {
                primaryKeyColumns.add(colName);
            }
        }
    }
    
    private static class ColumnDefinition {
        final String name;
        final String sqlType;
        final boolean autoIncrement;
        final boolean notNull;
        final boolean unique;
        
        ColumnDefinition(String name, String sqlType, boolean autoIncrement, 
                        boolean isPrimaryKey, boolean notNull, boolean unique) {
            this.name = name;
            this.sqlType = sqlType;
            this.autoIncrement = autoIncrement;
            // isPrimaryKey tracked separately in TableDefinition.primaryKeyColumns
            this.notNull = notNull;
            this.unique = unique;
        }
    }
    
    private static class LinkTableDefinition {
        final TableInfo linkTable;
        final String ownerColumn;
        final TableInfo ownerTable;
        final String ownerRefColumn;
        final String foreignColumn;
        final TableInfo foreignTable;
        final String foreignRefColumn;
        
        LinkTableDefinition(TableInfo linkTable, String ownerColumn, TableInfo ownerTable, 
                           String ownerRefColumn, String foreignColumn, TableInfo foreignTable,
                           String foreignRefColumn) {
            this.linkTable = linkTable;
            this.ownerColumn = ownerColumn;
            this.ownerTable = ownerTable;
            this.ownerRefColumn = ownerRefColumn;
            this.foreignColumn = foreignColumn;
            this.foreignTable = foreignTable;
            this.foreignRefColumn = foreignRefColumn;
        }
    }
    
    private static class ForeignKeyConstraint {
        final TableInfo table;
        final String column;
        final TableInfo refTable;
        final String refColumn;
        
        ForeignKeyConstraint(TableInfo table, String column, TableInfo refTable, String refColumn) {
            this.table = table;
            this.column = column;
            this.refTable = refTable;
            this.refColumn = refColumn;
        }
    }
}
