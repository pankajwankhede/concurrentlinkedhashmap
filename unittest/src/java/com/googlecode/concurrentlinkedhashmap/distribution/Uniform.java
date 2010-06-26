package com.googlecode.concurrentlinkedhashmap.distribution;

import org.apache.commons.math.random.RandomData;
import org.apache.commons.math.random.RandomDataImpl;

import java.util.concurrent.Callable;

/**
 * Creates a uniform distribution.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
final class Uniform implements Callable<Double> {
  private final RandomData random = new RandomDataImpl();
  private final double lower;
  private final double upper;

  /**
   * A uniform distribution across the open interval.
   *
   * @param lower The lower bound of the interval.
   * @param upper The lower bound of the interval.
   */
  public Uniform(double lower, double upper) {
    this.lower = lower;
    this.upper = upper;
  }

  /**
   * Random value from the open interval (end-points included).
   */
  public Double call() {
    return random.nextUniform(lower, upper);
  }
}