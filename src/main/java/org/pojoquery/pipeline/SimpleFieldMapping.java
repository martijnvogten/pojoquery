package org.pojoquery.pipeline;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.pojoquery.FieldMapping;
import org.pojoquery.internal.MappingException;

public class SimpleFieldMapping implements FieldMapping {

	private Field f;

	public SimpleFieldMapping(Field f) {
		this.f = f;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void apply(Object targetEntity, Object value) {
		try {
			if (value instanceof String && f.getType().isEnum()) {
				value = Enum.valueOf((Class<Enum>) f.getType(), (String)value);
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
				// Same policy as the Timestamp branch above: a zone-less value is
				// resolved in the JVM's default zone, whichever shape the driver gave.
				value = ((LocalDateTime)value).atZone(ZoneId.systemDefault()).toInstant();
			}
			if (value instanceof LocalDateTime && (f.getType().equals(Timestamp.class))) {
				value = Timestamp.valueOf((LocalDateTime)value);
			}
			if (value instanceof LocalDate && (f.getType().equals(Date.class))) {
				value = Date.valueOf((LocalDate)value);
			}
			if (value instanceof LocalTime && (f.getType().equals(java.sql.Time.class))) {
				value = java.sql.Time.valueOf((LocalTime)value);
			}
			if (value instanceof Timestamp && (f.getType().equals(Instant.class))) {
				value = ((Timestamp)value).toInstant();
			}
			if (value instanceof java.sql.Time && (f.getType().equals(LocalTime.class))) {
				value = ((java.sql.Time)value).toLocalTime();
			}
			if (value instanceof Blob && f.getType().equals(byte[].class)) {
				Blob blob = (Blob) value;
				value = blob.getBytes(1, (int) blob.length());
			}
			if (value instanceof Clob && f.getType().equals(String.class)) {
				Clob clob = (Clob) value;
				value = clob.getSubString(1, (int) clob.length());
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
