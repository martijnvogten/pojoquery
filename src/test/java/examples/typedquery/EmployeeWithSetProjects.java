package examples.typedquery;

import java.util.Set;

import org.pojoquery.annotations.GenerateQuery;

/**
 * Entity using Set for collection relationship to test TypedQuery collection handling.
 */
@GenerateQuery
public class EmployeeWithSetProjects extends Employee {
    public Set<Project> projects;
}
