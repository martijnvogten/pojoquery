package org.pojoquery.pipeline;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.pojoquery.JdbcValueMapper;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;

public class DefaultValueMappers {

	public static JdbcValueMapper forField(FieldModel f) {
		return createMapper(f.getType());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static JdbcValueMapper createMapper(TypeModel targetTypeModel) {
		if (!(targetTypeModel instanceof ReflectionTypeModel)) {
			return value -> value;
		}
		Class<?> targetType = ((ReflectionTypeModel) targetTypeModel).getReflectionClass();
		if (targetType.isEnum() ) {
			return o -> o instanceof String ? Enum.valueOf((Class<Enum>) targetType, (String)o) : o;
		}
		else if (targetType.equals(Integer.class) || targetType.equals(Integer.TYPE)) {
			return value -> value instanceof BigDecimal ? ((BigDecimal)value).intValue() : value;
		}
		else if (targetType.equals(Long.class) || targetType.equals(Long.TYPE)) {
			return value -> value instanceof BigDecimal ? ((BigDecimal)value).longValue() : value;
		}
		else if (targetType.equals(LocalDate.class)) {
			return value -> value instanceof Date ? ((Date)value).toLocalDate() : value;
		}
		else if (targetType.equals(LocalDateTime.class)) {
			return value -> value instanceof Timestamp ? ((Timestamp)value).toLocalDateTime() : value;
		}
		else if (targetType.equals(Instant.class)) {
			return value -> value instanceof Timestamp ? ((Timestamp)value).toInstant() :
			                value instanceof LocalDateTime ? ((LocalDateTime)value).atZone(ZoneOffset.UTC).toInstant() : value;
		}
		else if (targetType.equals(LocalTime.class)) {
			return value -> value instanceof java.sql.Time ? ((java.sql.Time)value).toLocalTime() : value;
		}
		else if (targetType.equals(byte[].class)) {
			return value -> value instanceof Blob ? ((Blob)value).getBytes(1, (int)((Blob)value).length()) : value;
		}
		else if (targetType.equals(String.class)) {
			return value -> value instanceof Clob ? ((Clob)value).getSubString(1, (int)((Clob)value).length()) : value;
		}
		else {
			return value -> value;
		}
	}

	@SuppressWarnings("unchecked")
	public static <L> L addValueToCollection(Class<L> collectionType, Object collection, Class<?> componentType, Object value) {
		if (List.class.isAssignableFrom(collectionType)) {
			List<Object> coll = (List<Object>) collection;
			if (coll == null) {
				coll = new ArrayList<>();
			}
			if (!coll.contains(value)) {
				coll.add(value);
			}
			return (L) coll;
		} else if (Set.class.isAssignableFrom(collectionType)) {
			Set<Object> coll = (Set<Object>) collection;
			if (coll == null) {
				coll = new HashSet<>();
			}
			coll.add(value);
			return (L) coll;
		} else if (collectionType.isArray()) {
			Object arr = collection;
			int len = (arr == null) ? 0 : Array.getLength(arr);
			if (arr == null || !Arrays.asList((Object[]) arr).contains(value)) {
				Object extended = Array.newInstance(componentType, len + 1);
				if (len > 0) {
					System.arraycopy(arr, 0, extended, 0, len);
				}
				Array.set(extended, len, value);
				return (L) extended;
			}
			return (L) arr;
		} else {
			return (L) value;
		}
	}
}
