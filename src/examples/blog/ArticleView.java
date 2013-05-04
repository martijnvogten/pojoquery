package examples.blog;

import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import nl.pojoquery.PojoQuery;
import nl.pojoquery.annotations.GroupBy;
import nl.pojoquery.annotations.Join;
import nl.pojoquery.annotations.Select;
import nl.pojoquery.annotations.Table;


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
		List<ArticleView> articles = PojoQuery.create(ArticleView.class).addWhere("blog.id=?", 3L).execute(getDatabase());
		
	}

	private static DataSource getDatabase() {
		return null;
	}
}
