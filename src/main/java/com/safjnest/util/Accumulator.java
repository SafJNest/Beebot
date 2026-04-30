package com.safjnest.util;

public class Accumulator {

    public int sum = 0;
    public int count = 0;

    public void add(int value) {
        sum += value;
        count++;
    }

    public double avg() {
        return count == 0 ? 0 : (double) sum / count;
    }

    @Override
    public String toString() {
        return "sum: " + sum + ", count: " + count + ", avg: " + avg();
    }
}
