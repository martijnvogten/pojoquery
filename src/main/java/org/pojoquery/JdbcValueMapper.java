package org.pojoquery;

import java.sql.SQLException;

public interface JdbcValueMapper {
	Object mapValue(Object jdbcValue) throws SQLException;
}
