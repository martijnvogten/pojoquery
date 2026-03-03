package org.pojoquery.pipeline.querytree;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.pipeline.SqlQuery.JoinType;

/**
 * Tests for QueryTreeBuilder - building QueryTree from POJO classes.
 */
public class TestQueryTreeBuilder {

    @Table("person")
    public static class Person {
        @Id
        public Long id;
        public String name;
        public String email;
    }

    @Table("book")
    public static class Book {
        @Id
        public Long id;
        public String title;
        public Person author;
    }

    @Test
    public void testBuildSimplePerson() {
        QueryTree tree = QueryTreeBuilder.from(Person.class);
        
        System.out.println(tree);
        
        assertEquals("Person", tree.resultType().getSimpleName());        
        assertInstanceOf(JoinedNode.class, tree.root());
        JoinedNode root = (JoinedNode) tree.root();
        
        assertEquals("person", root.alias());
        assertEquals("person", root.tableInfo().tableName());
        assertNull(root.tableInfo().schemaName());
        
        // Should have 3 fields: id, name, email
        assertEquals(3, root.fields().size());
        
        // Check field aliases
        var fieldAliases = root.fields().stream()
            .map(FieldSelection::alias)
            .toList();
        assertTrue(fieldAliases.contains("person.id"));
        assertTrue(fieldAliases.contains("person.name"));
        assertTrue(fieldAliases.contains("person.email"));
        
        // Check ID fields
        assertEquals(1, root.idFieldNames().size());
        assertTrue(root.idFieldNames().contains("id"));
        
        // No children for simple class
        assertTrue(root.children().isEmpty());
    }

    @Test
    public void testBuildBookWithAuthor() {
        QueryTree tree = QueryTreeBuilder.from(Book.class);
        
        System.out.println(tree);
        
        JoinedNode root = (JoinedNode) tree.root();
        assertEquals("book", root.tableInfo().tableName());
        
        // Should have 2 simple fields: id, title
        assertEquals(2, root.fields().size());
        
        // Should have 1 child for author
        assertEquals(1, root.children().size());
        
        QueryNode authorChild = root.children().get(0);
        JoinInfo authorJoinInfo = authorChild.joinInfo();
        assertEquals("author", authorJoinInfo.linkField().getName());
        assertFalse(authorJoinInfo.isCollection());

		assertEquals("{book.author_id} = {author.id}", authorJoinInfo.condition().getSql());
        
        EmptyTableNode authorNode = (EmptyTableNode) authorChild;
        assertEquals("author", authorNode.alias());
        // assertEquals("person", authorNode.tableInfo().tableName());
        
        // Author should have 3 fields
        // assertEquals(3, authorNode.fields().size());
    }

    // --- Entities for join types test ---

    @Table("author")
    public static class Author {
        @Id Long id;
        String name;
    }

    @Table("comment")
    public static class Comment {
        @Id Long id;
        String text;
    }

    @Table("tag")
    public static class Tag {
        @Id Long id;
        String name;
    }

    @Table("article")
    public static class Article {
        @Id Long id;
        String title;
        
        Author author;                                      // entity reference → LEFT JOIN
        List<Comment> comments;                             // one-to-many → LEFT JOIN  
        @Link(linktable="article_tag") List<Tag> tags;      // many-to-many → two LEFT JOINs
    }

    @Test
    public void testArticleWithDifferentJoinTypes() {
        QueryTree tree = QueryTreeBuilder.from(Article.class);

		System.out.println(tree);

        JoinedNode root = (JoinedNode) tree.root();

        // Article has 3 children: author, comments, tags (via link table)
        assertEquals(3, root.children().size());

        // All joins are LEFT
        for (QueryNode child : root.children()) {
            assertEquals(JoinType.LEFT, child.joinInfo().joinType());
        }
    }

}