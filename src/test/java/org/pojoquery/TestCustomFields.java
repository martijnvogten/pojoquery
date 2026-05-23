package org.pojoquery;

import static org.pojoquery.TestUtils.norm;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.Table;
import org.pojoquery.pipeline.AQTRowProcessor;
import org.pojoquery.pipeline.AQTTransformer;
import org.pojoquery.pipeline.AbstractQueryTree;
import org.pojoquery.pipeline.AbstractQueryTree.CustomQueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.EmptyFieldNode;
import org.pojoquery.pipeline.AbstractQueryTree.QueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.TransformPipeline;
import org.pojoquery.pipeline.TransformPipeline.RecursiveTransform;
import org.pojoquery.pipeline.Transforms;
import org.pojoquery.pipeline.Transforms.AddDeclaredFields;

public class TestCustomFields {

	@BeforeEach
	public void setup() {
		DbContext.setDefault(DbContext.forDialect(Dialect.MYSQL));
	}
	

	@Table("user")
	static class User {
		Long id;
		String email;
		
		@CustomFields
		Map<String,Object> customFields;
		
		public Object getCustomValue(String field) {
			return customFields.get(field);
		}
	}

	public static class MyCustomTransform implements RecursiveTransform {

		@Override
		public QueryNode transform(QueryNode node) {
			return Transforms.transformChildren(
				node, 
				child -> child instanceof EmptyFieldNode emptyFieldNode && 
					emptyFieldNode.field().hasAnnotation(CustomFields.class), 
				(TableNode tableNode, EmptyFieldNode child) -> {
					if (tableNode.type().isSameType(User.class)) {
						CustomQueryNode customNode = new AbstractQueryTree.CustomQueryNode() {
							@Override
							public void applyToSqlQuery(TableNode parentNode, AQTTransformer.PlainQueryBuilder sqlQuery) {
								sqlQuery.addField(new SqlExpression("{" + parentNode.alias() + ".custom_linkedInUrl}"), parentNode.alias() + ".linkedInUrl");
							}

							@Override
							public void applyRowResultToEntity(AbstractQueryTree.TableNode parentNode, Object targetEntity, Map<String, Object> fullRow) {
								AQTRowProcessor.setFieldValue(targetEntity, child.field(), Map.of("linkedInUrl", fullRow.get(parentNode.alias() + ".linkedInUrl")));
							}
						};
						return customNode;
					}
					return child;
				});
			}
	}
	
	@Test
	public void testBasics() throws SQLException {

		PojoQuery<User> query = PojoQuery.build(DbContext.getDefault(),
				TransformPipeline.defaultPipeline()
						.insertAfter(AddDeclaredFields.class, MyCustomTransform.class), User.class);
		
		Assert.assertEquals(norm("""
			SELECT
			 `user`.`id` AS `user.id`,
			 `user`.`email` AS `user.email`,
			 `user`.`custom_linkedInUrl` AS `user.linkedInUrl` 
			FROM `user` AS `user`
			"""), norm(query.toStatement().getSql()));
		
		List<Map<String,Object>> resultSet = TestUtils.resultSet(new String[] 
				{"user.id", "user.email",     "user.linkedInUrl"}, 
				  1L,       "john@ewbank.nl", "http://www.linkedin.com/123456");
		
		List<User> users = AQTRowProcessor.processRows(query.getTree(), resultSet);
		// List<User> users = q.processRows(resultSet);
		Assert.assertEquals("http://www.linkedin.com/123456", users.get(0).getCustomValue("linkedInUrl"));
	}

}
