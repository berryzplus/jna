package com.sun.jna;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

public class IntegerTypeTest extends TestCase {

    public static class Sized extends IntegerType {
        public Sized() { this(4, 0); }
        public Sized(final int size, final long value) { super(size, value); }
    }

    public void testWriteNull() {
        class NTStruct extends Structure {
            public Sized field;
            protected List getFieldOrder() {
                return Arrays.asList(new String[] { "field" });
            }
        }
        final NTStruct s = new NTStruct();
        assertNotNull("Field not initialized", s.field);
    }
    public void testReadNull() {
        class NTStruct extends Structure {
            public Sized field;
            protected List getFieldOrder() {
                return Arrays.asList(new String[] { "field" });
            }
        }
        final NTStruct s = new NTStruct();
        s.read();
        assertNotNull("Integer type field should be initialized on read", s.field);
    }

    public void testCheckArgumentSize() {
        for (int i=1;i <= 8;i*=2) {
            long value = -1L << i*8-1;
            new Sized(i, value);
            new Sized(i, -1);
            new Sized(i, 0);
            new Sized(i, 1);

            value = 1L << i*8-1;
            new Sized(i, value);
            value = -1L & ~(-1L << i*8);
            new Sized(i, value);

            if (i < 8) {
                try {
                    value = 1L << i*8;
                    new Sized(i, value);
                    fail("Value exceeding size (" + i + ") should fail");
                }
                catch(final IllegalArgumentException e) {
                }
            }
            if (i < 8) {
                try {
                    value = -1L << i*8;
                    new Sized(i, value);
                    fail("Negative value (" + value + ") exceeding size (" + i + ") should fail");
                }
                catch(final IllegalArgumentException e) {
                }
            }
        }
    }

    public void testInitialValue() {
        final long VALUE = 20;
        final NativeLong nl = new NativeLong(VALUE);
        assertEquals("Wrong initial value", VALUE, nl.longValue());
    }

    public void testValueBoundaries() {
        class TestType extends IntegerType {
            public TestType(final int size, final long value) {
                super(size, value);
            }
        }
        try {
            new TestType(1, 0x100L);
            fail("Exception should be thrown if byte value out of bounds");
        }
        catch(final IllegalArgumentException e) {
        }
        try {
            new TestType(2, 0x10000L);
            fail("Exception should be thrown if short value out of bounds");
        }
        catch(final IllegalArgumentException e) {
        }
        try {
            new TestType(4, 0x100000000L);
            fail("Exception should be thrown if int value out of bounds");
        }
        catch(final IllegalArgumentException e) {
        }
    }

    public void testUnsignedValues() {
        class TestType extends IntegerType {
            public TestType(final int size, final long value) {
                super(size, value);
            }
        }
        long VALUE = 0xFF;
        assertEquals("Wrong unsigned byte value", VALUE, new TestType(1, VALUE).longValue());
        VALUE = 0xFFFF;
        assertEquals("Wrong unsigned short value", VALUE, new TestType(2, VALUE).longValue());
        VALUE = 0xFFFFFFFF;
        assertEquals("Wrong unsigned int value", VALUE, new TestType(4, VALUE).longValue());

        class UnsignedTestType extends IntegerType {
            public UnsignedTestType(final int size, final long value) {
                super(size, value, true);
            }
        }
        final UnsignedTestType tt = new UnsignedTestType(4, -1);
        assertTrue("Expected an unsigned value (ctor): " + tt.longValue(), tt.longValue() > 0);
        tt.setValue(-2);
        assertTrue("Expected an unsigned value: " + tt.longValue(), tt.longValue() > 0);
    }

    public static void main(final String[] args) {
        junit.textui.TestRunner.run(IntegerTypeTest.class);
    }
}
