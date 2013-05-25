package nl.pojoquery;

import java.util.List;
import java.util.Map;

import nl.pojoquery.annotations.Other;
import nl.pojoquery.annotations.Table;

import org.junit.Assert;
import org.junit.Test;

public class TestCustomFields {

	@Table("user")
	static class User {
		Long id;
		String email;
		
		@Other
		Map<String,Object> customFields;
		
		public Object getCustomValue(String field) {
			return customFields.get(field);
		}
	}
	
	@Test
	public void testBasics() {
		PojoQuery<User> query = PojoQuery.build(User.class).addField("user.custom_linkedInUrl `user.linkedInUrl`");
		
		Assert.assertEquals("SELECT" +
				" `user`.id `user.id`," +
				" `user`.email `user.email`," +
				" user.custom_linkedInUrl `user.linkedInUrl` " +
				"FROM user", TestUtils.norm(query.toSql()));
		
		List<Map<String,Object>> resultSet = TestUtils.resultSet(new String[] 
				{"user.id", "user.email",     "user.linkedInUrl"}, 
				  1L,       "john@ewbank.nl", "http://www.linkedin.com/123456");
		
		List<User> users = PojoQuery.processRows(resultSet, User.class);
		Assert.assertEquals("http://www.linkedin.com/123456", users.get(0).getCustomValue("linkedInUrl"));
	}

}
