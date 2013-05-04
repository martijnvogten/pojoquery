PojoQuery

PojoQuery is a light-weight alternative to traditional Object Relational Mapping (ORM) frameworks in Java.
Unlike JPA or Hibernate, PojoQuery does not try to abstract away the database, but instead makes working with a relational database in Java as simple and pleasant as possible.

The basic idea is this: Instead of writing a SQL query in plain text, we create a Plain Old Java Object (POJO).
Each field or property in the POJO corresponds to a field in the SELECT clause of the query.

	@Table("employee")
	public class Employee {
		Long id;
		String firstName;
		String lastName;
	}

	// SELECT id, firstName, lastName FROM employee
	List<Employee> employees = Query.buildQuery(Employee.class).execute(getDataSource());

PojoQuery creates a query from the pojo, in this case:

and transforms the JDBC resultset to instances of Employee.

A more advanced example: We want to show a list of articles from a blog, listing a title and the number of comments with the most recently commented article first.

Now in SQL this would be

SELECT 
  article.id, 
  article.title, 
  COUNT(comment.id) AS commentCount, 
  MAX(comment.submitdate) AS lastCommentDate 
FROM article
  LEFT JOIN comment ON comment.article_id = article.id
GROUP BY 1, 2, 3
ORDER BY lastCommentDate DESC

PojoQuery uses annotations to provide the select, join and group by clauses, like so:

@Table("article")
@Join("LEFT JOIN comment ON comment.article_id = article.id")
@GroupBy("article.id")
public class ArticleView {
	Long id;
	String title;
	
	@Select("COUNT(comment.id)")
	int commentCount;
	
	@Select("MAX(comment.submitdate)")
	Date lastCommentDate;
}


DataSource db = getDatabase();
List<Employee> result = Query.buildQuery(Employee.class).execute(db);

Use annotations to add SQL constructs like join and group by:

@Table("article")
@Join("LEFT JOIN comment ON comment.article_id = article.id")
@GroupBy("1,2,3")
public class ArticleSummary {

	Long id;
	String title;

	@Select("SUBSTRING(article.text, 1, 200)")
	String summary;
	
	@Select("COUNT(comment.id)")
	int commentCount;
	
	@Select("MAX(comment.creationdate)")
	Date lastCommentDate;

	public static void main(String[] args) {
		List<ArticleSummary> articles = Query.buildQuery(ArticleSummary.class).addLimit(0, 10).execute(getDatabase());
		
		for(ArticleSummary as : articles) {
			System.out.println("Article #" + as.id + " '" + as.title + " has ")
		}
	}

	private static DataSource getDatabase() {
		// ...
	}
}

NNN is clever with links. 
