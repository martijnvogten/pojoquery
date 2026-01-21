package examples.typedquery.nameclash;

import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

@Table("department")
public class DepartmentRef {
	@Id
	public Long id;
	
	public String name;
}

