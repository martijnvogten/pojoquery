package examples.blog;

import java.util.Date;

import nl.pojoquery.annotations.GroupBy;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Join;
import nl.pojoquery.annotations.Select;
import nl.pojoquery.annotations.Table;


@Table("blog")
@Join("LEFT JOIN comment ON comment.blog_id = blog.id")
@GroupBy("blog.id")
public class BlogList {
	@Id
	public Integer id;
	public String title;

	@Select("SUBSTR(blog.content, 1, 20)")
	public String summary;

	@Select("COUNT(comment.id)")
	public Long commentCount;

	@Select("MAX(comment.creationDate)")
	public Date lastCommentDate;

}