package examples.typedquery;

import org.pojoquery.annotations.GenerateQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

/**
 * Entity using array for collection relationship to test TypedQuery collection handling.
 */
@Table("employee")
@GenerateQuery
public class EmployeeWithArrayProjects {

    @Id
    public Long id;

    public String firstName;

    public String lastName;

    public String email;

    /**
     * One-to-many relationship using array instead of List or Set.
     */
    public Project[] projects;
}
