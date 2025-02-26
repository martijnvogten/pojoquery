package nl.pojoquery.pipeline;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import nl.pojoquery.FieldMapping;
import nl.pojoquery.internal.MappingException;

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
			if (value instanceof LocalDateTime && (f.getType().equals(Instant.class))) {
				value = ((LocalDateTime)value).atZone(ZoneOffset.UTC).toInstant();
			}
			if (value instanceof Timestamp && (f.getType().equals(Instant.class))) {
				value = ((Timestamp)value).toInstant();
			}
			f.setAccessible(true);
			f.set(targetEntity, value);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new MappingException("Exception setting value of field " + f + " of entity " + targetEntity, e);
		}
	}

	public Field getField() {
		return f;
	}
}
