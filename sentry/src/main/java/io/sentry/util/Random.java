/*
 * Copyright (c) 1995, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package io.sentry.util;

import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.ApiStatus;

/**
 * A simplified version of {@link java.util.Random} that we use for sampling, which is much faster
 * than {@link java.security.SecureRandom}. This is necessary so that some security tools do not
 * flag our Random usage as potentially insecure.
 */
@ApiStatus.Internal
public final class Random implements java.io.Serializable {
  /** use serialVersionUID from JDK 1.1 for interoperability */
  private static final long serialVersionUID = 3905348978240129619L;

  /**
   * The internal state associated with this pseudorandom number generator. (The specs for the
   * methods in this class describe the ongoing computation of this value.)
   */
  private final AtomicLong seed;

  private static final long multiplier = 0x5DEECE66DL;
  private static final long addend = 0xBL;
  private static final long mask = (1L << 48) - 1;

  private static final double DOUBLE_UNIT = 0x1.0p-53; // 1.0 / (1L << 53)

  // IllegalArgumentException messages
  static final String BadBound = "bound must be positive";

  /**
   * Creates a new random number generator. This constructor sets the seed of the random number
   * generator to a value very likely to be distinct from any other invocation of this constructor.
   */
  public Random() {
    this(seedUniquifier() ^ System.nanoTime());
  }

  private static long seedUniquifier() {
    // L'Ecuyer, "Tables of Linear Congruential Generators of
    // Different Sizes and Good Lattice Structure", 1999
    for (; ; ) {
      long current = seedUniquifier.get();
      long next = current * 1181783497276652981L;
      if (seedUniquifier.compareAndSet(current, next)) return next;
    }
  }

  private static final AtomicLong seedUniquifier = new AtomicLong(8682522807148012L);

  /**
   * Creates a new random number generator using a single {@code long} seed. The seed is the initial
   * value of the internal state of the pseudorandom number generator which is maintained by method
   * {@link #next}.
   *
   * <p>The invocation {@code new Random(seed)} is equivalent to:
   *
   * <pre>{@code
   * Random rnd = new Random();
   * rnd.setSeed(seed);
   * }</pre>
   *
   * @param seed the initial seed
   * @see #setSeed(long)
   */
  public Random(long seed) {
    if (getClass() == Random.class) this.seed = new AtomicLong(initialScramble(seed));
    else {
      // subclass might have overriden setSeed
      this.seed = new AtomicLong();
      setSeed(seed);
    }
  }

  private static long initialScramble(long seed) {
    return (seed ^ multiplier) & mask;
  }

  /**
   * Sets the seed of this random number generator using a single {@code long} seed. The general
   * contract of {@code setSeed} is that it alters the state of this random number generator object
   * so as to be in exactly the same state as if it had just been created with the argument {@code
   * seed} as a seed. The method {@code setSeed} is implemented by class {@code Random} by
   * atomically updating the seed to
   *
   * <pre>{@code (seed ^ 0x5DEECE66DL) & ((1L << 48) - 1)}</pre>
   *
   * <p>The implementation of {@code setSeed} by class {@code Random} happens to use only 48 bits of
   * the given seed. In general, however, an overriding method may use all 64 bits of the {@code
   * long} argument as a seed value.
   *
   * @param seed the initial seed
   */
  public synchronized void setSeed(long seed) {
    this.seed.set(initialScramble(seed));
  }

  /**
   * Generates the next pseudorandom number. Subclasses should override this, as this is used by all
   * other methods.
   *
   * <p>The general contract of {@code next} is that it returns an {@code int} value and if the
   * argument {@code bits} is between {@code 1} and {@code 32} (inclusive), then that many low-order
   * bits of the returned value will be (approximately) independently chosen bit values, each of
   * which is (approximately) equally likely to be {@code 0} or {@code 1}. The method {@code next}
   * is implemented by class {@code Random} by atomically updating the seed to
   *
   * <pre>{@code (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1)}</pre>
   *
   * and returning
   *
   * <pre>{@code (int)(seed >>> (48 - bits))}.</pre>
   *
   * This is a linear congruential pseudorandom number generator, as defined by D. H. Lehmer and
   * described by Donald E. Knuth in <i>The Art of Computer Programming,</i> Volume 2:
   * <i>Seminumerical Algorithms</i>, section 3.2.1.
   *
   * @param bits random bits
   * @return the next pseudorandom value from this random number generator's sequence
   * @since 1.1
   */
  private int next(int bits) {
    long oldseed, nextseed;
    AtomicLong seed = this.seed;
    do {
      oldseed = seed.get();
      nextseed = (oldseed * multiplier + addend) & mask;
    } while (!seed.compareAndSet(oldseed, nextseed));
    return (int) (nextseed >>> (48 - bits));
  }

  /**
   * Generates random bytes and places them into a user-supplied byte array. The number of random
   * bytes produced is equal to the length of the byte array.
   *
   * <p>The method {@code nextBytes} is implemented by class {@code Random} as if by:
   *
   * <pre>{@code
   * public void nextBytes(byte[] bytes) {
   *   for (int i = 0; i < bytes.length; )
   *     for (int rnd = nextInt(), n = Math.min(bytes.length - i, 4);
   *          n-- > 0; rnd >>= 8)
   *       bytes[i++] = (byte)rnd;
   * }
   * }</pre>
   *
   * @param bytes the byte array to fill with random bytes
   * @throws NullPointerException if the byte array is null
   * @since 1.1
   */
  public void nextBytes(byte[] bytes) {
    for (int i = 0, len = bytes.length; i < len; )
      for (int rnd = nextInt(), n = Math.min(len - i, Integer.SIZE / Byte.SIZE);
          n-- > 0;
          rnd >>= Byte.SIZE) bytes[i++] = (byte) rnd;
  }

  /**
   * The form of nextLong used by LongStream Spliterators. If origin is greater than bound, acts as
   * unbounded form of nextLong, else as bounded form.
   *
   * @param origin the least value, unless greater than bound
   * @param bound the upper bound (exclusive), must not equal origin
   * @return a pseudorandom value
   */
  final long internalNextLong(long origin, long bound) {
    long r = nextLong();
    if (origin < bound) {
      long n = bound - origin, m = n - 1;
      if ((n & m) == 0L) // power of two
      r = (r & m) + origin;
      else if (n > 0L) { // reject over-represented candidates
        for (long u = r >>> 1; // ensure nonnegative
            u + m - (r = u % n) < 0L; // rejection check
            u = nextLong() >>> 1) // retry
          ;
        r += origin;
      } else { // range not representable as long
        while (r < origin || r >= bound) r = nextLong();
      }
    }
    return r;
  }

  /**
   * The form of nextInt used by IntStream Spliterators. For the unbounded case: uses nextInt(). For
   * the bounded case with representable range: uses nextInt(int bound) For the bounded case with
   * unrepresentable range: uses nextInt()
   *
   * @param origin the least value, unless greater than bound
   * @param bound the upper bound (exclusive), must not equal origin
   * @return a pseudorandom value
   */
  final int internalNextInt(int origin, int bound) {
    if (origin < bound) {
      int n = bound - origin;
      if (n > 0) {
        return nextInt(n) + origin;
      } else { // range not representable as int
        int r;
        do {
          r = nextInt();
        } while (r < origin || r >= bound);
        return r;
      }
    } else {
      return nextInt();
    }
  }

  /**
   * The form of nextDouble used by DoubleStream Spliterators.
   *
   * @param origin the least value, unless greater than bound
   * @param bound the upper bound (exclusive), must not equal origin
   * @return a pseudorandom value
   */
  final double internalNextDouble(double origin, double bound) {
    double r = nextDouble();
    if (origin < bound) {
      r = r * (bound - origin) + origin;
      if (r >= bound) // correct for rounding
      r = Double.longBitsToDouble(Double.doubleToLongBits(bound) - 1);
    }
    return r;
  }

  /**
   * Returns the next pseudorandom, uniformly distributed {@code int} value from this random number
   * generator's sequence. The general contract of {@code nextInt} is that one {@code int} value is
   * pseudorandomly generated and returned. All 2<sup>32</sup> possible {@code int} values are
   * produced with (approximately) equal probability.
   *
   * <p>The method {@code nextInt} is implemented by class {@code Random} as if by:
   *
   * <pre>{@code
   * public int nextInt() {
   *   return next(32);
   * }
   * }</pre>
   *
   * @return the next pseudorandom, uniformly distributed {@code int} value from this random number
   *     generator's sequence
   */
  public int nextInt() {
    return next(32);
  }

  /**
   * Returns a pseudorandom, uniformly distributed {@code int} value between 0 (inclusive) and the
   * specified value (exclusive), drawn from this random number generator's sequence. The general
   * contract of {@code nextInt} is that one {@code int} value in the specified range is
   * pseudorandomly generated and returned. All {@code bound} possible {@code int} values are
   * produced with (approximately) equal probability. The method {@code nextInt(int bound)} is
   * implemented by class {@code Random} as if by:
   *
   * <pre>{@code
   * public int nextInt(int bound) {
   *   if (bound <= 0)
   *     throw new IllegalArgumentException("bound must be positive");
   *
   *   if ((bound & -bound) == bound)  // i.e., bound is a power of 2
   *     return (int)((bound * (long)next(31)) >> 31);
   *
   *   int bits, val;
   *   do {
   *       bits = next(31);
   *       val = bits % bound;
   *   } while (bits - val + (bound-1) < 0);
   *   return val;
   * }
   * }</pre>
   *
   * <p>The hedge "approximately" is used in the foregoing description only because the next method
   * is only approximately an unbiased source of independently chosen bits. If it were a perfect
   * source of randomly chosen bits, then the algorithm shown would choose {@code int} values from
   * the stated range with perfect uniformity.
   *
   * <p>The algorithm is slightly tricky. It rejects values that would result in an uneven
   * distribution (due to the fact that 2^31 is not divisible by n). The probability of a value
   * being rejected depends on n. The worst case is n=2^30+1, for which the probability of a reject
   * is 1/2, and the expected number of iterations before the loop terminates is 2.
   *
   * <p>The algorithm treats the case where n is a power of two specially: it returns the correct
   * number of high-order bits from the underlying pseudo-random number generator. In the absence of
   * special treatment, the correct number of <i>low-order</i> bits would be returned. Linear
   * congruential pseudo-random number generators such as the one implemented by this class are
   * known to have short periods in the sequence of values of their low-order bits. Thus, this
   * special case greatly increases the length of the sequence of values returned by successive
   * calls to this method if n is a small power of two.
   *
   * @param bound the upper bound (exclusive). Must be positive.
   * @return the next pseudorandom, uniformly distributed {@code int} value between zero (inclusive)
   *     and {@code bound} (exclusive) from this random number generator's sequence
   * @throws IllegalArgumentException if bound is not positive
   * @since 1.2
   */
  public int nextInt(int bound) {
    if (bound <= 0) throw new IllegalArgumentException(BadBound);

    int r = next(31);
    int m = bound - 1;
    if ((bound & m) == 0) // i.e., bound is a power of 2
    r = (int) ((bound * (long) r) >> 31);
    else {
      for (int u = r; u - (r = u % bound) + m < 0; u = next(31))
        ;
    }
    return r;
  }

  /**
   * Returns the next pseudorandom, uniformly distributed {@code long} value from this random number
   * generator's sequence. The general contract of {@code nextLong} is that one {@code long} value
   * is pseudorandomly generated and returned.
   *
   * <p>The method {@code nextLong} is implemented by class {@code Random} as if by:
   *
   * <pre>{@code
   * public long nextLong() {
   *   return ((long)next(32) << 32) + next(32);
   * }
   * }</pre>
   *
   * Because class {@code Random} uses a seed with only 48 bits, this algorithm will not return all
   * possible {@code long} values.
   *
   * @return the next pseudorandom, uniformly distributed {@code long} value from this random number
   *     generator's sequence
   */
  @SuppressWarnings("UnnecessaryParentheses")
  public long nextLong() {
    // it's okay that the bottom word remains signed.
    return ((long) (next(32)) << 32) + next(32);
  }

  /**
   * Returns the next pseudorandom, uniformly distributed {@code boolean} value from this random
   * number generator's sequence. The general contract of {@code nextBoolean} is that one {@code
   * boolean} value is pseudorandomly generated and returned. The values {@code true} and {@code
   * false} are produced with (approximately) equal probability.
   *
   * <p>The method {@code nextBoolean} is implemented by class {@code Random} as if by:
   *
   * <pre>{@code
   * public boolean nextBoolean() {
   *   return next(1) != 0;
   * }
   * }</pre>
   *
   * @return the next pseudorandom, uniformly distributed {@code boolean} value from this random
   *     number generator's sequence
   * @since 1.2
   */
  public boolean nextBoolean() {
    return next(1) != 0;
  }

  /**
   * Returns the next pseudorandom, uniformly distributed {@code float} value between {@code 0.0}
   * and {@code 1.0} from this random number generator's sequence.
   *
   * <p>The general contract of {@code nextFloat} is that one {@code float} value, chosen
   * (approximately) uniformly from the range {@code 0.0f} (inclusive) to {@code 1.0f} (exclusive),
   * is pseudorandomly generated and returned. All 2<sup>24</sup> possible {@code float} values of
   * the form <i>m&nbsp;x&nbsp;</i>2<sup>-24</sup>, where <i>m</i> is a positive integer less than
   * 2<sup>24</sup>, are produced with (approximately) equal probability.
   *
   * <p>The method {@code nextFloat} is implemented by class {@code Random} as if by:
   *
   * <pre>{@code
   * public float nextFloat() {
   *   return next(24) / ((float)(1 << 24));
   * }
   * }</pre>
   *
   * <p>The hedge "approximately" is used in the foregoing description only because the next method
   * is only approximately an unbiased source of independently chosen bits. If it were a perfect
   * source of randomly chosen bits, then the algorithm shown would choose {@code float} values from
   * the stated range with perfect uniformity.
   *
   * <p>[In early versions of Java, the result was incorrectly calculated as:
   *
   * <pre>{@code
   * return next(30) / ((float)(1 << 30));
   * }</pre>
   *
   * This might seem to be equivalent, if not better, but in fact it introduced a slight
   * nonuniformity because of the bias in the rounding of floating-point numbers: it was slightly
   * more likely that the low-order bit of the significand would be 0 than that it would be 1.]
   *
   * @return the next pseudorandom, uniformly distributed {@code float} value between {@code 0.0}
   *     and {@code 1.0} from this random number generator's sequence
   */
  public float nextFloat() {
    return next(24) / ((float) (1 << 24));
  }

  /**
   * Returns the next pseudorandom, uniformly distributed {@code double} value between {@code 0.0}
   * and {@code 1.0} from this random number generator's sequence.
   *
   * <p>The general contract of {@code nextDouble} is that one {@code double} value, chosen
   * (approximately) uniformly from the range {@code 0.0d} (inclusive) to {@code 1.0d} (exclusive),
   * is pseudorandomly generated and returned.
   *
   * <p>The method {@code nextDouble} is implemented by class {@code Random} as if by:
   *
   * <pre>{@code
   * public double nextDouble() {
   *   return (((long)next(26) << 27) + next(27))
   *     / (double)(1L << 53);
   * }
   * }</pre>
   *
   * <p>The hedge "approximately" is used in the foregoing description only because the {@code next}
   * method is only approximately an unbiased source of independently chosen bits. If it were a
   * perfect source of randomly chosen bits, then the algorithm shown would choose {@code double}
   * values from the stated range with perfect uniformity.
   *
   * <p>[In early versions of Java, the result was incorrectly calculated as:
   *
   * <pre>{@code
   * return (((long)next(27) << 27) + next(27))
   *   / (double)(1L << 54);
   * }</pre>
   *
   * This might seem to be equivalent, if not better, but in fact it introduced a large
   * nonuniformity because of the bias in the rounding of floating-point numbers: it was three times
   * as likely that the low-order bit of the significand would be 0 than that it would be 1! This
   * nonuniformity probably doesn't matter much in practice, but we strive for perfection.]
   *
   * @return the next pseudorandom, uniformly distributed {@code double} value between {@code 0.0}
   *     and {@code 1.0} from this random number generator's sequence
   * @see Math#random
   */
  @SuppressWarnings("UnnecessaryParentheses")
  public double nextDouble() {
    return (((long) (next(26)) << 27) + next(27)) * DOUBLE_UNIT;
  }
}
