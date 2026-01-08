package examples.typedquery;

import java.util.List;

import org.pojoquery.annotations.GenerateQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

/**
 * Example entity demonstrating relationships in generated queries.
 *
 * <p>This entity has:
 * <ul>
 *   <li>One-to-one relationship with Department (via department_id FK)</li>
 *   <li>One-to-many relationship with Project (projects have employee_id FK)</li>
 * </ul>
 */
@Table("employee")
@GenerateQuery
public class EmployeeWithRelations {

    @Id
    public Long id;

    public String firstName;

    public String lastName;

    public String email;

    public Long department_id;  // Foreign key column

    /**
     * One-to-one relationship: Employee belongs to a Department.
     * Join condition: employee.department_id = department.id
     */
    public Department department;

    /**
     * One-to-many relationship: Employee has many Projects.
     * Join condition: employee.id = project.employee_id
     */
    public List<Project> projects;

    @Override
    public String toString() {
        return "Employee [id=" + id + ", name=" + firstName + " " + lastName +
               ", department=" + (department != null ? department.name : "null") +
               ", projects=" + (projects != null ? projects.size() : 0) + "]";
    }
}
