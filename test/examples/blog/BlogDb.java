package examples.blog;

import java.util.Date;

import javax.sql.DataSource;

import nl.pojoquery.DB;
import nl.pojoquery.PojoQuery;
import examples.blog.ArticleDetailExample.Article;
import examples.blog.ArticleDetailExample.Comment;
import examples.blog.ArticleDetailExample.User;
import examples.util.MysqlDatabases;

public class BlogDb {

	public static DataSource create(String schemaname) {
		DataSource db = MysqlDatabases.createDatabase("localhost", schemaname, "root", "");
		createTables(db);
		insertData(db);
		return db;
	}
	
	private static void createTables(DataSource db) {
		DB.executeDDL(db, "CREATE TABLE article (id BIGINT NOT NULL AUTO_INCREMENT, title TEXT, content LONGTEXT, author_id BIGINT, PRIMARY KEY(id))");
		DB.executeDDL(db, "CREATE TABLE user    (id BIGINT NOT NULL AUTO_INCREMENT, email VARCHAR(255), firstName VARCHAR(255), lastName VARCHAR(255), PRIMARY KEY(id))");
		DB.executeDDL(db, "CREATE TABLE comment (id BIGINT NOT NULL AUTO_INCREMENT, article_id BIGINT NOT NULL, comment LONGTEXT, submitdate DATETIME, author_id BIGINT, PRIMARY KEY(id))");
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
		
		Comment relativity = new Comment();
		relativity.author_id = albert.id;
		relativity.article_id = article.id;
		relativity.comment = "It all depends on how you look at it";
		relativity.submitdate = new Date();
		relativity.id = PojoQuery.insert(db, relativity);
	}


}
