package examples.typedquery;

import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

/**
 * Example Department entity for demonstrating one-to-one relationships.
 */
@Table("department")
public class Department {

    @Id
    public Long id;

    public String name;

    public String location;

    public Department() {
    }

    public Department(String name, String location) {
        this.name = name;
        this.location = location;
    }

    @Override
    public String toString() {
        return "Department [id=" + id + ", name=" + name + ", location=" + location + "]";
    }
}
