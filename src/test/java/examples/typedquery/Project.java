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

    public Employee employee;

    public Project() {
    }

    public Project(String name, String status, Employee employee) {
        this.name = name;
        this.status = status;
        this.employee = employee;
    }

    @Override
    public String toString() {
        return "Project [id=" + id + ", name=" + name + ", status=" + status + "]";
    }
}
