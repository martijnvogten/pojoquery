package examples.typedquery.nameclash;

import org.pojoquery.annotations.GenerateQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

/**
 * A Company entity that demonstrates the name clash problem.
 * 
 * <p>Relationship chain:
 * <ul>
 *   <li>Company -> mainDepartment (Department) -> head (Manager) -> department (Department)</li>
 * </ul>
 * 
 * <p>Without proper nesting, the generated fields class would have conflicting nested classes.
 * 
 * <p>With proper nesting, we get:
 * <pre>
 * Company_ {
 *     class mainDepartment {
 *         class head {
 *             class department { ... }  // Properly nested!
 *         }
 *     }
 * }
 * </pre>
 */
@Table("company")
@GenerateQuery
public class Company {
    
    @Id
    public Long id;
    
    public String name;
    
    /** The main department, whose head also has a department */
    public Department mainDepartment;
    
    public Company() {}
    
    public Company(String name) {
        this.name = name;
    }
}
