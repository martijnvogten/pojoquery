package examples;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.pojoquery.integrationtest.DbContextExtension;

import examples.blog.ArticleDetailExample;
import examples.blog.ArticleListViewExample;
import examples.bookstore.BookstoreExample;
import examples.docs.BasicCrudExample;
import examples.docs.CustomTypeMappingExample;
import examples.docs.EmbeddedExample;
import examples.docs.FilteredRelationshipExample;
import examples.docs.GettingStartedExample;
import examples.docs.JpaAnnotationsExample;
import examples.docs.TablePerSubclassInheritanceExample;
import examples.events.EventsExample;
import examples.inheritance.InheritanceExample;
import examples.typedquery.RelationshipQueryExample;
import examples.typedquery.TypedQueryExample;
import examples.users.UsersExample;

/**
 * Runs all examples as tests to ensure they don't throw exceptions.
 * Each example is still independently runnable via its main() method.
 */
@ExtendWith(DbContextExtension.class)
public class TestAllExamples {

	@Test
	void bookstoreExample() {
		BookstoreExample.main(new String[0]);
	}

	@Test
	void articleDetailExample() {
		ArticleDetailExample.main(new String[0]);
	}

	@Test
	void articleListViewExample() {
		ArticleListViewExample.main(new String[0]);
	}

	@Test
	void basicCrudExample() {
		BasicCrudExample.main(new String[0]);
	}

	@Test
	void customTypeMappingExample() {
		CustomTypeMappingExample.main(new String[0]);
	}

	@Test
	void embeddedExample() {
		EmbeddedExample.main(new String[0]);
	}

	@Test
	void filteredRelationshipExample() {
		FilteredRelationshipExample.main(new String[0]);
	}

	@Test
	void gettingStartedExample() {
		GettingStartedExample.main(new String[0]);
	}

	@Test
	void jpaAnnotationsExample() {
		JpaAnnotationsExample.main(new String[0]);
	}

	@Test
	void tablePerSubclassInheritanceExample() {
		TablePerSubclassInheritanceExample.main(new String[0]);
	}

	@Test
	void eventsExample() {
		EventsExample.main(new String[0]);
	}

	@Test
	void inheritanceExample() {
		InheritanceExample.main(new String[0]);
	}

	@Test
	void usersExample() throws Exception {
		UsersExample.main(new String[0]);
	}

	@Test
	void typedQueryExample() {
		TypedQueryExample.main(new String[0]);
	}

	@Test
	void relationshipQueryExample() {
		RelationshipQueryExample.main(new String[0]);
	}
}
