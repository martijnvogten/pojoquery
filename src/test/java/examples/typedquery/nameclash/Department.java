package examples.typedquery.nameclash;

import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

/**
 * A Department entity used in multiple relationship chains.
 */
@Table("department")
public class Department {
    
    @Id
    public Long id;
    
    public String name;
    
    public String location;
    
    /** Department head - this creates the chain: Department -> Manager -> DepartmentRef */
    public Manager head;
    
    public Department() {}
    
    public Department(String name, String location) {
        this.name = name;
        this.location = location;
    }
}
