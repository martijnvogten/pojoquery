package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.Cascade;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Tests for CascadingUpdater with Owner/Pet/Visit domain.
 */
public class TestCascadingUpdaterPets {

    private DataSource dataSource;

    // === Owner/Pet entities ===

    @Table("owners")
    public static class Owner {
        @Id public Long id;
        public String name;
        
        @Cascade
        @Link(foreignlinkfield = "owner_id")
        public List<Pet> pets;
        
        public Owner() { this.pets = new ArrayList<>(); }
        public Owner(String name) { this(); this.name = name; }
    }

    @Table("pets")
    public static class Pet {
        @Id public Long id;
        public String petName;
        
        // Note: Pet does NOT have an owner or owner_id property
        
        public Pet() {}
        public Pet(String petName) { this.petName = petName; }
    }

    // === Two-level cascading: Owner -> Pet -> Visit ===

    @Table("visit")
    public static class Visit {
        @Id public Long id;
        public LocalDate visitDate;
        public String description;
        
        // Note: Visit does NOT have a Pet property
        
        public Visit() {}
        public Visit(LocalDate visitDate, String description) {
            this.visitDate = visitDate;
            this.description = description;
        }
    }

    @Table("pets")
    public static class PetWithVisits {
        @Id public Long id;
        public String petName;
        
        // Pet owns visits collection - Visit has no pet reference
        @Cascade
        @Link(foreignlinkfield = "pet_id")
        public List<Visit> visits;
        
        public PetWithVisits() { this.visits = new ArrayList<>(); }
        public PetWithVisits(String petName) { this(); this.petName = petName; }
    }

    @Table("owners")
    public static class OwnerWithPetsWithVisits {
        @Id public Long id;
        public String name;
        
        @Cascade
        @Link(foreignlinkfield = "owner_id")
        public List<PetWithVisits> pets;
        
        public OwnerWithPetsWithVisits() { this.pets = new ArrayList<>(); }
        public OwnerWithPetsWithVisits(String name) { this(); this.name = name; }
    }

    @BeforeEach
    void setup() {
        DbContext.setDefault(DbContext.forDialect(Dialect.HSQLDB));
        
        // Create unique in-memory database
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:cascade_pets_test_" + System.nanoTime());
        ds.setUser("SA");
        ds.setPassword("");
        dataSource = ds;
    }

    @Test
    public void testCascadeWithForeignLinkFieldOnOwnedCollection() {
        // Test @Cascade with @Link(foreignlinkfield="owner_id") where Pet has no owner reference
        // SchemaGenerator creates pets table with owner_id FK column from @Link annotation
        SchemaGenerator.createTables(dataSource, Owner.class, Pet.class);
        
        DB.withConnection(dataSource, (Connection c) -> {
            Owner owner = new Owner("John");
            owner.pets.add(new Pet("Fluffy"));
            owner.pets.add(new Pet("Rex"));
            PojoQuery.insert(c, owner);
            
            // Verify pets were inserted with correct owner_id
            List<Pet> pets = PojoQuery.build(Pet.class)
                .addWhere("{pets.owner_id} = ?", owner.id)
                .execute(c);
            assertEquals(2, pets.size());
            
            // Test update: add a pet, remove one
            owner.pets.add(new Pet("Spot"));
            owner.pets.removeIf(p -> "Fluffy".equals(p.petName));
            PojoQuery.update(c, owner);
            
            // Verify: should have Rex and Spot
            List<Pet> updatedPets = PojoQuery.build(Pet.class)
                .addWhere("{pets.owner_id} = ?", owner.id)
                .execute(c);
            assertEquals(2, updatedPets.size());
            assertTrue(updatedPets.stream().anyMatch(p -> "Rex".equals(p.petName)));
            assertTrue(updatedPets.stream().anyMatch(p -> "Spot".equals(p.petName)));
            assertFalse(updatedPets.stream().anyMatch(p -> "Fluffy".equals(p.petName)));
        });
    }

    @Test
    public void testTwoLevelCascade_OwnerPetVisit() {
        // Test two-level cascading: Owner -> Pet -> Visit
        // Both Pet and Visit have no back-reference to their parent
        SchemaGenerator.createTables(dataSource, OwnerWithPetsWithVisits.class, PetWithVisits.class, Visit.class);
        
        DB.withConnection(dataSource, (Connection c) -> {
            // Create owner with pets that have visits
            OwnerWithPetsWithVisits owner = new OwnerWithPetsWithVisits("Alice");
            
            PetWithVisits fluffy = new PetWithVisits("Fluffy");
            fluffy.visits.add(new Visit(LocalDate.of(2026, 1, 15), "Annual checkup"));
            fluffy.visits.add(new Visit(LocalDate.of(2026, 2, 10), "Vaccination"));
            
            PetWithVisits rex = new PetWithVisits("Rex");
            rex.visits.add(new Visit(LocalDate.of(2026, 1, 20), "Dental cleaning"));
            
            owner.pets.add(fluffy);
            owner.pets.add(rex);
            
            // Insert with cascade (should insert owner, pets, and visits)
            PojoQuery.insert(c, owner);
            
            assertNotNull(owner.id);
            assertNotNull(fluffy.id);
            assertNotNull(rex.id);
            
            // Verify by querying Owner and navigating through collections
            OwnerWithPetsWithVisits loaded = PojoQuery.build(OwnerWithPetsWithVisits.class).findById(c, owner.id).orElseThrow();
            assertEquals(2, loaded.pets.size());
            
			{
				// Verify Fluffy's visits
				List<Visit> fluffyVisits = loaded.pets.stream().filter(p -> "Fluffy".equals(p.petName)).findFirst().orElseThrow().visits;
				assertEquals(2, fluffyVisits.size());
				assertTrue(fluffyVisits.stream().anyMatch(v -> "Annual checkup".equals(v.description)));
				assertTrue(fluffyVisits.stream().anyMatch(v -> "Vaccination".equals(v.description)));
				
				// Verify Rex's visits
				List<Visit> rexVisits = loaded.pets.stream().filter(p -> "Rex".equals(p.petName)).findFirst().orElseThrow().visits;
				assertEquals(1, rexVisits.size());
				assertEquals("Dental cleaning", rexVisits.get(0).description);
			}

            // Test update: modify visits at second level
            // - Add a visit to Rex
            // - Remove a visit from Fluffy
            // - Add a new pet with visits
            rex.visits.add(new Visit(LocalDate.of(2026, 3, 5), "Follow-up"));
            fluffy.visits.removeIf(v -> "Vaccination".equals(v.description));
            
            PetWithVisits spot = new PetWithVisits("Spot");
            spot.visits.add(new Visit(LocalDate.of(2026, 2, 28), "Initial examination"));
            owner.pets.add(spot);
            
            PojoQuery.update(c, owner);
            
            // Verify by querying Owner again
            OwnerWithPetsWithVisits updated = PojoQuery.build(OwnerWithPetsWithVisits.class).findById(c, owner.id).orElseThrow();
            assertEquals(3, updated.pets.size());
            
			{
				// Verify Fluffy now has 1 visit
				List<Visit> flufflyVisits = updated.pets.stream().filter(p -> "Fluffy".equals(p.petName)).findFirst().orElseThrow().visits;
				assertEquals(1, flufflyVisits.size());
				assertEquals("Annual checkup", flufflyVisits.get(0).description);
				
				// Verify Rex now has 2 visits
				List<Visit> rexVisits = updated.pets.stream().filter(p -> "Rex".equals(p.petName)).findFirst().orElseThrow().visits;
				assertEquals(2, rexVisits.size());
				assertTrue(rexVisits.stream().anyMatch(v -> "Dental cleaning".equals(v.description)));
				assertTrue(rexVisits.stream().anyMatch(v -> "Follow-up".equals(v.description)));
				
				// Verify Spot was inserted with visit
				assertNotNull(spot.id);
				List<Visit> spotVisits = updated.pets.stream().filter(p -> "Spot".equals(p.petName)).findFirst().orElseThrow().visits;
				assertEquals(1, spotVisits.size());
				assertEquals("Initial examination", spotVisits.get(0).description);
			}

            // Test removing a pet (should also remove its visits)
            Long rexId = rex.id;
            owner.pets.removeIf(p -> "Rex".equals(p.petName));
            PojoQuery.update(c, owner);
            
            // Verify by querying Owner - Rex should be gone
            List<PetWithVisits> pets = PojoQuery.build(OwnerWithPetsWithVisits.class).findById(c, owner.id).orElseThrow().pets;
            assertEquals(2, pets.size());
            assertTrue(pets.stream().anyMatch(p -> "Fluffy".equals(p.petName)));
            assertTrue(pets.stream().anyMatch(p -> "Spot".equals(p.petName)));
            assertFalse(pets.stream().anyMatch(p -> "Rex".equals(p.petName)));
            
            // Verify Rex's visits are deleted (orphan removal) - query Visit directly to confirm deletion
            List<Visit> deletedRexVisits = PojoQuery.build(Visit.class)
                .addWhere("{visit.pet_id} = ?", rexId)
                .execute(c);
            assertEquals(0, deletedRexVisits.size());
        });
    }
}
