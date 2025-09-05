package com.safjnest.model.guild.alert;

import java.util.Objects;

public class AlertKey<T> {
    private AlertType type;
    private T key;

    public AlertKey(AlertType type) {
        this.type = type;
        this.key = null;
    }

    public AlertKey(AlertType type, T key) {
        this.type = type;
        this.key = key;
    }

    public AlertType getType() {
        return type;
    }

    public T getKey() {
        return key;
    }

    @Override
    public String toString() {
        return "AlertKey{" +
                "type=" + type +
                ", key=" + key +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AlertKey)) return false;
        AlertKey<?> alertKey = (AlertKey<?>) o;
        return type == alertKey.type &&
                Objects.equals(key, alertKey.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, key);
    }
}