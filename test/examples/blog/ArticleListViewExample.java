package examples.blog;

import java.util.Date;

import javax.sql.DataSource;

import nl.pojoquery.PojoQuery;
import nl.pojoquery.annotations.GroupBy;
import nl.pojoquery.annotations.Join;
import nl.pojoquery.annotations.Select;
import examples.blog.ArticleDetailExample.Article;
import examples.blog.ArticleDetailExample.User;


public class ArticleListViewExample {
	
	@Join("LEFT JOIN comment ON comment.article_id = article.id")
	@GroupBy("article.id")
	public static class ArticleListView extends Article {
		public User author;
		
		@Select("COUNT(comment.id)")
		public Long commentCount;
		
		@Select("MAX(comment.submitdate)")
		public Date lastCommentDate;
	}

	public static void main(String[] args) {
		DataSource db = BlogDb.create("localhost", "pojoquery_blog", "root", "");
		
		PojoQuery<ArticleListView> q = PojoQuery.build(ArticleListView.class)
				.addOrderBy("comment.submitdate DESC");
		
		System.out.println(q.toSql());
		
		for(ArticleListView article : q.execute(db)) {
			System.out.println("Article #" + article.id + " by " + article.author.getFullName() + " has " + article.commentCount + " comments");
		}
	}
}