package org.pojoquery.integrationtest;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.pojoquery.DbContext;
import org.pojoquery.annotations.Column;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Integration test for SchemaGenerator with multiple entity classes mapping to the same table.
 * 
 * <p>This test verifies that when multiple entity classes map to the SAME table with
 * different @Column annotations on the same field, the schema generator correctly
 * resolves conflicts by applying the most restrictive constraint.
 * 
 * <p>The scenario: UserRef and User both map to "user" table, but only User has
 * @Column(unique=true) on username. The schema generator should apply the unique
 * constraint when generating the schema, regardless of class order.
 */
public class SchemaGeneratorInheritanceIT {

    // ========== Test Entities ==========
    
    /**
     * A lightweight reference entity with just id and username.
     * Maps to the SAME "user" table as User.
     * Note: username does NOT have the unique constraint here.
     */
    @Table("user")
    public static class UserRef {
        @Id
        Long id;
        
        String username;  // No @Column(unique) annotation
    }
    
    /**
     * The full User entity with all fields.
     * Maps to the SAME "user" table as UserRef.
     * username has @Column(unique=true).
     */
    @Table("user")
    public static class User {
        @Id
        Long id;
        
        @Column(unique = true)
        String username;  // Has unique constraint
        
        String email;
        
        String displayName;
    }
    
    /**
     * UserDetail extends User with additional fields.
     * Inherits username with @Column(unique=true) from User.
     */
    public static class UserDetail extends User {
        String bio;
        
        String avatarUrl;
    }
    
    // ========== Test ==========
    
    /**
     * When UserRef (no unique) and User (unique=true) both map to the same "user" table,
     * the schema generator should resolve the conflict by applying the unique constraint
     * from User, regardless of the order the classes are provided.
     * 
     * This ensures that if ANY class mapping to a table has a constraint on a field,
     * that constraint is applied to the generated schema.
     */
    @Test
    public void testUserRefAndUserTogetherResolvesToUnique() {
        DbContext dbContext = TestDatabaseProvider.getDbContext();
        
        // UserRef is listed FIRST (has no unique constraint on username)
        // User is listed SECOND (has unique=true on username)
        List<String> sqlStatements = SchemaGenerator.generateCreateTableStatements(
            dbContext, UserRef.class, User.class);
        String sql = String.join("\n", sqlStatements);
        System.out.println("UserRef + User CREATE TABLE:\n" + sql);
        
        // The schema should have UNIQUE on username because User has @Column(unique=true)
        // even though UserRef (listed first) does not have the annotation
        assertTrue("Should have UNIQUE on username when User (with unique=true) is included, " +
            "regardless of class order. Generated SQL:\n" + sql, 
            sql.contains("username VARCHAR(255) UNIQUE") || 
            sql.contains("username\" VARCHAR(255) UNIQUE"));
    }
}
