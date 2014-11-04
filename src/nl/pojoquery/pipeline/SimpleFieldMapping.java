package nl.pojoquery.pipeline;

import java.lang.reflect.Field;

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
			f.set(targetEntity, value);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new MappingException("Exception setting value of field " + f + " of entity " + targetEntity, e);
		}
	}

}
