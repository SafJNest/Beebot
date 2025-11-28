package com.safjnest.lol.model;

public class Accumulator {
  int sum = 0;
  public int count = 0;

  public void add(int value) {
    sum += value;
    count++;
  }

  public double avg() {
    return count == 0 ? 0 : (double) sum / count;
  }

  public String toString() {
    return "sum: " + sum + ", count: " + count + ", avg: " + avg();
  }
}