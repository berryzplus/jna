/* Copyright (c) 2013 Minato Hamano, All Rights Reserved
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * <p/>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 */
package test;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.logging.Logger;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;

/**
 * JnaTest
 *
 * a benchmark test for JNA.
 *
 * the preparation of the this code, I refered to the following blog.
 *   http://d.hatena.ne.jp/maachang/20110110 (japanese)
 *
 * @author berryzplus@gmail.com
 */
public final class JnaTest {

    /** a standard style for using c-runtime. */
    public interface CLibrary extends Library {

        CLibrary INSTANCE = (CLibrary) Native.loadLibrary(
                Platform.isWindows() ? "msvcrt" : "c", CLibrary.class);

        Pointer malloc(int length);

        void free(Pointer p);
    }

    /** private logger for class. */
    private static final Logger LOG = Logger.getLogger(JnaTest.class.getName());

    /** set a size, how many bytes will be allocated. */
    private static final int ALLOC_SIZE = 1024;

    /** set a times, allocation will do. */
    private static final int ALLOC_TIMES = 1000000;

    /** set a times, test will do. */
    private static final int TEST_TIMES = 12;

    /** main procedure for test. */
    public static void main(final String[] args) {

        // following assertion should never be failed.
        assert 2 < TEST_TIMES;
        assert 0 < ALLOC_TIMES;
        assert 0 < ALLOC_SIZE;

        final long[] results = new long[TEST_TIMES];

        LOG.info("testing...");

        Pointer p = null;
        for (int n = 0; n < TEST_TIMES; n++) {
            long t = System.currentTimeMillis();

            for (int i = 0; i < ALLOC_TIMES; i++) {
                p = CLibrary.INSTANCE.malloc(ALLOC_SIZE);
                CLibrary.INSTANCE.free(p);
            }
            t = System.currentTimeMillis() - t;

            LOG.fine(MessageFormat.format(
                    "{0}times malloc and free({1}bytes) = time:{2}msec",
                    ALLOC_TIMES, ALLOC_SIZE, t));

            results[n] = t;
        }

        // sort scores to report
        Arrays.sort(results);

        final long min = results[0];
        final long max = results[results.length - 1];
        double average = 0d;
        for (int n = 1; n < TEST_TIMES - 1; n++) {
            average += results[n] - min;
        }
        average = average / (TEST_TIMES - 2) + min;

        LOG.info(MessageFormat.format(
                "{0}times malloc and free({1}bytes) = "
                        + "average:{2}msec, min:{3}msec, max:{4}msec",
                ALLOC_TIMES, ALLOC_SIZE, average, min, max));
    }

}
