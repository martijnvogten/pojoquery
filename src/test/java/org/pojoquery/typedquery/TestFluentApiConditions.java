package org.pojoquery.typedquery;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext;

/**
 * Unit tests for the fluent API condition building.
 * 
 * <p>These tests verify SQL generation without requiring a database connection
 * or annotation processing. We manually create the query classes that would
 * normally be generated.
 */
public class TestFluentApiConditions {

    @BeforeEach
    void setup() {
        DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.HSQLDB));
    }

    // =========================================================================
    // Test Entity and Field References (normally generated)
    // =========================================================================

    /**
     * Simple test entity - no annotations needed for SQL generation tests.
     */
    public static class Person {
        public Long id;
        public String firstName;
        public String lastName;
        public Integer age;
    }

    /**
     * Field references (normally generated as Person_).
     */
    public static final class Person_ {
        public static final String TABLE = "person";
        
        public static final ComparableQueryField<Person, Long> id = 
            new ComparableQueryField<>(TABLE, "id", "id", Long.class);
        public static final ComparableQueryField<Person, String> firstName = 
            new ComparableQueryField<>(TABLE, "firstName", "firstName", String.class);
        public static final ComparableQueryField<Person, String> lastName = 
            new ComparableQueryField<>(TABLE, "lastName", "lastName", String.class);
        public static final ComparableQueryField<Person, Integer> age = 
            new ComparableQueryField<>(TABLE, "age", "age", Integer.class);
        
        private Person_() {}
    }

    // =========================================================================
    // Manual Query Implementation (normally generated)
    // =========================================================================

    /**
     * Minimal query implementation for testing SQL generation.
     */
    public static class PersonQuery extends TypedQuery<Person, PersonQuery> {

        public PersonQuery() {
            super("person");
        }

        @Override
        protected void initializeQuery() {
            // Add fields to select
            query.addField(org.pojoquery.SqlExpression.sql("{person.id}"), "person.id");
            query.addField(org.pojoquery.SqlExpression.sql("{person.firstName}"), "person.firstName");
            query.addField(org.pojoquery.SqlExpression.sql("{person.lastName}"), "person.lastName");
            query.addField(org.pojoquery.SqlExpression.sql("{person.age}"), "person.age");
        }

        @Override
        protected Class<Person> getEntityClass() {
            return Person.class;
        }

        /**
         * Start a fluent where clause chain.
         */
        public PersonQueryWhere where() {
            return new PersonQueryWhere(this);
        }

        @Override
        public PersonQuerySubClauseBuilder begin() {
            return new PersonQuerySubClauseBuilder(this);
        }
    }

    /**
     * Fluent where clause builder (normally generated).
     */
    public static class PersonQueryWhere 
            extends WhereChainBuilder<Person, PersonQuery, PersonQueryWhere, PersonInlineSubClause> {

        public PersonQueryWhere(PersonQuery query) {
            super(query);
        }

        // Field accessors
        public final ComparableChainField<Long> id = new ComparableChainField<>(Person_.id);
        public final ComparableChainField<String> firstName = new ComparableChainField<>(Person_.firstName);
        public final ComparableChainField<String> lastName = new ComparableChainField<>(Person_.lastName);
        public final ComparableChainField<Integer> age = new ComparableChainField<>(Person_.age);

        @Override
        protected PersonQueryWhere self() {
            return this;
        }

        @Override
        protected PersonInlineSubClause createInlineSubClauseBuilder() {
            return new PersonInlineSubClause(this);
        }
    }

    /**
     * Inline sub-clause builder for begin()/end() grouping within where() chains (normally generated).
     */
    public static class PersonInlineSubClause 
            extends InlineSubClauseBuilder<Person, PersonQuery, PersonQueryWhere, PersonInlineSubClause> {

        public PersonInlineSubClause(PersonQueryWhere parentBuilder) {
            super(parentBuilder);
        }

        // Field accessors
        public final ComparableChainField<Long> id = new ComparableChainField<>(Person_.id);
        public final ComparableChainField<String> firstName = new ComparableChainField<>(Person_.firstName);
        public final ComparableChainField<String> lastName = new ComparableChainField<>(Person_.lastName);
        public final ComparableChainField<Integer> age = new ComparableChainField<>(Person_.age);

        @Override
        protected PersonInlineSubClause self() {
            return this;
        }
    }

    /**
     * Sub-clause builder for grouping conditions from TypedQuery.begin() (normally generated).
     */
    public static class PersonQuerySubClauseBuilder extends SubClauseBuilder<Person, PersonQuery> {
        
        public PersonQuerySubClauseBuilder(PersonQuery parentQuery) {
            super(parentQuery);
        }

        public PersonQuerySubClauseWhere where() {
            return new PersonQuerySubClauseWhere(this);
        }
    }

    /**
     * Sub-clause where builder for TypedQuery.begin() (normally generated).
     */
    public static class PersonQuerySubClauseWhere 
            extends SubClauseWhereBuilder<Person, PersonQuery, PersonQuerySubClauseWhere> {

        public PersonQuerySubClauseWhere(PersonQuerySubClauseBuilder subClauseBuilder) {
            super(subClauseBuilder);
        }

        // Field accessors
        public final ComparableChainField<Long> id = new ComparableChainField<>(Person_.id);
        public final ComparableChainField<String> firstName = new ComparableChainField<>(Person_.firstName);
        public final ComparableChainField<String> lastName = new ComparableChainField<>(Person_.lastName);
        public final ComparableChainField<Integer> age = new ComparableChainField<>(Person_.age);

        @Override
        protected PersonQuerySubClauseWhere self() {
            return this;
        }
    }

    // =========================================================================
    // TESTS
    // =========================================================================

    @Test
    void testSimpleCondition() {
        String sql = Person_.lastName.eq("Smith").getSql();

        assertEquals("{person.lastName} = ?", sql);
    }

    @Test
    void testConditionApi_aAndB() {
        // Using Condition API: a AND b
        String sql = Person_.firstName.eq("John")
            .and(Person_.lastName.eq("Smith"))
            .getSql();

        assertEquals("({person.firstName} = ? AND {person.lastName} = ?)", sql);
    }

    @Test
    void testConditionApi_aOrB() {
        // Using Condition API: a OR b
        String sql = Person_.firstName.eq("John")
            .or(Person_.firstName.eq("Jane"))
            .getSql();

        assertEquals("({person.firstName} = ? OR {person.firstName} = ?)", sql);
    }

    @Test
    void testConditionApi_aAndB_Or_cAndD() {
        // (a AND b) OR (c AND d)
        Condition<Person> group1 = Person_.firstName.eq("John")
            .and(Person_.lastName.eq("Smith"));
        
        Condition<Person> group2 = Person_.firstName.eq("Jane")
            .and(Person_.lastName.eq("Doe"));
        
        String sql = group1.or(group2).getSql();

        assertEquals(
            "(({person.firstName} = ? AND {person.lastName} = ?) OR ({person.firstName} = ? AND {person.lastName} = ?))",
            sql);
    }

    @Test
    void testFluentChainApi_leftToRightEvaluation() {
        // Fluent chain evaluates left-to-right WITHOUT automatic grouping
        // a AND b OR c AND d becomes ((a AND b) OR c) AND d
        // NOT (a AND b) OR (c AND d)
        
        String sql = new PersonQuery()
            .where()
                .firstName.eq("John")    // a
                .and()
                .lastName.eq("Smith")    // b
            .or()
                .firstName.eq("Jane")    // c
            .and()
                .lastName.eq("Doe")      // d
            .toCondition().getSql();

        // Result is: ((firstName=John AND lastName=Smith) OR firstName=Jane) AND lastName=Doe
        assertEquals(
            "((({person.firstName} = ? AND {person.lastName} = ?) OR {person.firstName} = ?) AND {person.lastName} = ?)",
            sql);
    }

    @Test
    void testFluentChainApi_aAndB_Or_cAndD_withBegin() {
        // (a AND b) OR (c AND d) using begin()/end() for grouping
        // Now works because end() returns to ChainResult which has .or()
        
        String sql = new PersonQuery()
            .where()
                .begin()
                    .firstName.eq("John")
                    .and()
                    .lastName.eq("Smith")
                .end()
                .or()
                .begin()
                    .firstName.eq("Jane")
                    .and()
                    .lastName.eq("Doe")
                .end().toCondition().getSql();

        System.out.println("(a AND b) OR (c AND d) with begin/end SQL: " + sql);

        // Verify the WHERE clause structure: (a AND b) OR (c AND d)
        // Parentheses are added by the Condition.or() combination
        assertEquals(
			"(({person.firstName} = ? AND {person.lastName} = ?) OR ({person.firstName} = ? AND {person.lastName} = ?))",
            sql);
    }

    @Test
    void testExtractConditionFromChain() {
        // Build a reusable Condition from fluent chain using toCondition()
        String sql = new PersonQuery()
            .where()
                .firstName.eq("John")
                .and()
                .lastName.eq("Smith")
            .toCondition().getSql();

        assertEquals("({person.firstName} = ? AND {person.lastName} = ?)", sql);
    }

    @Test
    void testConditionApi_complexNesting() {
        // ((a AND b) OR (c AND d)) AND e
        Condition<Person> group1 = Person_.firstName.eq("John")
            .and(Person_.lastName.eq("Smith"));
        
        Condition<Person> group2 = Person_.firstName.eq("Jane")
            .and(Person_.lastName.eq("Doe"));
        
        Condition<Person> nameFilter = group1.or(group2);
        Condition<Person> ageFilter = Person_.age.ge(18);
        
        String sql = nameFilter.and(ageFilter).getSql();

        assertEquals(
            "((({person.firstName} = ? AND {person.lastName} = ?) OR ({person.firstName} = ? AND {person.lastName} = ?)) AND {person.age} >= ?)",
            sql);
    }

    @Test
    void testBeginEnd_SubClause() {
        // Using begin()/end() for explicit grouping - now with direct field access!
        String sql = new PersonQuery()
            .where()
                .begin()
                    .firstName.eq("John")
                    .or()
                    .lastName.eq("Smith")
                .end()
                .and()
                .age.ge(18)
            .toCondition().getSql();

        assertEquals(
            "(({person.firstName} = ? OR {person.lastName} = ?) AND {person.age} >= ?)",
            sql);
    }

    @Test
    void testIn() {
        // Using begin()/end() for explicit grouping - now with direct field access!
        String sql = new PersonQuery()
            .where()
			.firstName.in("John", "Smith").and().age.ge(18)
            .toCondition().getSql();

        assertEquals(
            "({person.firstName} IN (?, ?) AND {person.age} >= ?)",
            sql);
    }
}
