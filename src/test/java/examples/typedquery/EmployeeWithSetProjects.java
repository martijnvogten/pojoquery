package examples.typedquery;

import java.util.Set;

import org.pojoquery.annotations.GenerateQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

/**
 * Entity using Set for collection relationship to test TypedQuery collection handling.
 */
@Table("employee")
@GenerateQuery
public class EmployeeWithSetProjects {

    @Id
    public Long id;

    public String firstName;

    public String lastName;

    public String email;

    /**
     * One-to-many relationship using Set instead of List.
     */
    public Set<Project> projects;
}
