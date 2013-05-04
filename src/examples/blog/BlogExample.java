package blog;

import java.util.List;

import javax.sql.DataSource;

import system.db.DB;
import system.sql.Query;

public class Main {
	
	public static void main(String[] args) throws Exception {
		
		Query<BlogList> q = Query.buildQuery(BlogList.class).addWhere("blog_id=?", 1L);
		System.out.println(q.toSql());
		
		List<BlogList> results = q.execute(getConnection());
		
		System.out.println(results.get(0).lastCommentDate);
	}
	
	private static DataSource getConnection() {
		return DB.getDataSource("jdbc:mysql://localhost/blog", "root", "");
	}
}
