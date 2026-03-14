package org.pojoquery;

import java.util.List;
import java.util.function.Function;

import org.junit.Test;
import org.pojoquery.pipeline.querytree.TableInfo;
import org.pojoquery.typemodel.FieldModel;

public class TestAlternativeStructureForQueryTree {

	/**
	 * This test explores a clearer structure for the querytree
	 * strictly based on java properties.
	 * 
	 * An entity is a class mapped to a table.
	 * 
	 * Each java property transforms into one of:
	 * - ScalarValue: a primitive value or string (non-entity), a single column or SQL expression
	 * - EntityReference: a reference to another entity (many-to-one or one-to-one)
	 * - EntityCollection: a collection of entities (one-to-many)
	 * - ValueCollection: a collection or map of scalar values (non-entities) from a one-to-many join
	 * - JoinTableEntityCollection: a collection of entities through a join table (many-to-many)
	 * - JoinTableValueCollection: a collection of primitive values (non-entities) through a join table (many-to-many)
	 * - Embedding: an embedded entity
	 * - Transient: a field is not queried (not mapped to any column or join)
	 * 
	 * Single Table Inheritance adds Embeddings for superclasses and subclasses
	 * 
	 * Table per Subclass adds an EntityReference for its direct superclass and direct subclasses
	 * 
	 * Each of the non-scalar types (EntityReference, EntityCollection, JoinTableEntityCollection, Embedding) corresponds to a TableNode in the query tree, and can have child nodes for their properties.
	 */

	public sealed interface QueryNode permits TableNode, ScalarNode {
	}

	public sealed interface ScalarNode extends QueryNode permits ScalarValue, ValueCollection, JoinTableValueCollection {
	}

	public sealed interface TableNode extends QueryNode permits RootNode, EntityReference, EntityCollection, JoinTableEntityCollection, Embedding, TPSSubClassNode, TPSSuperClassNode {
		String alias();
		Class<?> type();
		List<QueryNode> children();
	}

	public non-sealed interface Embedding extends TableNode {
	}

	public non-sealed interface TPSSubClassNode extends TableNode, JoinOne {
	}

	public non-sealed interface TPSSuperClassNode extends TableNode, JoinOne {
	}

	// STINode = Single Table Inheritance node, represents a class in the STI hierarchy. It has an alias and type, but shares the same table as its parent (so no join).
	public interface STINode extends Embedding {
	}

	public interface PropertyNode {
		String columnName();
		FieldModel field();
	}

	public interface SimpleExpression {
	}

	public non-sealed interface RootNode extends TableNode {
	}

	public interface EmbeddedEntity extends Embedding, PropertyNode {
	}

	public non-sealed interface ScalarValue extends ScalarNode {
		String columnName();
		SimpleExpression expression();
	}

	public sealed interface JoinMany {
		/** foreign key column in the child table
		 * references id column in parent table.
		 */
		String foreignKeyColumn();
	}

	public sealed interface JoinOne extends PropertyNode {

	}

	public non-sealed interface ValueCollection extends ScalarNode, JoinMany {
		String columnName();
		SimpleExpression expression();
	}

	public non-sealed interface EntityReference extends TableNode, JoinOne {
		String columnName();
	}

	public non-sealed interface EntityCollection extends TableNode, JoinMany {
	}

	public interface JoinTableInfo {
		TableInfo joinTable();
		String joinTableAlias();
		String parentFkColumn();
		String childFkColumn();
	}

	public interface JoinTableJoin {
		JoinTableInfo joinTableInfo();
		SimpleExpression joinConditionToParent();
		SimpleExpression joinConditionToChild();
	}

	public non-sealed interface JoinTableEntityCollection extends TableNode, JoinTableJoin {
	}

	public non-sealed interface JoinTableValueCollection extends ScalarNode, JoinTableJoin {
		String[] fieldsToSelect();
		Function<Object[], Object> valueMapper();
	}

// EntityReference
// EntityCollection
// ValueCollection = EntityCollection with projection
// JoinTableEntityCollection
// JoinTableValueCollection = JoinTableEntityCollection with projection
// Embedding

// Superclasses zijn EntityReference of Embedded

// EntityReference heeft een field en columnName







	@Test
	public void testAlternativeStructure() {
		// This test just checks that we can build a query tree with an alternative structure (using a different class for the nodes).
		// The actual correctness of the query tree is not checked here, but it should be covered by other tests.
		// PojoQuery.build(TestBasics.ArticleDetail.class, AlternativeQueryTreeNode.class);
	}
}
