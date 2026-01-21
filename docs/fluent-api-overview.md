User: can you give me a visual overview (maybe a table) of the methods in the fluent api and a in which each of the classes in the chain they are available?

GitHub Copilot: Read [](file:///Users/martijn/git/pojoquery/src/main/java/org/pojoquery/typedquery)

Read [](file:///Users/martijn/git/pojoquery/src/main/java/org/pojoquery/typedquery/TypedQuery.java#1-1), lines 1 to 150

Read [](file:///Users/martijn/git/pojoquery/src/main/java/org/pojoquery/typedquery/WhereClause.java#1-1), lines 1 to 100

Read [](file:///Users/martijn/git/pojoquery/src/main/java/org/pojoquery/typedquery/WhereChainBuilder.java#1-1), lines 1 to 100

Read [](file:///Users/martijn/git/pojoquery/src/main/java/org/pojoquery/typedquery/OrderByBuilder.java#1-1), lines 1 to 100

Read [](file:///Users/martijn/git/pojoquery/src/main/java/org/pojoquery/typedquery/WhereClause.java#100-100), lines 100 to 126

Read [](file:///Users/martijn/git/pojoquery/src/main/java/org/pojoquery/typedquery/WhereChainBuilder.java#100-100), lines 100 to 179

Read [](file:///Users/martijn/git/pojoquery/src/main/java/org/pojoquery/typedquery/OrderByBuilder.java#100-100), lines 100 to 128

Read [](file:///Users/martijn/git/pojoquery/src/main/java/org/pojoquery/typedquery/TypedQuery.java#150-150), lines 150 to 300

Read [](file:///Users/martijn/git/pojoquery/src/main/java/org/pojoquery/typedquery/TypedQuery.java#300-300), lines 300 to 507

Read [](file:///Users/martijn/git/pojoquery/src/main/java/org/pojoquery/typedquery/ComparableWhereClause.java)

Read [](file:///Users/martijn/git/pojoquery/src/main/java/org/pojoquery/typedquery/SubClauseBuilder.java)

Read [](file:///Users/martijn/git/pojoquery/src/main/java/org/pojoquery/typedquery/AbstractConditionBuilder.java#1-1), lines 1 to 150

Read [](file:///Users/martijn/git/pojoquery/src/main/java/org/pojoquery/typedquery/AbstractConditionBuilder.java#150-150), lines 150 to 354

Read [](file:///Users/martijn/git/pojoquery/src/main/java/org/pojoquery/typedquery/QueryField.java#1-1), lines 1 to 100

Read [](file:///Users/martijn/git/pojoquery/src/main/java/org/pojoquery/typedquery/QueryField.java#100-100), lines 100 to 183

Read [](file:///Users/martijn/git/pojoquery/src/main/java/org/pojoquery/typedquery/ComparableQueryField.java)

Now I have a complete picture of the fluent API. Let me create a visual overview for you.

## Fluent API Overview

Here's a visual table showing the methods available in each class of the fluent API chain:

### Main Query Entry Points

| Method | `TypedQuery<E,Q>` | Returns |
|--------|:-----------------:|---------|
| `where(QueryField)` | ✅ | `WhereClause` |
| `where(ComparableQueryField)` | ✅ | `ComparableWhereClause` |
| `where(Condition)` | ✅ | `Q` (query) |
| `begin()` | ✅ | `SubClauseBuilder` |
| `where()` | ✅ (generated) | `WhereChainBuilder` |
| `orderBy()` | ✅ (generated) | `OrderByBuilder` |

---

### Condition Methods (WHERE clauses)

| Method | `WhereClause` | `ComparableWhereClause` | `ChainField` | `ComparableChainField` |
|--------|:-------------:|:----------------------:|:------------:|:---------------------:|
| `is(value)` / `eq(value)` | ✅ | ✅ | ✅ | ✅ |
| `isNot(value)` / `ne(value)` | ✅ | ✅ | ✅ | ✅ |
| `isNull()` | ✅ | ✅ | ✅ | ✅ |
| `isNotNull()` | ✅ | ✅ | ✅ | ✅ |
| `like(pattern)` | ✅ | ✅ | ✅ | ✅ |
| `notLike(pattern)` | ✅ | ✅ | ✅ | ✅ |
| `in(values...)` | ✅ | ✅ | ✅ | ✅ |
| `in(Collection)` | ✅ | ✅ | ✅ | ✅ |
| `notIn(values...)` | ✅ | ✅ | ✅ | ✅ |
| `notIn(Collection)` | ✅ | ✅ | ✅ | ✅ |
| `greaterThan(value)` / `gt(value)` | ❌ | ✅ | ❌ | ✅ |
| `greaterThanOrEqual(value)` / `ge(value)` | ❌ | ✅ | ❌ | ✅ |
| `lessThan(value)` / `lt(value)` | ❌ | ✅ | ❌ | ✅ |
| `lessThanOrEqual(value)` / `le(value)` | ❌ | ✅ | ❌ | ✅ |
| `between(low, high)` | ❌ | ✅ | ❌ | ✅ |
| `notBetween(low, high)` | ❌ | ✅ | ❌ | ✅ |

---

### Chain Control Methods

| Method | `TypedQuery` | `WhereChainBuilder` | `ChainResult` | `OrderByBuilder` | `OrderByField` |
|--------|:------------:|:-------------------:|:-------------:|:----------------:|:--------------:|
| `and()` | ❌ | ❌ | ✅ | ❌ | ❌ |
| `or()` | ❌ | ❌ | ✅ | ❌ | ❌ |
| `and(Condition)` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `or(Condition)` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `begin()` | ✅ | ❌ | ✅ | ❌ | ❌ |
| `toCondition()` | ❌ | ❌ | ✅ | ❌ | ❌ |
| `asc()` | ❌ | ❌ | ❌ | ❌ | ✅ |
| `desc()` | ❌ | ❌ | ❌ | ❌ | ✅ |

---

### ORDER BY & LIMIT Methods

| Method | `TypedQuery` | `WhereChainBuilder` | `ChainResult` | `OrderByBuilder` |
|--------|:------------:|:-------------------:|:-------------:|:----------------:|
| `orderBy(field)` | ✅ | ✅ | ✅ | ❌ |
| `orderByAsc(field)` | ✅ | ❌ | ❌ | ❌ |
| `orderByDesc(field)` | ✅ | ✅ | ✅ | ❌ |
| `addOrderBy(String)` | ✅ | ❌ | ❌ | ❌ |
| `limit(rowCount)` | ✅ | ✅ | ✅ | ✅ |
| `limit(offset, rowCount)` | ✅ | ✅ | ✅ | ✅ |

---

### Execution (Terminal) Methods

| Method | `TypedQuery` | `WhereChainBuilder` | `ChainResult` | `OrderByBuilder` |
|--------|:------------:|:-------------------:|:-------------:|:----------------:|
| `list(Connection)` | ✅ | ✅ | ✅ | ✅ |
| `list(DataSource)` | ✅ | ❌ | ❌ | ❌ |
| `first(Connection)` | ✅ | ✅ | ✅ | ✅ |
| `first(DataSource)` | ✅ | ❌ | ❌ | ❌ |
| `stream(Connection, Consumer)` | ✅ | ✅ | ✅ | ✅ |
| `stream(DataSource, Consumer)` | ✅ | ❌ | ❌ | ❌ |
| `toSql()` | ✅ | ✅ | ✅ | ✅ |

---

### Class Flow Diagram

```
TypedQuery<E,Q>
    │
    ├── where(QueryField)      ──► WhereClause<E,T,Q>      ──► Q (back to query)
    ├── where(ComparableField) ──► ComparableWhereClause   ──► Q (back to query)
    ├── where(Condition)       ──► Q (back to query)
    │
    ├── where()                ──► WhereChainBuilder       ──► ChainResult
    │   (generated)                 │                          ├── and() ──► WhereChainBuilder
    │                               │                          ├── or()  ──► WhereChainBuilder
    │                               │                          └── begin() ──► SubClauseBuilder
    │                               └── (field access returns ChainField/ComparableChainField)
    │
    ├── begin()                ──► SubClauseBuilder        ──► end() ──► Q
    │
    ├── orderBy()              ──► OrderByBuilder
    │   (generated)                 └── (field).asc/desc() ──► OrderByBuilder
    │
    └── orderBy(field)         ──► Q (back to query)
```

The `WhereClause`/`ComparableWhereClause` style (`query.where(field).is(value)`) returns directly to the query, while the `WhereChainBuilder` style (`query.where().field.eq(value)`) returns a `ChainResult` that allows `.and()/.or()` chaining.

User: can you explain to me with a simple usage example the difference between ChainField and WhereClause

GitHub Copilot: ## `WhereClause` vs `ChainField` - Usage Comparison

Both provide the same filtering operations, but they're used in **different fluent API styles**:

### `WhereClause` Style — Field-as-parameter

```java
// You pass the field as a parameter to where()
new EmployeeQuery()
    .where(lastName).is("Smith")      // WhereClause<E, String, Q>
    .where(salary).greaterThan(50000) // ComparableWhereClause<E, Integer, Q>
    .list(connection);
```

- Each `.where(field).condition()` call returns **directly to the query** (`Q`)
- Conditions are implicitly combined with `AND`
- No explicit `.and()` or `.or()` chaining between conditions
- Simpler for basic sequential filtering

---

### `ChainField` Style — Field-as-property

```java
// Fields are accessed as properties on a builder object
new EmployeeQuery()
    .where()                          // Returns WhereChainBuilder
        .lastName.is("Smith")         // ChainField → returns ChainResult
        .or()                         // Returns back to WhereChainBuilder
        .lastName.is("Johnson")       // ChainField → returns ChainResult
    .list(connection);
```

- Fields like `lastName`, `salary` are **properties** on the generated `WhereChainBuilder`
- Each condition returns a `ChainResult`, which provides `.and()` / `.or()` for explicit chaining
- Allows building **complex boolean logic** (OR conditions, grouping)
- More verbose but more powerful

---

### Side-by-Side Comparison

| Aspect | `WhereClause` | `ChainField` |
|--------|---------------|--------------|
| Entry point | `query.where(field)` | `query.where().field` |
| Returns | Query (`Q`) | `ChainResult` |
| Chaining | Implicit AND | Explicit `.and()` / `.or()` |
| OR support | Via `Condition` objects | Built-in with `.or()` |
| Best for | Simple filters | Complex boolean logic |

---

### Practical Example: Same Query, Two Styles

**Goal:** Find employees named "Smith" OR "Johnson" with salary > 50000

```java
// WhereClause style - requires pre-built Condition
Condition<Employee> nameFilter = lastName.eq("Smith").or(lastName.eq("Johnson"));
new EmployeeQuery()
    .where(nameFilter)
    .where(salary).greaterThan(50000)
    .list(connection);

// ChainField style - inline OR
new EmployeeQuery()
    .where()
        .lastName.is("Smith")
        .or()
        .lastName.is("Johnson")
    .and(salary.gt(50000))   // terminal method accepting Condition
    .list(connection);
```

The `ChainField` approach lives inside `AbstractConditionBuilder` and is used by the generated `WhereChainBuilder` subclasses to expose entity fields directly as chainable properties.

User: can you create a setup to make the fluent API unit testable (no database involved, no annotation processing needs to run) and test the case: (a and b) or (c and d)

GitHub Copilot: Read [](file:///Users/martijn/git/pojoquery/src/test/java/org/pojoquery)

Read [](file:///Users/martijn/git/pojoquery/src/test/java/org/pojoquery/TestBasics.java#1-1), lines 1 to 50

Read [](file:///Users/martijn/git/pojoquery/src/test/java/examples/typedquery)

Read [](file:///Users/martijn/git/pojoquery/src/test/java/examples/typedquery/TestTypedQueryConditions.java#1-1), lines 1 to 150

Read [](file:///Users/martijn/git/pojoquery/src/test/java/examples/typedquery/TestTypedQueryWhereChain.java#1-1), lines 1 to 100

Now let me look at how the generated query classes work: 

Searched for files matching `**/EmployeeQuery.java`, no matches

Searched for text `class EmployeeQuery extends TypedQuery`, no results

Searched for text `class EmployeeQuery extends TypedQuery`, 1 result

Read [](file:///Users/martijn/git/pojoquery/target/generated-test-sources/test-annotations/examples/typedquery/EmployeeQuery.java)

Read [](file:///Users/martijn/git/pojoquery/target/generated-test-sources/test-annotations/examples/typedquery/EmployeeQueryWhere.java)

Read [](file:///Users/martijn/git/pojoquery/target/generated-test-sources/test-annotations/examples/typedquery/Employee_.java)

Now I have a good understanding of the structure. I'll create a unit test that tests the fluent API without needing a database or annotation processing by manually creating the necessary classes. 

Created [](file:///Users/martijn/git/pojoquery/src/test/java/org/pojoquery/typedquery/TestFluentApiConditions.java)

Read [](file:///Users/martijn/git/pojoquery/src/main/java/org/pojoquery/typedquery/SubClauseWhereBuilder.java)

I need to update the test to use the correct signature for `SubClauseWhereBuilder`: 

Using "Replace String in File"


```

```
Now let's run the tests to verify they work: 

6/9 tests passed (66.7%)

The tests are failing because the SQL uses quoted identifiers (`"person".lastName`) instead of the placeholder format (`person.lastName`). Let me fix the assertions: 

Using "Replace String in File"


```

```


Using "Replace String in File"


```

```
Now let's remove the unused `normalize` method since we no longer need it: 

Using "Replace String in File"


```

```


9/9 tests passed (100%)

All tests pass. Let me show you the final test file: 

Read [](file:///Users/martijn/git/pojoquery/src/test/java/org/pojoquery/typedquery/TestFluentApiConditions.java#1-1), lines 1 to 280

I've created a test setup at TestFluentApiConditions.java that allows you to unit test the fluent API without a database or annotation processing.

## Key Components

### 1. **Manual Entity & Field References** (lines 29-52)
```java
public static class Person { ... }       // Simple POJO
public static final class Person_ { ... } // Field references (normally generated)
```

### 2. **Manual Query Implementation** (lines 60-147)
- `PersonQuery` — extends `TypedQuery`, provides `where()` and `begin()`
- `PersonQueryWhere` — extends `WhereChainBuilder`, exposes field accessors
- `PersonQuerySubClauseBuilder` — for `begin()/end()` grouping
- `PersonQuerySubClauseWhere` — for conditions inside sub-clauses

### 3. **The (a AND b) OR (c AND d) Test** (lines 207-231)

```java
@Test
void testConditionApi_aAndB_Or_cAndD() {
    // (firstName = 'John' AND lastName = 'Smith') OR (firstName = 'Jane' AND lastName = 'Doe')
    
    Condition<Person> group1 = Person_.firstName.eq("John")
        .and(Person_.lastName.eq("Smith"));
    
    Condition<Person> group2 = Person_.firstName.eq("Jane")
        .and(Person_.lastName.eq("Doe"));
    
    Condition<Person> combined = group1.or(group2);

    String sql = new PersonQuery()
        .where(combined)
        .toSql();
    
    // Assertions verify AND, OR, parentheses...
}
```

### 4. **Same with Fluent Chain API** (lines 233-251)
```java
String sql = new PersonQuery()
    .where()
        .firstName.eq("John")
        .and()
        .lastName.eq("Smith")
    .or()
        .firstName.eq("Jane")
        .and()
        .lastName.eq("Doe")
    .toSql();
```

All 9 tests pass without needing a database or annotation processor.

Made changes.
