package com.safjnest.util.lol;

public class Accumulator {
  int sum = 0;
  int count = 0;

  void add(int value) {
    sum += value;
    count++;
  }

  double avg() {
    return count == 0 ? 0 : (double) sum / count;
  }

  public String toString() {
    return "sum: " + sum + ", count: " + count + ", avg: " + avg();
  }
}