/* Copyright (c) 2007 Timothy Wall, All Rights Reserved
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
package com.sun.jna.platform;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

public class FileUtilsTest extends TestCase {

    public void testMoveToTrash() throws Exception {
        final FileUtils utils = FileUtils.getInstance();
        if (!utils.hasTrash())
            return;

        final File home = new File(System.getProperty("user.home"));
        final File file = File.createTempFile(getName(), ".tmp", home);
        try {
            assertTrue("File should exist", file.exists());
            try {
                utils.moveToTrash(new File[] { file });
            }
            catch(final IOException e) {
                fail(e.toString());
            }
            assertFalse("File still exists after move to trash: " + file, file.exists());
        }
        finally {
            if (file.exists()) {
                file.delete();
            }
        }
    }

    public static void main(final String[] args) {
        junit.textui.TestRunner.run(FileUtilsTest.class);
    }
}
