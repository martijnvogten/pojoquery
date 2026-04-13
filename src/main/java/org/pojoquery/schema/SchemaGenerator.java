package org.pojoquery.schema;

import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.pipeline.AQTSchemaGenerator;

public class SchemaGenerator {
    
    public static void createTables(javax.sql.DataSource db, Class<?>... classes) {
        DB.runInTransaction(db, c -> {
            for (String ddl : AQTSchemaGenerator.generateSchemaDDLFromClasses(DbContext.getDefault(), classes)) {
                DB.executeDDL(c, ddl);
            }
        });
    }
    
}
