package examples.blog;

import java.util.Date;

import javax.sql.DataSource;

import examples.blog.ArticleDetailExample.Article;
import examples.blog.ArticleDetailExample.User;
import nl.pojoquery.PojoQuery;
import nl.pojoquery.annotations.GroupBy;
import nl.pojoquery.annotations.Join;
import nl.pojoquery.annotations.Select;
import nl.pojoquery.pipeline.SqlQuery.JoinType;


public class ArticleListViewExample {
	
	@Join(type=JoinType.LEFT, tableName="comment", alias="comment", joinCondition="{comment}.article_id = {this}.id")
	@Join(type=JoinType.LEFT, tableName="views", alias="views", joinCondition="{views}.article_id = {this}.id")
	@GroupBy("article.id")
	public static class ArticleListView extends Article {
		public User author;
		
		@Select("COUNT(comment.id)")
		public Long commentCount;
		
		@Select("COUNT(views.id)")
		public Long viewCount;
		
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