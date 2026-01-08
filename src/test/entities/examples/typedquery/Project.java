package examples.typedquery;

import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

/**
 * Example Project entity for demonstrating one-to-many relationships.
 */
@Table("project")
public class Project {

    @Id
    public Long id;

    public String name;

    public String status;

    public Long employee_id;  // Foreign key to employee

    @Override
    public String toString() {
        return "Project [id=" + id + ", name=" + name + ", status=" + status + "]";
    }
}
