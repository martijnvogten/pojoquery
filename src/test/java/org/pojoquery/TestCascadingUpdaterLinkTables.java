package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Tests for CascadingUpdater with Link table (many-to-many) relationships.
 */
public class TestCascadingUpdaterLinkTables {

    private DataSource dataSource;

    // === Article/Tag entities ===

    @Table("article")
    public static class Article {
        @Id public Long id;
        public String title;
        @Link(linktable = "article_tag")
        public List<Tag> tags;
        
        public Article() { this.tags = new ArrayList<>(); }
        public Article(String title) { this(); this.title = title; }
    }

    @Table("tag")
    public static class Tag {
        @Id public Long id;
        public String name;
        
        public Tag() {}
        public Tag(String name) { this.name = name; }
    }

    // === Project/Member entities (custom linkfield) ===

    @Table("project")
    public static class Project {
        @Id public Long id;
        public String name;
        
        // Custom link table name and custom linkfield (parent FK column)
        @Link(linktable = "project_members", linkfield = "proj_id")
        public List<Member> members;
        
        public Project() { this.members = new ArrayList<>(); }
        public Project(String name) { this(); this.name = name; }
    }

    @Table("member")
    public static class Member {
        @Id public Long id;
        public String username;
        
        public Member() {}
        public Member(String username) { this.username = username; }
    }

    // === Team/Developer entities (custom foreignlinkfield) ===

    @Table("team")
    public static class Team {
        @Id public Long id;
        public String teamName;
        
        // Custom foreignlinkfield (foreign entity FK column)
        @Link(linktable = "team_developers", foreignlinkfield = "dev_id")
        public List<Developer> developers;
        
        public Team() { this.developers = new ArrayList<>(); }
        public Team(String teamName) { this(); this.teamName = teamName; }
    }

    @Table("developer")
    public static class Developer {
        @Id public Long id;
        public String devName;
        
        public Developer() {}
        public Developer(String devName) { this.devName = devName; }
    }

    // === Account/Permission entities (both custom fields) ===

    @Table("account")
    public static class Account {
        @Id public Long id;
        public String accountName;
        
        // Custom both linkfield and foreignlinkfield
        @Link(linktable = "account_permission_map", linkfield = "acct_id", foreignlinkfield = "perm_id")
        public List<Permission> permissions;
        
        public Account() { this.permissions = new ArrayList<>(); }
        public Account(String accountName) { this(); this.accountName = accountName; }
    }

    @Table("permission")
    public static class Permission {
        @Id public Long id;
        public String permName;
        
        public Permission() {}
        public Permission(String permName) { this.permName = permName; }
    }

    // === Resource/AccessLevel entities (fetchColumn for value collection) ===

    public enum AccessLevel {
        READ, WRITE, ADMIN
    }

    @Table("resource")
    public static class Resource {
        @Id public Long id;
        public String resourceName;
        
        // fetchColumn for value collection (enum) - use default linkfield naming for query compatibility
        @Link(linktable = "resource_access", fetchColumn = "access_level")
        public List<AccessLevel> accessLevels;
        
        public Resource() { this.accessLevels = new ArrayList<>(); }
        public Resource(String resourceName) { this(); this.resourceName = resourceName; }
    }

    @BeforeEach
    void setup() {
        DbContext.setDefault(DbContext.forDialect(Dialect.HSQLDB));

        // Create unique in-memory database
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:cascade_link_test_" + System.nanoTime());
        ds.setUser("SA");
        ds.setPassword("");
        dataSource = ds;
    }

    @Test
    public void testLinkTableSync() {
        // Create tables
        SchemaGenerator.createTables(dataSource, Article.class, Tag.class);

        DB.withConnection(dataSource, (Connection c) -> {
            // Create tags first (reference data)
            Tag java = new Tag("java");
            Tag sql = new Tag("sql");
            Tag python = new Tag("python");
            PojoQuery.insert(c, java);
            PojoQuery.insert(c, sql);
            PojoQuery.insert(c, python);

            // Insert article with tags
            Article article = new Article("PojoQuery Tutorial");
            article.tags.add(java);
            article.tags.add(sql);
            PojoQuery.insert(c, article);

            // Verify link table has 2 rows
            Article loaded = PojoQuery.build(Article.class).findById(c, article.id).get();
            assertEquals(2, loaded.tags.size());

            // Update: remove sql, add python
            loaded.tags.removeIf(t -> "sql".equals(t.name));
            loaded.tags.add(python);
            PojoQuery.update(c, loaded);

            // Verify link table updated
            Article reloaded = PojoQuery.build(Article.class).findById(c, article.id).get();
            assertEquals(2, reloaded.tags.size());
            assertTrue(reloaded.tags.stream().anyMatch(t -> "java".equals(t.name)));
            assertTrue(reloaded.tags.stream().anyMatch(t -> "python".equals(t.name)));
            assertFalse(reloaded.tags.stream().anyMatch(t -> "sql".equals(t.name)));
        });
    }

    @Test
    public void testLinkTableWithCustomLinkField() {
        // Custom linkfield (parent FK column name)
        // SchemaGenerator should pick up the custom column name from @Link(linkfield="proj_id")
        SchemaGenerator.createTables(dataSource, Project.class, Member.class);
        
        DB.withConnection(dataSource, (Connection c) -> {
            Member alice = new Member("alice");
            Member bob = new Member("bob");
            PojoQuery.insert(c, alice);
            PojoQuery.insert(c, bob);
            
            Project project = new Project("Backend");
            project.members.add(alice);
            project.members.add(bob);
            PojoQuery.insert(c, project);
            
            // Verify the link table was populated correctly
            Project loaded = PojoQuery.build(Project.class).findById(c, project.id).get();
            assertEquals(2, loaded.members.size());
            assertTrue(loaded.members.stream().anyMatch(m -> "alice".equals(m.username)));
            assertTrue(loaded.members.stream().anyMatch(m -> "bob".equals(m.username)));
        });
    }

    @Test
    public void testLinkTableWithCustomForeignLinkField() {
        // Custom foreignlinkfield (foreign entity FK column name)
        // SchemaGenerator should pick up the custom column name from @Link(foreignlinkfield="dev_id")
        SchemaGenerator.createTables(dataSource, Team.class, Developer.class);
        
        DB.withConnection(dataSource, (Connection c) -> {
            Developer dev1 = new Developer("John");
            Developer dev2 = new Developer("Jane");
            PojoQuery.insert(c, dev1);
            PojoQuery.insert(c, dev2);
            
            Team team = new Team("Core");
            team.developers.add(dev1);
            team.developers.add(dev2);
            PojoQuery.insert(c, team);
            
            // Verify the link table was populated correctly
            Team loaded = PojoQuery.build(Team.class).findById(c, team.id).get();
            assertEquals(2, loaded.developers.size());
            assertTrue(loaded.developers.stream().anyMatch(d -> "John".equals(d.devName)));
            assertTrue(loaded.developers.stream().anyMatch(d -> "Jane".equals(d.devName)));
        });
    }

    @Test
    public void testLinkTableWithBothCustomFields() {
        // Both linkfield and foreignlinkfield customized
        // SchemaGenerator should pick up both custom column names
        SchemaGenerator.createTables(dataSource, Account.class, Permission.class);
        
        DB.withConnection(dataSource, (Connection c) -> {
            Permission read = new Permission("read");
            Permission write = new Permission("write");
            Permission delete = new Permission("delete");
            PojoQuery.insert(c, read);
            PojoQuery.insert(c, write);
            PojoQuery.insert(c, delete);
            
            Account account = new Account("admin");
            account.permissions.add(read);
            account.permissions.add(write);
            PojoQuery.insert(c, account);
            
            // Verify the link table was populated correctly
            Account loaded = PojoQuery.build(Account.class).findById(c, account.id).get();
            assertEquals(2, loaded.permissions.size());
            
            // Test update: add delete, remove read
            loaded.permissions.add(delete);
            loaded.permissions.removeIf(p -> "read".equals(p.permName));
            PojoQuery.update(c, loaded);
            
            Account reloaded = PojoQuery.build(Account.class).findById(c, account.id).get();
            assertEquals(2, reloaded.permissions.size());
            assertTrue(reloaded.permissions.stream().anyMatch(p -> "write".equals(p.permName)));
            assertTrue(reloaded.permissions.stream().anyMatch(p -> "delete".equals(p.permName)));
            assertFalse(reloaded.permissions.stream().anyMatch(p -> "read".equals(p.permName)));
        });
    }

    @Test
    public void testLinkTableWithFetchColumnForValueCollection() {
        SchemaGenerator.createTables(dataSource, Resource.class);
        DB.withConnection(dataSource, (Connection c) -> {
            
            Resource resource = new Resource("document.pdf");
            resource.accessLevels.add(AccessLevel.READ);
            resource.accessLevels.add(AccessLevel.WRITE);
            PojoQuery.insert(c, resource);
            
            Resource loaded = PojoQuery.build(Resource.class).findById(c, resource.id).get();
            assertEquals(2, loaded.accessLevels.size());
            assertTrue(loaded.accessLevels.contains(AccessLevel.READ));
            assertTrue(loaded.accessLevels.contains(AccessLevel.WRITE));
            
            loaded.accessLevels.add(AccessLevel.ADMIN);
            loaded.accessLevels.remove(AccessLevel.WRITE);
            PojoQuery.update(c, loaded);
            
            Resource reloaded = PojoQuery.build(Resource.class).findById(c, resource.id).get();
            assertEquals(2, reloaded.accessLevels.size());
            assertTrue(reloaded.accessLevels.contains(AccessLevel.READ));
            assertTrue(reloaded.accessLevels.contains(AccessLevel.ADMIN));
            assertFalse(reloaded.accessLevels.contains(AccessLevel.WRITE));
        });
    }
}
