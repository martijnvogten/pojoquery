package examples.typedquery;

import java.time.LocalDate;

import org.pojoquery.annotations.GenerateQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

/**
 * Example entity class demonstrating the @GenerateQuery annotation.
 *
 * <p>The annotation processor will generate:
 * <ul>
 *   <li>{@code Employee_} - Static field references for type-safe queries</li>
 *   <li>{@code EmployeeQuery} - Fluent query builder with efficient ResultSet mapping</li>
 * </ul>
 */
@Table("employee")
@GenerateQuery
public class Employee {

    @Id
    public Long id;

    public String firstName;

    public String lastName;

    public String email;

    public LocalDate hireDate;

    public Integer salary;
    
    public String primary;

    protected Employee() {}

    public Employee(String firstName, String lastName, String email, Integer salary) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.salary = salary;
    }

    @Override
    public String toString() {
        return "Employee [id=" + id + ", firstName=" + firstName + ", lastName=" + lastName +
               ", email=" + email + ", hireDate=" + hireDate + ", salary=" + salary + "]";
    }
}
