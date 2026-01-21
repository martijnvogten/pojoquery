# PojoQuery vs JPA/Hibernate Comparison

This document captures a detailed comparison between PojoQuery and conventional ORMs (JPA/Hibernate), including honest assessments of where each approach excels.

## Feature Matrix

| Feature | PojoQuery | Conventional ORM (Hibernate/JPA) | Winner |
|---------|-----------|----------------------------------|--------|
| Query Predictability | Single SQL per query, always visible | Complex heuristics, lazy loading magic | PojoQuery |
| N+1 Query Prevention | Impossible by design (eager only) | Requires careful configuration | PojoQuery |
| Learning Curve | Small API surface, JDBC knowledge sufficient | Large, complex API + JPQL/Criteria | PojoQuery |
| Debugging SQL | Easy - explicit and predictable | Hard - generated dynamically | PojoQuery |
| No Session/EntityManager | Stateless, no lifecycle issues | Must manage session scope carefully | PojoQuery |
| Type Safety | POJO structure = query shape | JPQL strings, Criteria API | PojoQuery |
| Lazy Loading | Not supported | Full support with proxies | JPA |
| Second-Level Cache | None built-in | Ehcache, Infinispan, etc. | JPA* |
| Batch Insert/Update | Individual operations only | Bulk operations, batch sizing | JPA |
| Circular References | Explicitly forbidden | Handled (with care) | JPA |
| Dirty Tracking | Manual updates only | Automatic change detection | JPA |
| Cascade Operations | Manual | CascadeType.ALL, orphanRemoval | JPA |
| Entity Lifecycle Hooks | None | @PrePersist, @PostLoad, etc. | JPA |
| Ecosystem/Tooling | Small, single-maintainer | Massive ecosystem, IDE support | JPA |
| Framework Integration | Manual wiring | Spring Boot auto-config | JPA |
| Optimistic Locking | Via HasVersion interface | @Version | Tie |
| Transactions | DB.runInTransaction() | @Transactional, JTA | Tie |
| Schema Generation | Basic DDL + migrations | Full DDL, Flyway/Liquibase integration | Tie |

*See detailed analysis below

---

## Detailed Analysis of JPA "Advantages"

### 1. Large Object Graphs / Lazy Loading

**Initial claim**: JPA lazy loading is essential when you need to load a Customer without their 10,000 Orders.

**Reality**: In PojoQuery, you define the query shape with your POJO:

```java
// When you need orders
class CustomerWithOrders {
    @Id Long id;
    String name;
    List<Order> orders;
}

// When you don't
class Customer {
    @Id Long id;
    String name;
}

// Use the appropriate one - no orders fetched
PojoQuery.build(Customer.class).execute(ds);
```

PojoQuery doesn't "fetch everything" - it fetches what your POJO defines.

**JPA lazy loading problems**:
- `LazyInitializationException` outside transaction/session
- N+1 queries when iterating
- Unpredictable performance
- Hidden database calls from simple getters

Many teams disable lazy loading and use `JOIN FETCH` - which is what PojoQuery does by default.

---

### 2. Batch Processing

**Claim**: JPA batch operations are significantly faster than individual inserts.

**Reality**: This is a legitimate gap.

```java
// JPA with batching (hibernate.jdbc.batch_size=50)
for (int i = 0; i < 10000; i++) {
    entityManager.persist(new Order(...));
    if (i % 50 == 0) {
        entityManager.flush();
        entityManager.clear();
    }
}

// PojoQuery - individual round-trips
for (Order order : orders) {
    PojoQuery.insert(conn, order);  // 10,000 round-trips
}
```

**However**:
- JPA batching is just JDBC batching - you can do it without an ORM
- For truly high volume, both approaches use database-specific bulk loaders (COPY, LOAD DATA)
- For typical CRUD apps doing a few inserts at a time, this is irrelevant

---

### 3. Circular References

**Claim**: PojoQuery forbids cycles like `Employee.manager -> Employee`.

**Why PojoQuery forbids it**: The POJO defines query shape, so a cycle would generate infinite JOINs.

**Workarounds**:

```java
// Option 1: Just the foreign key
class Employee {
    @Id Long id;
    String name;
    Long managerId;  // FK only
}

// Option 2: Fixed depth with separate classes
class Employee {
    @Id Long id;
    String name;
    EmployeeRef manager;
}

class EmployeeRef {
    @Id Long id;
    String name;
    // No further nesting - can use inheritance to reduce duplication
}

// Option 3: Fetch flat, build tree in code
List<Employee> all = PojoQuery.build(Employee.class).execute(ds);
Map<Long, Employee> byId = all.stream().collect(toMap(e -> e.id, e -> e));
```

**JPA's approach isn't better**:
```java
employee.getManager().getManager().getManager();  // 3 hidden queries!
```

For deep hierarchies, proper solution is recursive CTEs - raw SQL in both cases.

---

### 4. Second-Level Cache

**Claim**: JPA provides caching out of the box.

**Reality**: JPA L2 cache requires:
- Cache provider dependency (Ehcache, etc.)
- Configuration for regions, eviction, sizing
- Complex invalidation logic

**PojoQuery equivalent** (~5 lines):
```java
Cache<String, Country> cache = Caffeine.newBuilder()
    .expireAfterWrite(1, TimeUnit.HOURS)
    .maximumSize(1000)
    .build();

Country getCountry(String code) {
    return cache.get(code, k ->
        PojoQuery.build(Country.class)
            .addWhere("{country}.code = ?", k)
            .execute(ds)
            .stream().findFirst().orElse(null));
}
```

Application-level caching is explicit, simple, and works with any data access approach. Most production apps outgrow ORM caching and use Redis/Memcached anyway.

---

### 5. Dynamic Projections

**Claim**: JPA can fetch different "views" without creating new classes.

**JPA approaches**:

```java
// Object[] - no type safety
List<Object[]> results = em.createQuery(
    "SELECT c.id, c.email FROM Customer c").getResultList();
Long id = (Long) results.get(0)[0];  // Hope you remembered the order

// Tuple - still fragile
Tuple row = results.get(0);
Long id = row.get(0, Long.class);  // Runtime type checking

// Constructor expression - needs DTO class anyway!
"SELECT NEW com.example.CustomerEmail(c.id, c.email) FROM Customer c"
```

**PojoQuery**:
```java
class CustomerEmail { Long id; String email; }
```

That's simpler, with full IDE support, compile-time checking, and refactoring support.

---

## Legitimate JPA Advantages

These are real reasons to choose JPA over PojoQuery:

### Ecosystem & Maturity
- 20+ years of production use
- Thousands of Stack Overflow answers
- Training materials, consultants, contractors assume JPA

### Standardization & Hiring
- JPA is a specification - swap implementations if needed
- New hires probably know JPA; they've never heard of PojoQuery

### Framework Integration
- Spring Boot auto-configures everything
- `@Transactional` just works
- Spring Data JPA generates repositories from interfaces

### Tooling
- IntelliJ validates JPQL at edit time
- Database schema validation at startup

### Entity Lifecycle Hooks
```java
@PrePersist void onCreate() { this.createdAt = Instant.now(); }
@PreUpdate void onUpdate() { this.updatedAt = Instant.now(); }
```

### Dirty Checking
```java
Customer c = em.find(Customer.class, 1L);
c.setEmail("new@email.com");
// Hibernate automatically generates: UPDATE customer SET email = ? WHERE id = ?
```

### First-Level Cache (Identity Guarantee)
```java
Customer c1 = em.find(Customer.class, 1L);
Customer c2 = em.find(Customer.class, 1L);
assert c1 == c2;  // Same instance guaranteed
```

### Additional Features
- Hibernate Envers (audit logging)
- Hibernate Validator integration
- Multi-tenancy support
- Criteria API for type-safe dynamic queries

---

## JPA Criteria API

Type-safe dynamic queries, designed in 2009 (before Java 8):

```java
CriteriaBuilder cb = em.getCriteriaBuilder();
CriteriaQuery<Customer> query = cb.createQuery(Customer.class);
Root<Customer> customer = query.from(Customer.class);

query.select(customer)
     .where(cb.and(
         cb.equal(customer.get("status"), "ACTIVE"),
         cb.greaterThan(customer.get("balance"), 1000)
     ))
     .orderBy(cb.desc(customer.get("createdAt")));
```

With metamodel (generated `Customer_` class):
```java
query.where(cb.equal(customer.get(Customer_.status), "ACTIVE"));
```

The metamodel class:
```java
@StaticMetamodel(Customer.class)
public class Customer_ {
    public static volatile SingularAttribute<Customer, Long> id;
    public static volatile SingularAttribute<Customer, String> email;
    public static volatile SingularAttribute<Customer, String> status;
    // ...
}
```

**Why not method references?** JPA 2.0 was finalized in 2009, Java 8 lambdas came in 2014. The API predates the feature.

---

## Potential PojoQuery Enhancement: Type-Safe Queries

With code generation, PojoQuery could offer type-safe queries better than JPA.

### Current PojoQuery
```java
PojoQuery.build(Customer.class)
    .addWhere("{customer}.email = ?", email)
    .addWhere("{customer}.balance > ?", 1000)
    .addOrderBy("{customer}.createdAt DESC")
    .execute(ds);
```

### Proposed: Generated Query Builder

```java
// Generated class
public class Customer$ extends PojoQuery<Customer> {
    public final StringField email = new StringField("customer", "email");
    public final NumberField<Integer> balance = new NumberField("customer", "balance");
    public final DateField<Instant> createdAt = new DateField("customer", "createdAt");
    public final Address$ address = new Address$();  // nested objects

    public Customer$() {
        super(Customer.class);
    }
}

// Usage
var q = new Customer$();
q.where(q.email.eq(email))
 .where(q.balance.gt(1000))
 .orderBy(q.createdAt.desc())
 .execute(ds);

// Nested objects - joins handled automatically
var q = new CustomerWithAddress$();
q.where(q.address.city.eq("Amsterdam"))
 .execute(ds);
```

### Comparison

| Style | Code |
|-------|------|
| Current PojoQuery | `.addWhere("{customer}.email = ?", email)` |
| Proposed PojoQuery | `q.where(q.email.eq(email))` |
| JPA Criteria | `cb.equal(root.get(Customer_.email), email)` |

The `q.field` approach references the generated class once when creating the query, then uses `q.` prefix for everything - cleaner than JPA's verbose CriteriaBuilder pattern.

---

## Conclusion

### Choose PojoQuery when:
- You value simplicity and predictability
- You want to see exactly what SQL runs
- You're building microservices or read-heavy apps
- Your team has been burned by ORM complexity
- You're a solo developer or small team

### Choose JPA/Hibernate when:
- You need to hire Java developers easily
- You're integrating with Spring ecosystem
- You need advanced features (Envers, lifecycle hooks)
- Your organization has existing JPA infrastructure
- Risk aversion is important (industry standard)

The technical differences are smaller than they appear. The main reasons to choose JPA are ecosystem, hiring, and organizational factors - not technical superiority.
