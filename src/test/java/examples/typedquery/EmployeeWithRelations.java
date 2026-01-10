package examples.typedquery;

import java.util.List;

import org.pojoquery.annotations.GenerateQuery;

/**
 * Example entity demonstrating relationships in generated queries.
 *
 * <p>This entity has:
 * <ul>
 *   <li>One-to-one relationship with Department (via department_id FK)</li>
 *   <li>One-to-many relationship with Project (projects have employee_id FK)</li>
 * </ul>
 */
@GenerateQuery
public class EmployeeWithRelations extends Employee  {

    public Department department;

    /**
     * One-to-many relationship: Employee has many Projects.
     * Join condition: employee.id = project.employee_id
     */
    public List<Project> projects;

    public EmployeeWithRelations() {
        super();
    }

    public EmployeeWithRelations(String firstName, String lastName, String email, Department department) {
        super(firstName, lastName, email, null);
        this.department = department;
    }

    @Override
    public String toString() {
        return "Employee [id=" + id + ", name=" + firstName + " " + lastName +
               ", department=" + (department != null ? department.name : "null") +
               ", projects=" + (projects != null ? projects.size() : 0) + "]";
    }
}
