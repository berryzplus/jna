/* This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 */
package com.sun.jna.platform.win32;

import com.sun.jna.ptr.IntByReference;
import junit.framework.TestCase;

public class MsiTest extends TestCase {

    public static void main(final String[] args) {
        junit.textui.TestRunner.run(MsiTest.class);
    }

    public void testMsiEnumComponents() {
        final char[] componentBuffer = new char[40];
        assertTrue(WinError.ERROR_SUCCESS == Msi.INSTANCE.MsiEnumComponents(new WinDef.DWORD(0), componentBuffer));
        final String component = new String(componentBuffer).trim();
        assertTrue(component.length() > 0);
    }

    public void testMsiGetProductCodeW() {
        final char[] componentBuffer = new char[40];
        assertTrue(WinError.ERROR_SUCCESS == Msi.INSTANCE.MsiEnumComponents(new WinDef.DWORD(0), componentBuffer));
        final String component = new String(componentBuffer).trim();

        final char[] productBuffer = new char[40];
        assertTrue(WinError.ERROR_SUCCESS == Msi.INSTANCE.MsiGetProductCode(component, productBuffer));

        final String product = new String(productBuffer).trim();
        assertTrue(product.length() > 0);
    }

    public void testMsiLocateComponentW() {
        final char[] componentBuffer = new char[40];
        assertTrue(WinError.ERROR_SUCCESS == Msi.INSTANCE.MsiEnumComponents(new WinDef.DWORD(0), componentBuffer));
        final String component = new String(componentBuffer).trim();

        final char[] pathBuffer = new char[WinDef.MAX_PATH];
        final IntByReference pathBufferSize = new IntByReference(pathBuffer.length);
        Msi.INSTANCE.MsiLocateComponent(component, pathBuffer, pathBufferSize);

        final String path = new String(pathBuffer, 0, pathBufferSize.getValue()).trim();
        assertTrue(path.length() > 0);
    }

    public void testMsiGetComponentPathW() {
        final char[] componentBuffer = new char[40];
        assertTrue(WinError.ERROR_SUCCESS == Msi.INSTANCE.MsiEnumComponents(new WinDef.DWORD(0), componentBuffer));
        final String component = new String(componentBuffer).trim();

        final char[] productBuffer = new char[40];
        assertTrue(WinError.ERROR_SUCCESS == Msi.INSTANCE.MsiGetProductCode(component, productBuffer));

        final String product = new String(productBuffer).trim();
        assertTrue(product.length() > 0);

        final char[] pathBuffer = new char[WinDef.MAX_PATH];
        final IntByReference pathBufferSize = new IntByReference(pathBuffer.length);
        Msi.INSTANCE.MsiGetComponentPath(product, component, pathBuffer, pathBufferSize);

        final String path = new String(pathBuffer, 0, pathBufferSize.getValue()).trim();
        assertTrue(path.length() > 0);
    }
}
