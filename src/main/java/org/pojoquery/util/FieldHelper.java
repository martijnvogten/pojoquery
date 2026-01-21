package org.pojoquery.util;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility methods for setting field values, handling collections and arrays.
 */
public class FieldHelper {

    /**
     * Gets a field from a class, searching the entire class hierarchy.
     * Unlike {@link Class#getDeclaredField(String)}, this method searches
     * superclasses if the field is not found in the specified class.
     *
     * @param clazz the class to search
     * @param fieldName the name of the field
     * @return the Field object
     * @throws NoSuchFieldException if the field is not found in the class hierarchy
     */
    public static Field getField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName + " not found in " + clazz.getName() + " or its superclasses");
    }

    /**
     * Sets a value into a field, handling collections and arrays appropriately.
     * <ul>
     *   <li>For List fields: adds the value if not already present</li>
     *   <li>For Set fields: adds the value (Set handles duplicates)</li>
     *   <li>For Array fields: extends the array if value not already present</li>
     *   <li>For other fields: directly sets the value</li>
     * </ul>
     *
     * @param parent the object containing the field
     * @param field the field to set
     * @param value the value to set or add
     */
    @SuppressWarnings("unchecked")
    public static void putValueIntoField(Object parent, Field field, Object value) {
        if (parent == null || value == null) {
            return;
        }
        try {
            field.setAccessible(true);
            if (List.class.isAssignableFrom(field.getType())) {
                List<Object> coll = (List<Object>) field.get(parent);
                if (coll == null) {
                    coll = new ArrayList<>();
                    field.set(parent, coll);
                }
                if (!coll.contains(value)) {
                    coll.add(value);
                }
            } else if (Set.class.isAssignableFrom(field.getType())) {
                Set<Object> coll = (Set<Object>) field.get(parent);
                if (coll == null) {
                    coll = new HashSet<>();
                    field.set(parent, coll);
                }
                coll.add(value);
            } else if (field.getType().isArray()) {
                Object arr = field.get(parent);
                int len = (arr == null) ? 0 : Array.getLength(arr);
                if (arr == null || !Arrays.asList((Object[]) arr).contains(value)) {
                    Object extended = Array.newInstance(field.getType().getComponentType(), len + 1);
                    if (len > 0) {
                        System.arraycopy(arr, 0, extended, 0, len);
                    }
                    Array.set(extended, len, value);
                    field.set(parent, extended);
                }
            } else {
                field.set(parent, value);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set field " + field.getName(), e);
        }
    }
}
