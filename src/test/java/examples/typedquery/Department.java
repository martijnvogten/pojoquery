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

    @Override
    public String toString() {
        return "Department [id=" + id + ", name=" + name + ", location=" + location + "]";
    }
}
