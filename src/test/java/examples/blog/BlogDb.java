package examples.blog;

import java.util.Date;

import javax.sql.DataSource;

import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.integrationtest.db.TestDatabase;
import org.pojoquery.schema.SchemaGenerator;

import examples.blog.ArticleDetailExample.Article;
import examples.blog.ArticleDetailExample.Comment;
import examples.blog.ArticleDetailExample.User;
import examples.blog.ArticleDetailExample.Views;

public class BlogDb {

	public static DataSource create(String host, String schemaname, String username, String password) {
		DataSource db = TestDatabase.dropAndRecreate();
		createTables(db);
		insertData(db);
		return db;
	}
	
	private static void createTables(DataSource db) {
		SchemaGenerator.createTables(db, Article.class, User.class, Comment.class, Views.class);
	}
	
	private static void insertData(DataSource db) {
		User john = new User();
		john.email = "john@ewbank.nl";
		john.firstName = "John";
		john.lastName = "Ewbank";
		john.id = PojoQuery.insert(db, john);
		
		User albert = new User();
		albert.email = "albert@einstein.com";
		albert.firstName = "Albert";
		albert.lastName = "Einstein";
		albert.id = PojoQuery.insert(db, albert);
		
		Article article = new Article();
		article.title = "King's song";
		article.content = "I wrote it";
		article.author_id = john.id;
		article.id = PojoQuery.insert(db, article);
		
		Comment ilikeit = new Comment();
		ilikeit.author_id = john.id;
		ilikeit.article_id = article.id;
		ilikeit.comment = "I like it too!";
		ilikeit.submitdate = new Date();
		ilikeit.id = PojoQuery.insert(db, ilikeit);
		
		Comment imagination = new Comment();
		imagination.author_id = albert.id;
		imagination.article_id = article.id;
		imagination.comment = "Imagination is more important than knowledge.";
		imagination.submitdate = new Date();
		imagination.id = PojoQuery.insert(db, imagination);
	}


}
