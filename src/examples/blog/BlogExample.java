package examples.blog;

import java.util.List;

import javax.sql.DataSource;

import nl.pojoquery.PojoQuery;


public class BlogExample {
	
	public static void run(DataSource db) {
		
		PojoQuery<BlogList> q = PojoQuery.build(BlogList.class).addWhere("blog_id=?", 1L);
		System.out.println(q.toSql());
		
		List<BlogList> results = q.execute(db);
		
		System.out.println(results.get(0).lastCommentDate);
	}
	
}
