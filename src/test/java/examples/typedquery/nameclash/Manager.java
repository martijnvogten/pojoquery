package examples.typedquery.nameclash;

import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

/**
 * A Manager with a department. This entity will also be used as a relationship
 * in other entities, potentially causing name clashes in generated code.
 */
@Table("manager")
public class Manager {
    
    @Id
    public Long id;
    
    public String name;
    
    /** The manager's department */
    public DepartmentRef department;
    
    public Manager() {}
    
    public Manager(String name, DepartmentRef department) {
        this.name = name;
        this.department = department;
    }
}
