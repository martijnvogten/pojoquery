package blog;

import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import system.sql.Query;
import system.sql.annotations.GroupBy;
import system.sql.annotations.Join;
import system.sql.annotations.Select;
import system.sql.annotations.Table;

@Table("blog")
@Join("LEFT JOIN comment ON comment.blog_id = blog.id")
@GroupBy("blog.id")
public class ArticleView {
	Long id;
	String title;
	
	@Select("COUNT(comment.id)")
	int commentCount;
	
	@Select("MAX(comment.creationdate)")
	Date lastCommentDate;

	public static void main(String[] args) {
		List<ArticleView> articles = Query.buildQuery(ArticleView.class).addWhere("blog.id=?", 3L).execute(getDatabase());
		
	}

	private static DataSource getDatabase() {
		return null;
	}
}
