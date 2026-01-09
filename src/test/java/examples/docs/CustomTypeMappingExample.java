package examples.docs;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.dialects.HsqldbDbContext;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Example demonstrating how to customize data type mapping by creating
 * a custom DbContext that overrides mapJavaTypeToSql().
 * 
 * This is useful when you need to:
 * - Support custom Java types (like UUID)
 * - Use database-specific types (like PostgreSQL's JSONB, INET, etc.)
 * - Override default type mappings for your application's needs
 */
public class CustomTypeMappingExample {

    // tag::custom-dbcontext[]
    /**
     * Custom DbContext that extends HsqldbDbContext to add UUID support.
     * Override mapJavaTypeToSql() to map java.util.UUID to the database's UUID type.
     */
    public static class HsqldbWithUuidContext extends HsqldbDbContext {

        @Override
        public String mapJavaTypeToSql(Field field) {
            Class<?> type = field.getType();
            
            // Add support for UUID type
            if (type == UUID.class) {
                return "UUID";
            }
            
            // Fall back to parent implementation for all other types
            return super.mapJavaTypeToSql(field);
        }
    }
    // end::custom-dbcontext[]

    // tag::entity[]
    @Table("document")
    public static class Document {
        @Id
        public Long id;
        
        public UUID documentId;  // Uses native UUID type
        
        public String title;
        public String content;
    }
    // end::entity[]

    public static void demonstrateUuidSupport(DataSource db) {
        // tag::usage[]
        // Use runInTransaction for atomic operations
        DB.withConnection(db, (Connection c) -> {
            // Insert a document with a UUID
            Document doc = new Document();
            doc.documentId = UUID.randomUUID();
            doc.title = "Architecture Overview";
            doc.content = "This document describes the system architecture...";
            
            PojoQuery.insert(c, doc);
            
            // Query back by UUID using {alias} syntax for proper quoting
            List<Document> found = PojoQuery.build(Document.class)
                .addWhere("{document}.documentId = ?", doc.documentId)
                .execute(c);
            
            System.out.println("Found " + found.size() + " document(s)");
            for (Document fetched : found) {
                System.out.println("Fetched: " + fetched.title + " (UUID: " + fetched.documentId + ")");
            }
        });
        // end::usage[]
    }
    
    public static void main(String[] args) {
        // Set up the custom context
        DbContext.setDefault(new HsqldbWithUuidContext());
        
        // Create database and table
        DataSource db = createDatabase();
        createTable(db);
        
        // Run the demonstration
        demonstrateUuidSupport(db);
    }

    // tag::schema[]
    private static void createTable(DataSource db) {
        // SchemaGenerator uses mapJavaTypeToSql() from the DbContext,
        // so our UUID field will be mapped to the database's UUID type
        SchemaGenerator.createTables(db, Document.class);
    }
    // end::schema[]

    private static DataSource createDatabase() {
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:uuid_example");
        ds.setUser("SA");
        ds.setPassword("");
        return ds;
    }
}

