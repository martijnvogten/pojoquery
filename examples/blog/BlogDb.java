package blog;

import java.util.Date;

import javax.sql.DataSource;

import blog.ArticleDetailExample.Article;
import blog.ArticleDetailExample.Comment;
import blog.ArticleDetailExample.User;

import nl.pojoquery.DB;
import nl.pojoquery.PojoQuery;
import nl.pojoquery.integrationtest.MysqlDatabases;

public class BlogDb {

	public static DataSource create(String host, String schemaname, String username, String password) {
		DataSource db = MysqlDatabases.createDatabase(host, schemaname, username, password);
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
		
		Comment imagindation = new Comment();
		imagindation.author_id = albert.id;
		imagindation.article_id = article.id;
		imagindation.comment = "Imagination is more important than knowledge.";
		imagindation.submitdate = new Date();
		imagindation.id = PojoQuery.insert(db, imagindation);
	}


}
