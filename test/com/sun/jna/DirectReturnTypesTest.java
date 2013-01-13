/* Copyright (c) 2009 Timothy Wall, All Rights Reserved
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
package com.sun.jna;

import java.util.Map;

/** Exercise a range of native methods.
 *
 * @author twall@users.sf.net
 */
public class DirectReturnTypesTest extends ReturnTypesTest {

    public static class DirectTestLibrary implements TestLibrary {

        public Object returnObjectArgument(final Object s) {
            throw new IllegalArgumentException(s.getClass().getName());
        }
        public TestObject returnObjectArgument(final TestObject s) {
            throw new IllegalArgumentException(s.getClass().getName());
        }
        public native boolean returnFalse();
        public native boolean returnTrue();
        public native int returnInt32Zero();
        public native int returnInt32Magic();
        public native long returnInt64Zero();
        public native long returnInt64Magic();
        public native NativeLong returnLongZero();
        public native NativeLong returnLongMagic();
        public native float returnFloatZero();
        public native float returnFloatMagic();
        public native double returnDoubleZero();
        public native double returnDoubleMagic();
        public native String returnStringMagic();
        public native WString returnWStringMagic();
        public native SimpleStructure returnStaticTestStructure();
        public native SimpleStructure returnNullTestStructure();
        public native TestSmallStructure.ByValue returnSmallStructureByValue();
        public native TestStructure.ByValue returnStructureByValue();

        public Pointer[] returnPointerArgument(final Pointer[] arg) {throw new UnsupportedOperationException();}
        public String[] returnPointerArgument(final String[] arg) {throw new UnsupportedOperationException();}
        public WString[] returnPointerArgument(final WString[] arg) {throw new UnsupportedOperationException();}

        static {
            Native.register("testlib");
        }
    }

    protected void setUp() {
        lib = new DirectTestLibrary();
    }

    public static class DirectObjectTestLibrary extends DirectTestLibrary {
        public DirectObjectTestLibrary(final Map options) {
            Native.register(getClass(), NativeLibrary.getInstance("testlib", options));
        }
    }

    public static class DirectNativeMappedLibrary implements NativeMappedLibrary {
        public native Custom returnInt32Argument(int arg);
        static {
            Native.register("testlib");
        }
    }
    protected NativeMappedLibrary loadNativeMappedLibrary() {
        return new DirectNativeMappedLibrary();
    }

    // Override not-yet-supported tests
    public void testReturnObject() { }
    public void testReturnPointerArray() { }
    public void testReturnStringArray() { }
    public void testReturnWStringArray() { }

    public static void main(final java.lang.String[] argList) {
        junit.textui.TestRunner.run(DirectReturnTypesTest.class);
    }
}
