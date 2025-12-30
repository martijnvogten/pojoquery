package org.pojoquery.pipeline;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.pojoquery.FieldMapping;
import org.pojoquery.internal.MappingException;

public class SimpleFieldMapping implements FieldMapping {

	private Field f;

	public SimpleFieldMapping(Field f) {
		this.f = f;
	}

	@Override
	public void apply(Object targetEntity, Object value) {
		try {
			if (value instanceof String && f.getType().isEnum()) {
				value = QueryBuilder.enumValueOf(f.getType(), (String)value);
			}
			if (value instanceof BigDecimal && (f.getType().equals(Integer.class) || f.getType().equals(Integer.TYPE))) {
				value = ((BigDecimal)value).intValue();
			}
			if (value instanceof BigDecimal && (f.getType().equals(Long.class) || f.getType().equals(Long.TYPE))) {
				value = ((BigDecimal)value).longValue();
			}
			if (value instanceof Date && (f.getType().equals(LocalDate.class))) {
				value = ((Date)value).toLocalDate();
			}
			if (value instanceof Timestamp && (f.getType().equals(LocalDateTime.class))) {
				value = ((Timestamp)value).toLocalDateTime();
			}
			if (value instanceof LocalDateTime && (f.getType().equals(Instant.class))) {
				value = ((LocalDateTime)value).atZone(ZoneOffset.UTC).toInstant();
			}
			if (value instanceof Timestamp && (f.getType().equals(Instant.class))) {
				value = ((Timestamp)value).toInstant();
			}
			if (value instanceof Blob && f.getType().equals(byte[].class)) {
				Blob blob = (Blob) value;
				value = blob.getBytes(1, (int) blob.length());
			}
			f.setAccessible(true);
			f.set(targetEntity, value);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new MappingException("Exception setting value of field " + f + " of entity " + targetEntity, e);
		} catch (SQLException e) {
			throw new MappingException("Exception reading blob value for field " + f + " of entity " + targetEntity, e);
		}
	}

	public Field getField() {
		return f;
	}
}
