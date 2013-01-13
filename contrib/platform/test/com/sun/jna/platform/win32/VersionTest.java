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

import java.io.File;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import junit.framework.TestCase;

public class VersionTest extends TestCase {

    public static void main(final String[] args) {
        junit.textui.TestRunner.run(VersionTest.class);
    }

    public void testGetFileVersion() {
        final String systemRoot = System.getenv("SystemRoot");
        final File file = new File(systemRoot + "\\regedit.exe");
        if (!file.exists()) {
            fail("Can't obtain file version, file " + file + " is missing");
        }

        final int size = Version.INSTANCE.GetFileVersionInfoSize(file.getAbsolutePath(), null);
        assertTrue(size > 0);

        final Pointer buffer = Kernel32.INSTANCE.LocalAlloc(WinBase.LMEM_ZEROINIT, size);
        assertTrue(!buffer.equals(Pointer.NULL));

        try
        {
            assertTrue(Version.INSTANCE.GetFileVersionInfo(file.getAbsolutePath(), 0, size, buffer));

            final IntByReference outputSize = new IntByReference();
            final PointerByReference pointer = new PointerByReference();

            assertTrue(Version.INSTANCE.VerQueryValue(buffer, "\\", pointer, outputSize));

            final VerRsrc.VS_FIXEDFILEINFO fixedFileInfo = new VerRsrc.VS_FIXEDFILEINFO(pointer.getValue());
            assertTrue(fixedFileInfo.dwFileVersionLS.longValue() > 0);
            assertTrue(fixedFileInfo.dwFileVersionMS.longValue() > 0);
        }
        finally
        {
            Kernel32.INSTANCE.GlobalFree(buffer);
        }
    }
}
