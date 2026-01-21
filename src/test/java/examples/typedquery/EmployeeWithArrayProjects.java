package examples.typedquery;

import org.pojoquery.annotations.GenerateQuery;

/**
 * Entity using array for collection relationship to test TypedQuery collection handling.
 */
@GenerateQuery
public class EmployeeWithArrayProjects extends Employee  {
    /**
     * One-to-many relationship using array instead of List or Set.
     */
    public Project[] projects;
}
