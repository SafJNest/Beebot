package com.hive.job;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class ResultType<T> {

    private final Class<?> rawType;
    private final Class<?> elementType;
    private final Function<Object, T> converter;

    private ResultType(Class<?> rawType, Class<?> elementType, Function<Object, T> converter) {
        this.rawType = Objects.requireNonNull(rawType, "rawType");
        this.elementType = elementType;
        this.converter = converter;
    }

    public static <T> ResultType<T> of(Class<T> type) {
        return new ResultType<>(type, null, type::cast);
    }

    public static <E> ResultType<List<E>> list(Class<E> elementType) {
        Objects.requireNonNull(elementType, "elementType");
        return new ResultType<>(List.class, elementType, value -> castList(value, elementType));
    }

    public T cast(Object value) { return value == null ? null : converter.apply(value); }

    @Override
    public boolean equals(Object other) {
        return other instanceof ResultType<?> type && rawType == type.rawType && elementType == type.elementType;
    }

    @Override
    public int hashCode() { return Objects.hash(rawType, elementType); }

    // ============================================================================

    @SuppressWarnings("unchecked")
    private static <E> List<E> castList(Object value, Class<E> elementType) {
        List<?> list = List.class.cast(value);
        for (Object element : list) elementType.cast(element);
        return (List<E>) list;
    }
}
