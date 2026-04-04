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
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.DefaultSqlQuery;
import org.pojoquery.pipeline.RecursiveTransform;
import org.pojoquery.pipeline.SqlQuery;
import org.pojoquery.pipeline.TransformPipeline;
import org.pojoquery.pipeline.Transforms;
import org.pojoquery.pipeline.Transforms.AddDeclaredFields;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;

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

	public static class MyCustomTransform extends RecursiveTransform {

		@Override
		public QueryNode transform(QueryNode node) {
			return Transforms.transformChildren(
				node, 
				child -> child instanceof EmptyFieldNode emptyFieldNode && emptyFieldNode.field().hasAnnotation(CustomFields.class), 
				(tableNode, child) -> {
					if (tableNode.type().isSameType(User.class)) {
						FieldModel javaField = new ReflectionTypeModel(User.class).getDeclaredFields().stream().filter(f -> f.getName().equals("customFields")).findFirst().orElseThrow();
						CustomQueryNode customNode = new AbstractQueryTree.CustomQueryNode() {
							@Override
							public void applyToSqlQuery(TableNode parentNode, SqlQuery<?> sqlQuery) {
								sqlQuery.addField(new SqlExpression("{" + parentNode.alias() + "}.custom_linkedInUrl"), parentNode.alias() + ".linkedInUrl");
							}

							@Override
							public void applyRowResultToEntity(AbstractQueryTree.TableNode parentNode, Object targetEntity, Map<String, Object> fullRow) {
								AQTRowProcessor.setFieldValue(targetEntity, javaField, Map.of("linkedInUrl", fullRow.get(parentNode.alias() + ".linkedInUrl")));
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

		TransformPipeline pipeline = TransformPipeline.defaultPipeline()
				.insertAfter(AddDeclaredFields.class, MyCustomTransform.class);
		
		RootNode root = AQTTransformer.buildQueryTreeForType(new ReflectionTypeModel(User.class), pipeline);

		// PojoQuery<User> q = PojoQuery.build(User.class);
		// q.addField(new SqlExpression("{" + root.alias() + "}.custom_linkedInUrl"), root.alias() + ".linkedInUrl");

		SqlQuery<?> query = new DefaultSqlQuery(DbContext.getDefault());
		AQTTransformer.toSql(root, query);

		Assert.assertEquals(norm("""
			SELECT
			 `user`.`id` AS `user.id`,
			 `user`.`email` AS `user.email`,
			 `user`.custom_linkedInUrl AS `user.linkedInUrl` 
			FROM `user` AS `user`
			"""), norm(query.toStatement().getSql()));
		
		List<Map<String,Object>> resultSet = TestUtils.resultSet(new String[] 
				{"user.id", "user.email",     "user.linkedInUrl"}, 
				  1L,       "john@ewbank.nl", "http://www.linkedin.com/123456");
		
		List<User> users = AQTRowProcessor.processRows(root, resultSet);
		// List<User> users = q.processRows(resultSet);
		Assert.assertEquals("http://www.linkedin.com/123456", users.get(0).getCustomValue("linkedInUrl"));
	}

}
