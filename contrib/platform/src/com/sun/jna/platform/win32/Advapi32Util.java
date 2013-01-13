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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinNT.ACCESS_ACEStructure;
import com.sun.jna.platform.win32.WinNT.ACL;
import com.sun.jna.platform.win32.WinNT.EVENTLOGRECORD;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinNT.HANDLEByReference;
import com.sun.jna.platform.win32.WinNT.PSID;
import com.sun.jna.platform.win32.WinNT.PSIDByReference;
import com.sun.jna.platform.win32.WinNT.SECURITY_DESCRIPTOR_RELATIVE;
import com.sun.jna.platform.win32.WinNT.SID_AND_ATTRIBUTES;
import com.sun.jna.platform.win32.WinNT.SID_NAME_USE;
import com.sun.jna.platform.win32.WinReg.HKEY;
import com.sun.jna.platform.win32.WinReg.HKEYByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * Advapi32 utility API.
 * @author dblock[at]dblock.org
 */
public abstract class Advapi32Util {
    /**
     * An account.
     */
    public static class Account {
	/**
	 * Account name.
	 */
	public String name;

	/**
	 * Account domain.
	 */
	public String domain;

	/**
	 * Account SID.
	 */
	public byte[] sid;

	/**
	 * String representation of the account SID.
	 */
	public String sidString;

	/**
	 * Account type, one of SID_NAME_USE.
	 */
	public int accountType;

	/**
	 * Fully qualified account name.
	 */
	public String fqn;
    }

    /**
     * Retrieves the name of the user associated with the current thread.
     * @return A user name.
     */
    public static String getUserName() {
	char[] buffer = new char[128];
	final IntByReference len = new IntByReference(buffer.length);
	boolean result = Advapi32.INSTANCE.GetUserNameW(buffer, len);

	if (!result) {
	    switch(Kernel32.INSTANCE.GetLastError()) {
	      case WinError.ERROR_INSUFFICIENT_BUFFER:
		buffer = new char[len.getValue()];
		break;

	      default:
		throw new Win32Exception(Native.getLastError());
	    }

	    result = Advapi32.INSTANCE.GetUserNameW(buffer, len);
	}

	if (!result)
        throw new Win32Exception(Native.getLastError());

	return Native.toString(buffer);
    }

    /**
     * Retrieves a security identifier (SID) for the account on the current system.
     * @param accountName Specifies the account name.
     * @return A structure containing the account SID;
     */
    public static Account getAccountByName(final String accountName) {
	return getAccountByName(null, accountName);
    }

    /**
     * Retrieves a security identifier (SID) for a given account.
     * @param systemName Name of the system.
     * @param accountName Account name.
     * @return A structure containing the account SID.
     */
    public static Account getAccountByName(final String systemName, final String accountName) {
	final IntByReference pSid = new IntByReference(0);
	final IntByReference cchDomainName = new IntByReference(0);
	final PointerByReference peUse = new PointerByReference();

	if (Advapi32.INSTANCE.LookupAccountName(systemName, accountName, null, pSid, null, cchDomainName, peUse))
        throw new RuntimeException("LookupAccountNameW was expected to fail with ERROR_INSUFFICIENT_BUFFER");

	final int rc = Kernel32.INSTANCE.GetLastError();
	if (pSid.getValue() == 0 || rc != WinError.ERROR_INSUFFICIENT_BUFFER)
        throw new Win32Exception(rc);

	final Memory sidMemory = new Memory(pSid.getValue());
	final PSID result = new PSID(sidMemory);
	final char[] referencedDomainName = new char[cchDomainName.getValue() + 1];

	if (! Advapi32.INSTANCE.LookupAccountName(systemName, accountName, result, pSid, referencedDomainName, cchDomainName, peUse))
        throw new Win32Exception(Kernel32.INSTANCE.GetLastError());

	final Account account = new Account();
	account.accountType = peUse.getPointer().getInt(0);
	account.name = accountName;

	final String[] accountNamePartsBs = accountName.split("\\\\", 2);
	final String[] accountNamePartsAt = accountName.split("@", 2);

	if (accountNamePartsBs.length == 2) {
	    account.name = accountNamePartsBs[1];
	} else if (accountNamePartsAt.length == 2) {
	    account.name = accountNamePartsAt[0];
	} else {
	    account.name = accountName;
	}

	if (cchDomainName.getValue() > 0) {
	    account.domain = Native.toString(referencedDomainName);
	    account.fqn = account.domain + "\\" + account.name;
	} else {
	    account.fqn = account.name;
	}

	account.sid = result.getBytes();
	account.sidString = convertSidToStringSid(new PSID(account.sid));
	return account;
    }

    /**
     * Get the account by SID on the local system.
     *
     * @param sid SID.
     * @return Account.
     */
    public static Account getAccountBySid(final PSID sid) {
	return getAccountBySid(null, sid);
    }

    /**
     * Get the account by SID.
     *
     * @param systemName Name of the system.
     * @param sid SID.
     * @return Account.
     */
    public static Account getAccountBySid(final String systemName, final PSID sid) {
	final IntByReference cchName = new IntByReference();
	final IntByReference cchDomainName = new IntByReference();
	final PointerByReference peUse = new PointerByReference();

	if (Advapi32.INSTANCE.LookupAccountSid(null, sid,
		null, cchName, null, cchDomainName, peUse))
        throw new RuntimeException("LookupAccountSidW was expected to fail with ERROR_INSUFFICIENT_BUFFER");

	final int rc = Kernel32.INSTANCE.GetLastError();
	if (cchName.getValue() == 0 || rc != WinError.ERROR_INSUFFICIENT_BUFFER)
        throw new Win32Exception(rc);

	final char[] domainName = new char[cchDomainName.getValue()];
	final char[] name = new char[cchName.getValue()];

	if (! Advapi32.INSTANCE.LookupAccountSid(null, sid,
		name, cchName, domainName, cchDomainName, peUse))
        throw new Win32Exception(Kernel32.INSTANCE.GetLastError());

	final Account account = new Account();
	account.accountType = peUse.getPointer().getInt(0);
	account.name = Native.toString(name);

	if (cchDomainName.getValue() > 0) {
	    account.domain = Native.toString(domainName);
	    account.fqn = account.domain + "\\" + account.name;
	} else {
	    account.fqn = account.name;
	}

	account.sid = sid.getBytes();
	account.sidString = convertSidToStringSid(sid);
	return account;
    }

    /**
     * Convert a security identifier (SID) to a string format suitable for display,
     * storage, or transmission.
     * @param sid SID bytes.
     * @return String SID.
     */
    public static String convertSidToStringSid(final PSID sid) {
	final PointerByReference stringSid = new PointerByReference();
	if (! Advapi32.INSTANCE.ConvertSidToStringSid(sid, stringSid))
        throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
	final String result = stringSid.getValue().getString(0, true);
	Kernel32.INSTANCE.LocalFree(stringSid.getValue());
	return result;
    }

    /**
     * Convert a string representation of a security identifier (SID) to
     * a binary format.
     * @param sidString
     *     String SID.
     * @return SID bytes.
     */
    public static byte[] convertStringSidToSid(final String sidString) {
	final PSIDByReference pSID = new PSIDByReference();
	if (! Advapi32.INSTANCE.ConvertStringSidToSid(sidString, pSID))
        throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
	return pSID.getValue().getBytes();
    }

    /**
     * Compares a SID to a well known SID and returns TRUE if they match.
     * @param sidString
     *  String representation of a SID.
     * @param wellKnownSidType
     *  Member of the WELL_KNOWN_SID_TYPE enumeration to compare with the SID at pSid.
     * @return
     *  True if the SID is of the well-known type, false otherwise.
     */
    public static boolean isWellKnownSid(final String sidString, final int wellKnownSidType) {
	final PSIDByReference pSID = new PSIDByReference();
	if (! Advapi32.INSTANCE.ConvertStringSidToSid(sidString, pSID))
        throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
	return Advapi32.INSTANCE.IsWellKnownSid(pSID.getValue(), wellKnownSidType);
    }

    /**
     * Compares a SID to a well known SID and returns TRUE if they match.
     * @param sidBytes
     *  Byte representation of a SID.
     * @param wellKnownSidType
     *  Member of the WELL_KNOWN_SID_TYPE enumeration to compare with the SID at pSid.
     * @return
     *  True if the SID is of the well-known type, false otherwise.
     */
    public static boolean isWellKnownSid(final byte[] sidBytes, final int wellKnownSidType) {
	final PSID pSID = new PSID(sidBytes);
	return Advapi32.INSTANCE.IsWellKnownSid(pSID, wellKnownSidType);
    }

    /**
     * Get an account name from a string SID on the local machine.
     *
     * @param sidString SID.
     * @return Account.
     */
    public static Account getAccountBySid(final String sidString) {
	return getAccountBySid(null, sidString);
    }

    /**
     * Get an account name from a string SID.
     *
     * @param systemName System name.
     * @param sidString SID.
     * @return Account.
     */
    public static Account getAccountBySid(final String systemName, final String sidString) {
	return getAccountBySid(systemName, new PSID(convertStringSidToSid(sidString)));
    }

    /**
     * This function returns the groups associated with a security token,
     * such as a user token.
     *
     * @param hToken Token.
     * @return Token groups.
     */
    public static Account[] getTokenGroups(final HANDLE hToken) {
	// get token group information size
	final IntByReference tokenInformationLength = new IntByReference();
	if (Advapi32.INSTANCE.GetTokenInformation(hToken,
		WinNT.TOKEN_INFORMATION_CLASS.TokenGroups, null, 0, tokenInformationLength))
        throw new RuntimeException("Expected GetTokenInformation to fail with ERROR_INSUFFICIENT_BUFFER");
	final int rc = Kernel32.INSTANCE.GetLastError();
	if (rc != WinError.ERROR_INSUFFICIENT_BUFFER)
        throw new Win32Exception(rc);
	// get token group information
	final WinNT.TOKEN_GROUPS groups = new WinNT.TOKEN_GROUPS(tokenInformationLength.getValue());
	if (! Advapi32.INSTANCE.GetTokenInformation(hToken,
		WinNT.TOKEN_INFORMATION_CLASS.TokenGroups, groups,
		tokenInformationLength.getValue(), tokenInformationLength))
        throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
	final ArrayList<Account> userGroups = new ArrayList<Account>();
	// make array of names
	for (final SID_AND_ATTRIBUTES sidAndAttribute : groups.getGroups()) {
	    Account group = null;
	    try {
		group = Advapi32Util.getAccountBySid(sidAndAttribute.Sid);
	    } catch(final Exception e) {
		group = new Account();
		group.sid = sidAndAttribute.Sid.getBytes();
		group.sidString = Advapi32Util.convertSidToStringSid(sidAndAttribute.Sid);
		group.name = group.sidString;
		group.fqn = group.sidString;
		group.accountType = SID_NAME_USE.SidTypeGroup;
	    }
	    userGroups.add(group);
	}
	return userGroups.toArray(new Account[0]);
    }

    /**
     * This function returns the information about the user who owns a security token,
     *
     * @param hToken Token.
     * @return Token user.
     */
    public static Account getTokenAccount(final HANDLE hToken) {
	// get token group information size
	final IntByReference tokenInformationLength = new IntByReference();
	if (Advapi32.INSTANCE.GetTokenInformation(hToken,
		WinNT.TOKEN_INFORMATION_CLASS.TokenUser, null, 0, tokenInformationLength))
        throw new RuntimeException("Expected GetTokenInformation to fail with ERROR_INSUFFICIENT_BUFFER");
	final int rc = Kernel32.INSTANCE.GetLastError();
	if (rc != WinError.ERROR_INSUFFICIENT_BUFFER)
        throw new Win32Exception(rc);
	// get token user information
	final WinNT.TOKEN_USER user = new WinNT.TOKEN_USER(tokenInformationLength.getValue());
	if (! Advapi32.INSTANCE.GetTokenInformation(hToken,
		WinNT.TOKEN_INFORMATION_CLASS.TokenUser, user,
		tokenInformationLength.getValue(), tokenInformationLength))
        throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
	return getAccountBySid(user.User.Sid);
    }

    /**
     * Return the group memberships of the currently logged on user.
     * @return An array of groups.
     */
    public static Account[] getCurrentUserGroups() {
	final HANDLEByReference phToken = new HANDLEByReference();
	try {
	    // open thread or process token
	    final HANDLE threadHandle = Kernel32.INSTANCE.GetCurrentThread();
	    if (! Advapi32.INSTANCE.OpenThreadToken(threadHandle,
		    WinNT.TOKEN_DUPLICATE | WinNT.TOKEN_QUERY, true, phToken)) {
		if (WinError.ERROR_NO_TOKEN != Kernel32.INSTANCE.GetLastError())
            throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
		final HANDLE processHandle = Kernel32.INSTANCE.GetCurrentProcess();
		if (! Advapi32.INSTANCE.OpenProcessToken(processHandle,
			WinNT.TOKEN_DUPLICATE | WinNT.TOKEN_QUERY, phToken))
            throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
	    }
	    return getTokenGroups(phToken.getValue());
	} finally {
	    if (phToken.getValue() != WinBase.INVALID_HANDLE_VALUE) {
		if (! Kernel32.INSTANCE.CloseHandle(phToken.getValue()))
            throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
	    }
	}
    }

    /**
     * Checks whether a registry key exists.
     * @param root
     *  HKEY_LOCAL_MACHINE, etc.
     * @param key
     *  Path to the registry key.
     * @return
     *  True if the key exists.
     */
    public static boolean registryKeyExists(final HKEY root, final String key) {
	final HKEYByReference phkKey = new HKEYByReference();
	final int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, key, 0, WinNT.KEY_READ, phkKey);
	switch(rc) {
	case WinError.ERROR_SUCCESS:
	    Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
	    return true;
	case WinError.ERROR_FILE_NOT_FOUND:
	    return false;
	default:
	    throw new Win32Exception(rc);
	}
    }

    /**
     * Checks whether a registry value exists.
     * @param root
     *  HKEY_LOCAL_MACHINE, etc.
     * @param key
     *  Registry key path.
     * @param value
     *  Value name.
     * @return
     *  True if the value exists.
     */
    public static boolean registryValueExists(final HKEY root, final String key, final String value) {
	final HKEYByReference phkKey = new HKEYByReference();
	int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, key, 0, WinNT.KEY_READ, phkKey);
	try {
	    switch(rc) {
	    case WinError.ERROR_SUCCESS:
		break;
	    case WinError.ERROR_FILE_NOT_FOUND:
		return false;
	    default:
		throw new Win32Exception(rc);
	    }
	    final IntByReference lpcbData = new IntByReference();
	    final IntByReference lpType = new IntByReference();
	    rc = Advapi32.INSTANCE.RegQueryValueEx(
		    phkKey.getValue(), value, 0, lpType, (char[]) null, lpcbData);
	    switch(rc) {
	    case WinError.ERROR_SUCCESS:
	    case WinError.ERROR_MORE_DATA:
	    case WinError.ERROR_INSUFFICIENT_BUFFER:
		return true;
	    case WinError.ERROR_FILE_NOT_FOUND:
		return false;
	    default:
		throw new Win32Exception(rc);
	    }
	} finally {
	    if (phkKey.getValue() != WinBase.INVALID_HANDLE_VALUE) {
		rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
		if (rc != WinError.ERROR_SUCCESS)
            throw new Win32Exception(rc);
	    }
	}
    }

    /**
     * Get a registry REG_SZ value.
     * @param root
     *  Root key.
     * @param key
     *  Registry path.
     * @param value
     *  Name of the value to retrieve.
     * @return
     *  String value.
     */
    public static String registryGetStringValue(final HKEY root, final String key, final String value) {
	final HKEYByReference phkKey = new HKEYByReference();
	int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, key, 0, WinNT.KEY_READ, phkKey);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
	try {
	    final IntByReference lpcbData = new IntByReference();
	    final IntByReference lpType = new IntByReference();
	    rc = Advapi32.INSTANCE.RegQueryValueEx(
		    phkKey.getValue(), value, 0, lpType, (char[]) null, lpcbData);
	    if (rc != WinError.ERROR_SUCCESS && rc != WinError.ERROR_INSUFFICIENT_BUFFER)
            throw new Win32Exception(rc);
	    if (lpType.getValue() != WinNT.REG_SZ && lpType.getValue() != WinNT.REG_EXPAND_SZ)
            throw new RuntimeException("Unexpected registry type " + lpType.getValue() + ", expected REG_SZ or REG_EXPAND_SZ");
	    final char[] data = new char[lpcbData.getValue()];
	    rc = Advapi32.INSTANCE.RegQueryValueEx(
		    phkKey.getValue(), value, 0, lpType, data, lpcbData);
	    if (rc != WinError.ERROR_SUCCESS && rc != WinError.ERROR_INSUFFICIENT_BUFFER)
            throw new Win32Exception(rc);
	    return Native.toString(data);
	} finally {
	    rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
	    if (rc != WinError.ERROR_SUCCESS)
            throw new Win32Exception(rc);
	}
    }

    /**
     * Get a registry REG_EXPAND_SZ value.
     * @param root
     *  Root key.
     * @param key
     *  Registry path.
     * @param value
     *  Name of the value to retrieve.
     * @return
     *  String value.
     */
    public static String registryGetExpandableStringValue(final HKEY root, final String key, final String value) {
	final HKEYByReference phkKey = new HKEYByReference();
	int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, key, 0, WinNT.KEY_READ, phkKey);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
	try {
	    final IntByReference lpcbData = new IntByReference();
	    final IntByReference lpType = new IntByReference();
	    rc = Advapi32.INSTANCE.RegQueryValueEx(
		    phkKey.getValue(), value, 0, lpType, (char[]) null, lpcbData);
	    if (rc != WinError.ERROR_SUCCESS && rc != WinError.ERROR_INSUFFICIENT_BUFFER)
            throw new Win32Exception(rc);
	    if (lpType.getValue() != WinNT.REG_EXPAND_SZ)
            throw new RuntimeException("Unexpected registry type " + lpType.getValue() + ", expected REG_SZ");
	    final char[] data = new char[lpcbData.getValue()];
	    rc = Advapi32.INSTANCE.RegQueryValueEx(
		    phkKey.getValue(), value, 0, lpType, data, lpcbData);
	    if (rc != WinError.ERROR_SUCCESS && rc != WinError.ERROR_INSUFFICIENT_BUFFER)
            throw new Win32Exception(rc);
	    return Native.toString(data);
	} finally {
	    rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
	    if (rc != WinError.ERROR_SUCCESS)
            throw new Win32Exception(rc);
	}
    }

    /**
     * Get a registry REG_MULTI_SZ value.
     * @param root
     *  Root key.
     * @param key
     *  Registry path.
     * @param value
     *  Name of the value to retrieve.
     * @return
     *  String value.
     */
    public static String[] registryGetStringArray(final HKEY root, final String key, final String value) {
	final HKEYByReference phkKey = new HKEYByReference();
	int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, key, 0, WinNT.KEY_READ, phkKey);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
	try {
	    final IntByReference lpcbData = new IntByReference();
	    final IntByReference lpType = new IntByReference();
	    rc = Advapi32.INSTANCE.RegQueryValueEx(
		    phkKey.getValue(), value, 0, lpType, (char[]) null, lpcbData);
	    if (rc != WinError.ERROR_SUCCESS && rc != WinError.ERROR_INSUFFICIENT_BUFFER)
            throw new Win32Exception(rc);
	    if (lpType.getValue() != WinNT.REG_MULTI_SZ)
            throw new RuntimeException("Unexpected registry type " + lpType.getValue() + ", expected REG_SZ");
	    final Memory data = new Memory(lpcbData.getValue());
	    rc = Advapi32.INSTANCE.RegQueryValueEx(
		    phkKey.getValue(), value, 0, lpType, data, lpcbData);
	    if (rc != WinError.ERROR_SUCCESS && rc != WinError.ERROR_INSUFFICIENT_BUFFER)
            throw new Win32Exception(rc);
	    final ArrayList<String> result = new ArrayList<String>();
	    int offset = 0;
	    while(offset < data.size()) {
		final String s = data.getString(offset, true);
		offset += s.length() * Native.WCHAR_SIZE;
		offset += Native.WCHAR_SIZE;
		if (s.length() == 0 && offset == data.size()) {
		    // skip the final NULL
		} else {
		    result.add(s);
		}
	    }
	    return result.toArray(new String[0]);
	} finally {
	    rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
	    if (rc != WinError.ERROR_SUCCESS)
            throw new Win32Exception(rc);
	}
    }

    /**
     * Get a registry REG_BINARY value.
     * @param root
     *  Root key.
     * @param key
     *  Registry path.
     * @param value
     *  Name of the value to retrieve.
     * @return
     *  String value.
     */
    public static byte[] registryGetBinaryValue(final HKEY root, final String key, final String value) {
	final HKEYByReference phkKey = new HKEYByReference();
	int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, key, 0, WinNT.KEY_READ, phkKey);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
	try {
	    final IntByReference lpcbData = new IntByReference();
	    final IntByReference lpType = new IntByReference();
	    rc = Advapi32.INSTANCE.RegQueryValueEx(
		    phkKey.getValue(), value, 0, lpType, (char[]) null, lpcbData);
	    if (rc != WinError.ERROR_SUCCESS && rc != WinError.ERROR_INSUFFICIENT_BUFFER)
            throw new Win32Exception(rc);
	    if (lpType.getValue() != WinNT.REG_BINARY)
            throw new RuntimeException("Unexpected registry type " + lpType.getValue() + ", expected REG_BINARY");
	    final byte[] data = new byte[lpcbData.getValue()];
	    rc = Advapi32.INSTANCE.RegQueryValueEx(
		    phkKey.getValue(), value, 0, lpType, data, lpcbData);
	    if (rc != WinError.ERROR_SUCCESS && rc != WinError.ERROR_INSUFFICIENT_BUFFER)
            throw new Win32Exception(rc);
	    return data;
	} finally {
	    rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
	    if (rc != WinError.ERROR_SUCCESS)
            throw new Win32Exception(rc);
	}
    }

    /**
     * Get a registry DWORD value.
     * @param root
     *  Root key.
     * @param key
     *  Registry key path.
     * @param value
     *  Name of the value to retrieve.
     * @return
     *  Integer value.
     */
    public static int registryGetIntValue(final HKEY root, final String key, final String value) {
	final HKEYByReference phkKey = new HKEYByReference();
	int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, key, 0, WinNT.KEY_READ, phkKey);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
	try {
	    final IntByReference lpcbData = new IntByReference();
	    final IntByReference lpType = new IntByReference();
	    rc = Advapi32.INSTANCE.RegQueryValueEx(
		    phkKey.getValue(), value, 0, lpType, (char[]) null, lpcbData);
	    if (rc != WinError.ERROR_SUCCESS && rc != WinError.ERROR_INSUFFICIENT_BUFFER)
            throw new Win32Exception(rc);
	    if (lpType.getValue() != WinNT.REG_DWORD)
            throw new RuntimeException("Unexpected registry type " + lpType.getValue() + ", expected REG_DWORD");
	    final IntByReference data = new IntByReference();
	    rc = Advapi32.INSTANCE.RegQueryValueEx(
		    phkKey.getValue(), value, 0, lpType, data, lpcbData);
	    if (rc != WinError.ERROR_SUCCESS && rc != WinError.ERROR_INSUFFICIENT_BUFFER)
            throw new Win32Exception(rc);
	    return data.getValue();
	} finally {
	    rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
	    if (rc != WinError.ERROR_SUCCESS)
            throw new Win32Exception(rc);
	}
    }

    /**
     * Get a registry QWORD value.
     * @param root
     *  Root key.
     * @param key
     *  Registry key path.
     * @param value
     *  Name of the value to retrieve.
     * @return
     *  Integer value.
     */
    public static long registryGetLongValue(final HKEY root, final String key, final String value) {
	final HKEYByReference phkKey = new HKEYByReference();
	int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, key, 0, WinNT.KEY_READ, phkKey);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
	try {
	    final IntByReference lpcbData = new IntByReference();
	    final IntByReference lpType = new IntByReference();
	    rc = Advapi32.INSTANCE.RegQueryValueEx(
		    phkKey.getValue(), value, 0, lpType, (char[]) null, lpcbData);
	    if (rc != WinError.ERROR_SUCCESS && rc != WinError.ERROR_INSUFFICIENT_BUFFER)
            throw new Win32Exception(rc);
	    if (lpType.getValue() != WinNT.REG_QWORD)
            throw new RuntimeException("Unexpected registry type " + lpType.getValue() + ", expected REG_QWORD");
	    final LongByReference data = new LongByReference();
	    rc = Advapi32.INSTANCE.RegQueryValueEx(
		    phkKey.getValue(), value, 0, lpType, data, lpcbData);
	    if (rc != WinError.ERROR_SUCCESS && rc != WinError.ERROR_INSUFFICIENT_BUFFER)
            throw new Win32Exception(rc);
	    return data.getValue();
	} finally {
	    rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
	    if (rc != WinError.ERROR_SUCCESS)
            throw new Win32Exception(rc);
	}
    }

    /**
     * Create a registry key.
     * @param hKey
     *  Parent key.
     * @param keyName
     *  Key name.
     * @return
     *  True if the key was created, false otherwise.
     */
    public static boolean registryCreateKey(final HKEY hKey, final String keyName) {
	final HKEYByReference phkResult = new HKEYByReference();
	final IntByReference lpdwDisposition = new IntByReference();
	int rc = Advapi32.INSTANCE.RegCreateKeyEx(hKey, keyName, 0, null, WinNT.REG_OPTION_NON_VOLATILE,
		WinNT.KEY_READ, null, phkResult, lpdwDisposition);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
	rc = Advapi32.INSTANCE.RegCloseKey(phkResult.getValue());
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
	return WinNT.REG_CREATED_NEW_KEY == lpdwDisposition.getValue();
    }

    /**
     * Create a registry key.
     * @param root
     *  Root key.
     * @param parentPath
     *  Path to an existing registry key.
     * @param keyName
     *  Key name.
     * @return
     *  True if the key was created, false otherwise.
     */
    public static boolean registryCreateKey(final HKEY root, final String parentPath, final String keyName) {
	final HKEYByReference phkKey = new HKEYByReference();
	int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, parentPath, 0, WinNT.KEY_CREATE_SUB_KEY, phkKey);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
	try {
	    return registryCreateKey(phkKey.getValue(), keyName);
	} finally {
	    rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
	    if (rc != WinError.ERROR_SUCCESS)
            throw new Win32Exception(rc);
	}
    }

    /**
     * Set an integer value in registry.
     * @param hKey
     *  Parent key.
     * @param name
     *  Value name.
     * @param value
     *  Value to write to registry.
     */
    public static void registrySetIntValue(final HKEY hKey, final String name, final int value) {
	final byte[] data = new byte[4];
	data[0] = (byte)(value & 0xff);
	data[1] = (byte)(value >> 8 & 0xff);
	data[2] = (byte)(value >> 16 & 0xff);
	data[3] = (byte)(value >> 24 & 0xff);
	final int rc = Advapi32.INSTANCE.RegSetValueEx(hKey, name, 0, WinNT.REG_DWORD, data, 4);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
    }

    /**
     * Set an integer value in registry.
     * @param root
     *  Root key.
     * @param keyPath
     *  Path to an existing registry key.
     * @param name
     *  Value name.
     * @param value
     *  Value to write to registry.
     */
    public static void registrySetIntValue(final HKEY root, final String keyPath, final String name, final int value) {
	final HKEYByReference phkKey = new HKEYByReference();
	int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, keyPath, 0, WinNT.KEY_READ | WinNT.KEY_WRITE, phkKey);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
	try {
	    registrySetIntValue(phkKey.getValue(), name, value);
	} finally {
	    rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
	    if (rc != WinError.ERROR_SUCCESS)
            throw new Win32Exception(rc);
	}
    }

    /**
     * Set a long value in registry.
     * @param hKey
     *  Parent key.
     * @param name
     *  Value name.
     * @param value
     *  Value to write to registry.
     */
    public static void registrySetLongValue(final HKEY hKey, final String name, final long value) {
	final byte[] data = new byte[8];
	data[0] = (byte)(value & 0xff);
	data[1] = (byte)(value >> 8 & 0xff);
	data[2] = (byte)(value >> 16 & 0xff);
	data[3] = (byte)(value >> 24 & 0xff);
	data[4] = (byte)(value >> 32 & 0xff);
	data[5] = (byte)(value >> 40 & 0xff);
	data[6] = (byte)(value >> 48 & 0xff);
	data[7] = (byte)(value >> 56 & 0xff);
	final int rc = Advapi32.INSTANCE.RegSetValueEx(hKey, name, 0, WinNT.REG_QWORD, data, 8);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
    }

    /**
     * Set a long value in registry.
     * @param root
     *  Root key.
     * @param keyPath
     *  Path to an existing registry key.
     * @param name
     *  Value name.
     * @param value
     *  Value to write to registry.
     */
    public static void registrySetLongValue(final HKEY root, final String keyPath, final String name, final long value) {
	final HKEYByReference phkKey = new HKEYByReference();
	int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, keyPath, 0, WinNT.KEY_READ | WinNT.KEY_WRITE, phkKey);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
	try {
	    registrySetLongValue(phkKey.getValue(), name, value);
	} finally {
	    rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
	    if (rc != WinError.ERROR_SUCCESS)
            throw new Win32Exception(rc);
	}
    }

    /**
     * Set a string value in registry.
     * @param hKey
     *  Parent key.
     * @param name
     *  Value name.
     * @param value
     *  Value to write to registry.
     */
    public static void registrySetStringValue(final HKEY hKey, final String name, final String value) {
	final char[] data = Native.toCharArray(value);
	final int rc = Advapi32.INSTANCE.RegSetValueEx(hKey, name, 0, WinNT.REG_SZ,
		data, data.length * Native.WCHAR_SIZE);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
    }

    /**
     * Set a string value in registry.
     * @param root
     *  Root key.
     * @param keyPath
     *  Path to an existing registry key.
     * @param name
     *  Value name.
     * @param value
     *  Value to write to registry.
     */
    public static void registrySetStringValue(final HKEY root, final String keyPath, final String name, final String value) {
	final HKEYByReference phkKey = new HKEYByReference();
	int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, keyPath, 0, WinNT.KEY_READ | WinNT.KEY_WRITE, phkKey);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
	try {
	    registrySetStringValue(phkKey.getValue(), name, value);
	} finally {
	    rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
	    if (rc != WinError.ERROR_SUCCESS)
            throw new Win32Exception(rc);
	}
    }

    /**
     * Set an expandable string value in registry.
     * @param hKey
     *  Parent key.
     * @param name
     *  Value name.
     * @param value
     *  Value to write to registry.
     */
    public static void registrySetExpandableStringValue(final HKEY hKey, final String name, final String value) {
	final char[] data = Native.toCharArray(value);
	final int rc = Advapi32.INSTANCE.RegSetValueEx(hKey, name, 0, WinNT.REG_EXPAND_SZ,
		data, data.length * Native.WCHAR_SIZE);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
    }

    /**
     * Set a string value in registry.
     * @param root
     *  Root key.
     * @param keyPath
     *  Path to an existing registry key.
     * @param name
     *  Value name.
     * @param value
     *  Value to write to registry.
     */
    public static void registrySetExpandableStringValue(final HKEY root, final String keyPath, final String name, final String value) {
	final HKEYByReference phkKey = new HKEYByReference();
	int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, keyPath, 0, WinNT.KEY_READ | WinNT.KEY_WRITE, phkKey);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
	try {
	    registrySetExpandableStringValue(phkKey.getValue(), name, value);
	} finally {
	    rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
	    if (rc != WinError.ERROR_SUCCESS)
            throw new Win32Exception(rc);
	}
    }

    /**
     * Set a string array value in registry.
     * @param hKey
     *  Parent key.
     * @param name
     *  Name.
     * @param arr
     *  Array of strings to write to registry.
     */
    public static void registrySetStringArray(final HKEY hKey, final String name, final String[] arr) {
	int size = 0;
	for(final String s : arr) {
	    size += s.length() * Native.WCHAR_SIZE;
	    size += Native.WCHAR_SIZE;
	}
	size += Native.WCHAR_SIZE;

	int offset = 0;
	final Memory data = new Memory(size);
	for(final String s : arr) {
	    data.setString(offset, s, true);
	    offset += s.length() * Native.WCHAR_SIZE;
	    offset += Native.WCHAR_SIZE;
	}
	for (int i=0; i < Native.WCHAR_SIZE; i++) {
	    data.setByte(offset++, (byte)0);
	}

	final int rc = Advapi32.INSTANCE.RegSetValueEx(hKey, name, 0, WinNT.REG_MULTI_SZ,
		data.getByteArray(0, size), size);

	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
    }

    /**
     * Set a string array value in registry.
     * @param root
     *  Root key.
     * @param keyPath
     *  Path to an existing registry key.
     * @param name
     *  Value name.
     * @param arr
     *  Array of strings to write to registry.
     */
    public static void registrySetStringArray(final HKEY root, final String keyPath, final String name, final String[] arr) {
	final HKEYByReference phkKey = new HKEYByReference();
	int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, keyPath, 0, WinNT.KEY_READ | WinNT.KEY_WRITE, phkKey);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
	try {
	    registrySetStringArray(phkKey.getValue(), name, arr);
	} finally {
	    rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
	    if (rc != WinError.ERROR_SUCCESS)
            throw new Win32Exception(rc);
	}
    }

    /**
     * Set a binary value in registry.
     * @param hKey
     *  Parent key.
     * @param name
     *  Value name.
     * @param data
     *  Data to write to registry.
     */
    public static void registrySetBinaryValue(final HKEY hKey, final String name, final byte[] data) {
	final int rc = Advapi32.INSTANCE.RegSetValueEx(hKey, name, 0, WinNT.REG_BINARY, data, data.length);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
    }

    /**
     * Set a binary value in registry.
     * @param root
     *  Root key.
     * @param keyPath
     *  Path to an existing registry key.
     * @param name
     *  Value name.
     * @param data
     *  Data to write to registry.
     */
    public static void registrySetBinaryValue(final HKEY root, final String keyPath, final String name, final byte[] data) {
	final HKEYByReference phkKey = new HKEYByReference();
	int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, keyPath, 0, WinNT.KEY_READ | WinNT.KEY_WRITE, phkKey);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
	try {
	    registrySetBinaryValue(phkKey.getValue(), name, data);
	} finally {
	    rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
	    if (rc != WinError.ERROR_SUCCESS)
            throw new Win32Exception(rc);
	}
    }

    /**
     * Delete a registry key.
     * @param hKey
     *  Parent key.
     * @param keyName
     *  Name of the key to delete.
     */
    public static void registryDeleteKey(final HKEY hKey, final String keyName) {
	final int rc = Advapi32.INSTANCE.RegDeleteKey(hKey, keyName);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
    }

    /**
     * Delete a registry key.
     * @param root
     *  Root key.
     * @param keyPath
     *  Path to an existing registry key.
     * @param keyName
     *  Name of the key to delete.
     */
    public static void registryDeleteKey(final HKEY root, final String keyPath, final String keyName) {
	final HKEYByReference phkKey = new HKEYByReference();
	int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, keyPath, 0, WinNT.KEY_READ | WinNT.KEY_WRITE, phkKey);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
	try {
	    registryDeleteKey(phkKey.getValue(), keyName);
	} finally {
	    rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
	    if (rc != WinError.ERROR_SUCCESS)
            throw new Win32Exception(rc);
	}
    }

    /**
     * Delete a registry value.
     * @param hKey
     *  Parent key.
     * @param valueName
     *  Name of the value to delete.
     */
    public static void registryDeleteValue(final HKEY hKey, final String valueName) {
	final int rc = Advapi32.INSTANCE.RegDeleteValue(hKey, valueName);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
    }

    /**
     * Delete a registry value.
     * @param root
     *  Root key.
     * @param keyPath
     *  Path to an existing registry key.
     * @param valueName
     *  Name of the value to delete.
     */
    public static void registryDeleteValue(final HKEY root, final String keyPath, final String valueName) {
	final HKEYByReference phkKey = new HKEYByReference();
	int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, keyPath, 0, WinNT.KEY_READ | WinNT.KEY_WRITE, phkKey);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
	try {
	    registryDeleteValue(phkKey.getValue(), valueName);
	} finally {
	    rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
	    if (rc != WinError.ERROR_SUCCESS)
            throw new Win32Exception(rc);
	}
    }

    /**
     * Get names of the registry key's sub-keys.
     * @param hKey
     *  Registry key.
     * @return
     *  Array of registry key names.
     */
    public static String[] registryGetKeys(final HKEY hKey) {
	final IntByReference lpcSubKeys = new IntByReference();
	final IntByReference lpcMaxSubKeyLen = new IntByReference();
	int rc = Advapi32.INSTANCE.RegQueryInfoKey(hKey, null, null, null,
		lpcSubKeys, lpcMaxSubKeyLen, null, null, null, null, null, null);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
	final ArrayList<String> keys = new ArrayList<String>(lpcSubKeys.getValue());
	final char[] name = new char[lpcMaxSubKeyLen.getValue() + 1];
	for (int i = 0; i < lpcSubKeys.getValue(); i++) {
	    final IntByReference lpcchValueName = new IntByReference(lpcMaxSubKeyLen.getValue() + 1);
	    rc = Advapi32.INSTANCE.RegEnumKeyEx(hKey, i, name, lpcchValueName,
		    null, null, null, null);
	    if (rc != WinError.ERROR_SUCCESS)
            throw new Win32Exception(rc);
	    keys.add(Native.toString(name));
	}
	return keys.toArray(new String[0]);
    }

    /**
     * Get names of the registry key's sub-keys.
     * @param root
     *  Root key.
     * @param keyPath
     *  Path to a registry key.
     * @return
     *  Array of registry key names.
     */
    public static String[] registryGetKeys(final HKEY root, final String keyPath) {
	final HKEYByReference phkKey = new HKEYByReference();
	int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, keyPath, 0, WinNT.KEY_READ, phkKey);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
	try {
	    return registryGetKeys(phkKey.getValue());
	} finally {
	    rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
	    if (rc != WinError.ERROR_SUCCESS)
            throw new Win32Exception(rc);
	}
    }

    /**
     * Get a table of registry values.
     * @param hKey
     *  Registry key.
     * @return
     *  Table of values.
     */
    public static TreeMap<String, Object> registryGetValues(final HKEY hKey) {
	final IntByReference lpcValues = new IntByReference();
	final IntByReference lpcMaxValueNameLen = new IntByReference();
	final IntByReference lpcMaxValueLen = new IntByReference();
	int rc = Advapi32.INSTANCE.RegQueryInfoKey(hKey, null, null, null, null,
		null, null, lpcValues, lpcMaxValueNameLen, lpcMaxValueLen, null, null);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
	final TreeMap<String, Object> keyValues = new TreeMap<String, Object>();
	final char[] name = new char[lpcMaxValueNameLen.getValue() + 1];
	final byte[] data = new byte[lpcMaxValueLen.getValue()];
	for (int i = 0; i < lpcValues.getValue(); i++) {
	    final IntByReference lpcchValueName = new IntByReference(lpcMaxValueNameLen.getValue() + 1);
	    final IntByReference lpcbData = new IntByReference(lpcMaxValueLen.getValue());
	    final IntByReference lpType = new IntByReference();
	    rc = Advapi32.INSTANCE.RegEnumValue(hKey, i, name, lpcchValueName, null,
		    lpType, data, lpcbData);
	    if (rc != WinError.ERROR_SUCCESS)
            throw new Win32Exception(rc);

	    final String nameString = Native.toString(name);

	    if(lpcbData.getValue() == 0) {
	        switch (lpType.getValue()) {
            case WinNT.REG_BINARY: {
                keyValues.put(nameString, new byte[0]);
                break;
            }
            case WinNT.REG_SZ:
            case WinNT.REG_EXPAND_SZ: {
                keyValues.put(nameString, new char[0]);
                break;
            }
            case WinNT.REG_MULTI_SZ: {
                keyValues.put(nameString, new String[0]);
                break;
            }
            case WinNT.REG_NONE: {
                keyValues.put(nameString, null);
                break;
            }
	        default:
	            throw new RuntimeException("Unsupported empty type: " + lpType.getValue());
	        }
	        continue;
	    }

	    final Memory byteData = new Memory(lpcbData.getValue());
	    byteData.write(0, data, 0, lpcbData.getValue());

	    switch(lpType.getValue()) {
	      case WinNT.REG_QWORD: {
		keyValues.put(nameString, byteData.getLong(0));
		break;
	      }
	      case WinNT.REG_DWORD: {
		keyValues.put(nameString, byteData.getInt(0));
		break;
	      }
	      case WinNT.REG_SZ:
	      case WinNT.REG_EXPAND_SZ: {
		keyValues.put(nameString, byteData.getString(0, true));
		break;
	      }
	      case WinNT.REG_BINARY: {
		keyValues.put(nameString, byteData.getByteArray(0, lpcbData.getValue()));
		break;
	      }
	      case WinNT.REG_MULTI_SZ: {
		final Memory stringData = new Memory(lpcbData.getValue());
		stringData.write(0, data, 0, lpcbData.getValue());
		final ArrayList<String> result = new ArrayList<String>();
		int offset = 0;
		while(offset < stringData.size()) {
		    final String s = stringData.getString(offset, true);
		    offset += s.length() * Native.WCHAR_SIZE;
		    offset += Native.WCHAR_SIZE;
		    if (s.length() == 0 && offset == stringData.size()) {
			// skip the final NULL
		    } else {
			result.add(s);
		    }
		}
		keyValues.put(nameString, result.toArray(new String[0]));
		break;
	      }
	      default:
		throw new RuntimeException("Unsupported type: " + lpType.getValue());
	    }
	}
	return keyValues;
    }

    /**
     * Get a table of registry values.
     * @param root
     *  Registry root.
     * @param keyPath
     *  Regitry key path.
     * @return
     *  Table of values.
     */
    public static TreeMap<String, Object> registryGetValues(final HKEY root, final String keyPath) {
	final HKEYByReference phkKey = new HKEYByReference();
	int rc = Advapi32.INSTANCE.RegOpenKeyEx(root, keyPath, 0, WinNT.KEY_READ, phkKey);
	if (rc != WinError.ERROR_SUCCESS)
        throw new Win32Exception(rc);
	try {
	    return registryGetValues(phkKey.getValue());
	} finally {
	    rc = Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
	    if (rc != WinError.ERROR_SUCCESS)
            throw new Win32Exception(rc);
	}
    }

    /**
     * Converts a map of environment variables to an environment block suitable
     * for {@link Advapi32#CreateProcessAsUser}. This environment block consists
     * of null-terminated blocks of null-terminated strings. Each string is in the
     * following form: name=value\0
     * @param environment Environment variables
     * @return A environment block
     */
    public static String getEnvironmentBlock(final Map<String, String> environment) {
	final StringBuffer out = new StringBuffer();
	for (final Entry<String, String> entry: environment.entrySet()) {
	    if (entry.getValue() != null) {
		out.append(entry.getKey() + "=" + entry.getValue() + "\0");
	    }
	}
	return out.toString() + "\0";
    }

    /**
     * Event log types.
     */
    public static enum EventLogType {
	Error,
	Warning,
	Informational,
	AuditSuccess,
	AuditFailure
    }

    /**
     * An event log record.
     */
    public static class EventLogRecord {
	private EVENTLOGRECORD _record = null;
	private final String _source;
	private byte[] _data;
	private String[] _strings;

	/**
	 * Raw record data.
	 * @return
	 *  EVENTLOGRECORD.
	 */
	public EVENTLOGRECORD getRecord() {
	    return _record;
	}

	/**
	 * Event Id.
	 * @return
	 *  Integer.
	 */
	public int getEventId() {
	    return _record.EventID.intValue();
	}

	/**
	 * Event source.
	 * @return
	 *  String.
	 */
	public String getSource() {
	    return _source;
	}

	/**
	 * Status code for the facility, part of the Event ID.
	 * @return
	 *  Status code.
	 */
	public int getStatusCode() {
	    return _record.EventID.intValue() & 0xFFFF;
	}

	/**
	 * Record number of the record. This value can be used with the EVENTLOG_SEEK_READ flag in
	 * the ReadEventLog function to begin reading at a specified record.
	 * @return
	 *  Integer.
	 */
	public int getRecordNumber() {
	    return _record.RecordNumber.intValue();
	}

	/**
	 * Record length, with data.
	 * @return
	 *  Number of bytes in the record including data.
	 */
	public int getLength() {
	    return _record.Length.intValue();
	}

	/**
	 * Strings associated with this event.
	 * @return
	 *  Array of strings or null.
	 */
	public String[] getStrings() {
	    return _strings;
	}

	/**
	 * Event log type.
	 * @return
	 *  Event log type.
	 */
	public EventLogType getType() {
	    switch(_record.EventType.intValue()) {
	    case WinNT.EVENTLOG_SUCCESS:
	    case WinNT.EVENTLOG_INFORMATION_TYPE:
		return EventLogType.Informational;
	    case WinNT.EVENTLOG_AUDIT_FAILURE:
		return EventLogType.AuditFailure;
	    case WinNT.EVENTLOG_AUDIT_SUCCESS:
		return EventLogType.AuditSuccess;
	    case WinNT.EVENTLOG_ERROR_TYPE:
		return EventLogType.Error;
	    case WinNT.EVENTLOG_WARNING_TYPE:
		return EventLogType.Warning;
	    default:
		throw new RuntimeException("Invalid type: " + _record.EventType.intValue());
	    }
	}

	/**
	 * Raw data associated with the record.
	 * @return
	 *  Array of bytes or null.
	 */
	public byte[] getData() {
	    return _data;
	}

	public EventLogRecord(final Pointer pevlr) {
	    _record = new EVENTLOGRECORD(pevlr);
	    _source = pevlr.getString(_record.size(), true);
	    // data
	    if (_record.DataLength.intValue() > 0) {
		_data = pevlr.getByteArray(_record.DataOffset.intValue(),
			_record.DataLength.intValue());
	    }
	    // strings
	    if (_record.NumStrings.intValue() > 0) {
		final ArrayList<String> strings = new ArrayList<String>();
		int count = _record.NumStrings.intValue();
		long offset = _record.StringOffset.intValue();
		while(count > 0) {
		    final String s = pevlr.getString(offset, true);
		    strings.add(s);
		    offset += s.length() * Native.WCHAR_SIZE;
		    offset += Native.WCHAR_SIZE;
		    count--;
		}
		_strings = strings.toArray(new String[0]);
	    }
	}
    }

    /**
     * An iterator for Event Log entries.
     */
    public static class EventLogIterator
	implements Iterable<EventLogRecord>, Iterator<EventLogRecord> {

	private HANDLE _h = null;
	private Memory _buffer = new Memory(1024 * 64); // memory buffer to store events
	private boolean _done = false; // no more events
	private int _dwRead = 0; // number of bytes remaining in the current buffer
	private Pointer _pevlr = null; // pointer to the current record
	private int _flags = WinNT.EVENTLOG_FORWARDS_READ;

	public EventLogIterator(final String sourceName) {
	    this(null, sourceName, WinNT.EVENTLOG_FORWARDS_READ);
	}

	public EventLogIterator(final String serverName, final String sourceName, final int flags) {
	    _flags = flags;
	    _h = Advapi32.INSTANCE.OpenEventLog(serverName, sourceName);
	    if (_h == null)
            throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
	}

	private boolean read() {
	    // finished or bytes remain, don't read any new data
	    if (_done || _dwRead > 0)
            return false;

	    final IntByReference pnBytesRead = new IntByReference();
	    final IntByReference pnMinNumberOfBytesNeeded = new IntByReference();

	    if (! Advapi32.INSTANCE.ReadEventLog(_h,
		    WinNT.EVENTLOG_SEQUENTIAL_READ | _flags,
		    0, _buffer, (int) _buffer.size(), pnBytesRead, pnMinNumberOfBytesNeeded)) {

		final int rc = Kernel32.INSTANCE.GetLastError();

		// not enough bytes in the buffer, resize
		if (rc == WinError.ERROR_INSUFFICIENT_BUFFER) {
		    _buffer = new Memory(pnMinNumberOfBytesNeeded.getValue());

		    if (! Advapi32.INSTANCE.ReadEventLog(_h,
			    WinNT.EVENTLOG_SEQUENTIAL_READ | _flags,
			    0, _buffer, (int) _buffer.size(), pnBytesRead, pnMinNumberOfBytesNeeded))
                throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
		} else {
		    // read failed, no more entries or error
		    close();
		    if (rc != WinError.ERROR_HANDLE_EOF)
                throw new Win32Exception(rc);
		    return false;
		}
	    }

	    _dwRead = pnBytesRead.getValue();
	    _pevlr = _buffer;
	    return true;
	}

	/**
	 * Call close() in the case when the caller needs to abandon the iterator before
	 * the iteration completes.
	 */
	public void close() {
	    _done = true;
	    if (_h != null) {
		if (! Advapi32.INSTANCE.CloseEventLog(_h))
            throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
		_h = null;
	    }
	}

	//@Override - @todo restore Override annotation after we move to source level 1.6
	public Iterator<EventLogRecord> iterator() {
	    return this;
	}

	//@Override - @todo restore Override annotation after we move to source level 1.6
	public boolean hasNext() {
	    read();
	    return ! _done;
	}

	//@Override - @todo restore Override annotation after we move to source level 1.6
	public EventLogRecord next() {
	    read();
	    final EventLogRecord record = new EventLogRecord(_pevlr);
	    _dwRead -= record.getLength();
	    _pevlr = _pevlr.share(record.getLength());
	    return record;
	}

	//@Override - @todo restore Override annotation after we move to source level 1.6
	public void remove() {
	}
    }

    public static ACCESS_ACEStructure[] getFileSecurity(final String fileName, final boolean compact) {
	final int infoType = WinNT.DACL_SECURITY_INFORMATION;
	int nLength = 1024;
	boolean repeat = false;
	Memory memory = null;

	do {
	    repeat = false;
	    memory = new Memory(nLength);
	    final IntByReference lpnSize = new IntByReference();
	    final boolean succeded = Advapi32.INSTANCE.GetFileSecurity(new WString(fileName), infoType, memory, nLength, lpnSize);

	    if (!succeded) {
		final int lastError = Kernel32.INSTANCE.GetLastError();
		memory.clear();
		if (WinError.ERROR_INSUFFICIENT_BUFFER != lastError)
            throw new Win32Exception(lastError);
	    }
	    final int lengthNeeded = lpnSize.getValue();
	    if (nLength < lengthNeeded) {
		repeat = true;
		nLength = lengthNeeded;
		memory.clear();
	    }
	} while (repeat);


	final SECURITY_DESCRIPTOR_RELATIVE sdr = new WinNT.SECURITY_DESCRIPTOR_RELATIVE(memory);
	memory.clear();
	final ACL dacl = sdr.getDiscretionaryACL();
	final ACCESS_ACEStructure[] aceStructures = dacl.getACEStructures();

	if (compact) {
	    final Map<String, ACCESS_ACEStructure> aceMap = new HashMap<String, ACCESS_ACEStructure>();
	    for (final ACCESS_ACEStructure aceStructure : aceStructures) {
		final boolean inherted = (aceStructure.AceFlags & WinNT.VALID_INHERIT_FLAGS) != 0;
		final String key = aceStructure.getSidString() + "/" + inherted + "/" + aceStructure.getClass().getName();
		final ACCESS_ACEStructure aceStructure2 = aceMap.get(key);
		if (aceStructure2 != null) {
		    int accessMask = aceStructure2.Mask;
		    accessMask = accessMask | aceStructure.Mask;
		    aceStructure2.Mask = accessMask;
		} else {
		    aceMap.put(key, aceStructure);
		}
	    }
	    return aceMap.values().toArray(new ACCESS_ACEStructure[aceMap.size()]);
	}
	return aceStructures;
    }
}
