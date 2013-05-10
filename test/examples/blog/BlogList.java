package examples.blog;

import java.util.Date;

import nl.pojoquery.annotations.GroupBy;
import nl.pojoquery.annotations.Join;
import nl.pojoquery.annotations.Select;


@Join("LEFT JOIN comment ON comment.article_id = article.id")
@GroupBy("article.id")
public class BlogList extends Article {
	
	public User author;

	@Select("COUNT(comment.id)")
	public Long commentCount;

	@Select("MAX(comment.submitdate)")
	public Date lastCommentDate;

}