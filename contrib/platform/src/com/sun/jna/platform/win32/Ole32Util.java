/* Copyright (c) 2010 Daniel Doubrovkine, All Rights Reserved
 *
 * This library is free software; you can redistribute it and/or
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

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.WinNT.HRESULT;

/**
 * Ole32 Utility API.
 * @author dblock[at]dblock.org
 */
public abstract class Ole32Util {

	/**
	 * Convert a string to a GUID.
	 * @param guidString
	 *  String representation of a GUID, including { }.
	 * @return
	 *  A GUID.
	 */
	public static GUID getGUIDFromString(final String guidString) {
		final GUID.ByReference lpiid = new GUID.ByReference();
    	final HRESULT hr = Ole32.INSTANCE.IIDFromString(guidString, lpiid);
    	if (! WinError.S_OK.equals(hr))
            throw new RuntimeException(hr.toString());
    	return lpiid;
	}

	/**
	 * Convert a GUID into a string.
	 * @param guid
	 *  GUID.
	 * @return
	 *  String representation of a GUID.
	 */
	public static String getStringFromGUID(final GUID guid) {
		final GUID.ByReference pguid = new GUID.ByReference(guid.getPointer());
    	final int max = 39;
    	final char[] lpsz = new char[max];
    	final int len = Ole32.INSTANCE.StringFromGUID2(pguid, lpsz, max);
    	if (len == 0)
            throw new RuntimeException("StringFromGUID2");
    	lpsz[len - 1] = 0;
    	return Native.toString(lpsz);
	}

	/**
	 * Generate a new GUID.
	 * @return
	 *  New GUID.
	 */
	public static GUID generateGUID() {
		final GUID.ByReference pguid = new GUID.ByReference();
    	final HRESULT hr = Ole32.INSTANCE.CoCreateGuid(pguid);
    	if (! WinError.S_OK.equals(hr))
            throw new RuntimeException(hr.toString());
    	return pguid;
	}
}
