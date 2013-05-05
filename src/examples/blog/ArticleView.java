package examples.blog;

import java.util.Date;

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

}
