package blog;

import java.util.Date;

import system.sql.annotations.GroupBy;
import system.sql.annotations.Id;
import system.sql.annotations.Join;
import system.sql.annotations.Select;
import system.sql.annotations.Table;

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