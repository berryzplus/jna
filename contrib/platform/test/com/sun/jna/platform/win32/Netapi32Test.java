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

import junit.framework.TestCase;

import com.sun.jna.WString;
import com.sun.jna.platform.win32.DsGetDC.DS_DOMAIN_TRUSTS;
import com.sun.jna.platform.win32.DsGetDC.PDOMAIN_CONTROLLER_INFO;
import com.sun.jna.platform.win32.LMAccess.GROUP_INFO_2;
import com.sun.jna.platform.win32.LMAccess.GROUP_USERS_INFO_0;
import com.sun.jna.platform.win32.LMAccess.LOCALGROUP_USERS_INFO_0;
import com.sun.jna.platform.win32.LMAccess.USER_INFO_1;
import com.sun.jna.platform.win32.NTSecApi.LSA_FOREST_TRUST_RECORD;
import com.sun.jna.platform.win32.NTSecApi.PLSA_FOREST_TRUST_INFORMATION;
import com.sun.jna.platform.win32.NTSecApi.PLSA_FOREST_TRUST_RECORD;
import com.sun.jna.platform.win32.Netapi32Util.User;
import com.sun.jna.platform.win32.Secur32.EXTENDED_NAME_FORMAT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * @author dblock[at]dblock[dot]org
 */
public class Netapi32Test extends TestCase {

    public static void main(final String[] args) {
        junit.textui.TestRunner.run(Netapi32Test.class);
    }

    public void testNetGetJoinInformation() {
		final IntByReference bufferType = new IntByReference();
    	assertEquals(WinError.ERROR_INVALID_PARAMETER, Netapi32.INSTANCE.NetGetJoinInformation(
    			null, null, bufferType));
    	final PointerByReference lpNameBuffer = new PointerByReference();
    	assertEquals(WinError.ERROR_SUCCESS, Netapi32.INSTANCE.NetGetJoinInformation(
    			null, lpNameBuffer, bufferType));
    	assertTrue(lpNameBuffer.getValue().getString(0).length() > 0);
    	assertTrue(bufferType.getValue() > 0);
    	assertEquals(WinError.ERROR_SUCCESS, Netapi32.INSTANCE.NetApiBufferFree(
    			lpNameBuffer.getValue()));
    }

    public void testNetGetLocalGroups() {
    	for(int i = 0; i < 2; i++) {
			final PointerByReference bufptr = new PointerByReference();
			final IntByReference entriesRead = new IntByReference();
			final IntByReference totalEntries = new IntByReference();
	    	assertEquals(LMErr.NERR_Success, Netapi32.INSTANCE.NetLocalGroupEnum(null, i, bufptr,
	    			LMCons.MAX_PREFERRED_LENGTH,
	    			entriesRead,
	    			totalEntries,
	    			null));
	    	assertTrue(entriesRead.getValue() > 0);
	    	assertEquals(totalEntries.getValue(), entriesRead.getValue());
	    	assertEquals(WinError.ERROR_SUCCESS, Netapi32.INSTANCE.NetApiBufferFree(
	    			bufptr.getValue()));
    	}
    }

    public void testNetGetDCName() {
    	final PointerByReference lpNameBuffer = new PointerByReference();
    	final IntByReference BufferType = new IntByReference();
    	assertEquals(LMErr.NERR_Success, Netapi32.INSTANCE.NetGetJoinInformation(null, lpNameBuffer, BufferType));
    	if (BufferType.getValue() == LMJoin.NETSETUP_JOIN_STATUS.NetSetupDomainName) {
	    	final PointerByReference bufptr = new PointerByReference();
	    	assertEquals(LMErr.NERR_Success, Netapi32.INSTANCE.NetGetDCName(null, null, bufptr));
	    	final String dc = bufptr.getValue().getString(0);
	    	assertTrue(dc.length() > 0);
	    	assertEquals(WinError.ERROR_SUCCESS, Netapi32.INSTANCE.NetApiBufferFree(bufptr.getValue()));
    	}
    	assertEquals(WinError.ERROR_SUCCESS, Netapi32.INSTANCE.NetApiBufferFree(lpNameBuffer.getValue()));
    }

    public void testNetUserGetGroups() {
    	final User[] users = Netapi32Util.getUsers();
    	assertTrue(users.length >= 1);
    	final PointerByReference bufptr = new PointerByReference();
    	final IntByReference entriesread = new IntByReference();
    	final IntByReference totalentries = new IntByReference();
    	assertEquals(LMErr.NERR_Success, Netapi32.INSTANCE.NetUserGetGroups(
    			null, users[0].name, 0, bufptr, LMCons.MAX_PREFERRED_LENGTH,
    			entriesread, totalentries));
    	final GROUP_USERS_INFO_0 lgroup = new GROUP_USERS_INFO_0(bufptr.getValue());
    	final GROUP_USERS_INFO_0[] lgroups = (GROUP_USERS_INFO_0[]) lgroup.toArray(entriesread.getValue());
        for (final GROUP_USERS_INFO_0 localGroupInfo : lgroups) {
        	assertTrue(localGroupInfo.grui0_name.length() > 0);
        }
    	assertEquals(LMErr.NERR_Success, Netapi32.INSTANCE.NetApiBufferFree(bufptr.getValue()));
    }

    public void testNetUserGetLocalGroups() {
    	final String currentUser = Secur32Util.getUserNameEx(
				EXTENDED_NAME_FORMAT.NameSamCompatible);
    	final PointerByReference bufptr = new PointerByReference();
    	final IntByReference entriesread = new IntByReference();
    	final IntByReference totalentries = new IntByReference();
    	assertEquals(LMErr.NERR_Success, Netapi32.INSTANCE.NetUserGetLocalGroups(
    			null, currentUser, 0, 0, bufptr, LMCons.MAX_PREFERRED_LENGTH,
    			entriesread, totalentries));
    	final LOCALGROUP_USERS_INFO_0 lgroup = new LOCALGROUP_USERS_INFO_0(bufptr.getValue());
    	final LOCALGROUP_USERS_INFO_0[] lgroups = (LOCALGROUP_USERS_INFO_0[]) lgroup.toArray(entriesread.getValue());
        for (final LOCALGROUP_USERS_INFO_0 localGroupInfo : lgroups) {
        	assertTrue(localGroupInfo.lgrui0_name.length() > 0);
        }
    	assertEquals(LMErr.NERR_Success, Netapi32.INSTANCE.NetApiBufferFree(bufptr.getValue()));
    }

    public void testNetGroupEnum() {
    	final PointerByReference bufptr = new PointerByReference();
    	final IntByReference entriesread = new IntByReference();
    	final IntByReference totalentries = new IntByReference();
    	assertEquals(LMErr.NERR_Success, Netapi32.INSTANCE.NetGroupEnum(
    			null, 2, bufptr, LMCons.MAX_PREFERRED_LENGTH, entriesread, totalentries, null));
    	final GROUP_INFO_2 group = new GROUP_INFO_2(bufptr.getValue());
    	final GROUP_INFO_2[] groups = (GROUP_INFO_2[]) group.toArray(entriesread.getValue());
        for (final GROUP_INFO_2 grpi : groups) {
        	assertTrue(grpi.grpi2_name.length() > 0);
        }
    	assertEquals(LMErr.NERR_Success, Netapi32.INSTANCE.NetApiBufferFree(bufptr.getValue()));
    }

    public void testNetUserEnum() {
    	final PointerByReference bufptr = new PointerByReference();
    	final IntByReference entriesread = new IntByReference();
    	final IntByReference totalentries = new IntByReference();
    	assertEquals(LMErr.NERR_Success, Netapi32.INSTANCE.NetUserEnum(
    			null, 1, 0, bufptr, LMCons.MAX_PREFERRED_LENGTH, entriesread, totalentries, null));
    	final USER_INFO_1 userinfo = new USER_INFO_1(bufptr.getValue());
    	final USER_INFO_1[] userinfos = (USER_INFO_1[]) userinfo.toArray(entriesread.getValue());
        for (final USER_INFO_1 ui : userinfos) {
        	assertTrue(ui.usri1_name.length() > 0);
        }
    	assertEquals(LMErr.NERR_Success, Netapi32.INSTANCE.NetApiBufferFree(bufptr.getValue()));
    }

    public void testNetUserAdd() {
    	final USER_INFO_1 userInfo = new USER_INFO_1();
    	userInfo.usri1_name = new WString("JNANetapi32TestUser");
    	userInfo.usri1_password = new WString("!JNAP$$Wrd0");
    	userInfo.usri1_priv = LMAccess.USER_PRIV_USER;
    	assertEquals(LMErr.NERR_Success, Netapi32.INSTANCE.NetUserAdd(
    			Kernel32Util.getComputerName(), 1, userInfo, null));
    	assertEquals(LMErr.NERR_Success, Netapi32.INSTANCE.NetUserDel(
    			Kernel32Util.getComputerName(), userInfo.usri1_name.toString()));
    }

    public void testNetUserChangePassword() {
    	final USER_INFO_1 userInfo = new USER_INFO_1();
    	userInfo.usri1_name = new WString("JNANetapi32TestUser");
    	userInfo.usri1_password = new WString("!JNAP$$Wrd0");
    	userInfo.usri1_priv = LMAccess.USER_PRIV_USER;
    	assertEquals(LMErr.NERR_Success, Netapi32.INSTANCE.NetUserAdd(
    			Kernel32Util.getComputerName(), 1, userInfo, null));
    	try {
	    	assertEquals(LMErr.NERR_Success, Netapi32.INSTANCE.NetUserChangePassword(
	    			Kernel32Util.getComputerName(), userInfo.usri1_name.toString(), userInfo.usri1_password.toString(),
	    			"!JNAP%%Wrd1"));
    	} finally {
	    	assertEquals(LMErr.NERR_Success, Netapi32.INSTANCE.NetUserDel(
	    			Kernel32Util.getComputerName(), userInfo.usri1_name.toString()));
    	}
    }

    public void testNetUserDel() {
    	assertEquals(LMErr.NERR_UserNotFound, Netapi32.INSTANCE.NetUserDel(
    			Kernel32Util.getComputerName(), "JNANetapi32TestUserDoesntExist"));
    }

    public void testDsGetDcName() {
    	if (Netapi32Util.getJoinStatus() != LMJoin.NETSETUP_JOIN_STATUS.NetSetupDomainName)
    		return;

        final PDOMAIN_CONTROLLER_INFO.ByReference pdci = new PDOMAIN_CONTROLLER_INFO.ByReference();
    	assertEquals(WinError.ERROR_SUCCESS, Netapi32.INSTANCE.DsGetDcName(
    			null, null, null, null, 0, pdci));
    	assertEquals(WinError.ERROR_SUCCESS, Netapi32.INSTANCE.NetApiBufferFree(
    			pdci.getPointer()));
    }

    public void testDsGetForestTrustInformation() {
    	if (Netapi32Util.getJoinStatus() != LMJoin.NETSETUP_JOIN_STATUS.NetSetupDomainName)
    		return;

    	final String domainController = Netapi32Util.getDCName();
    	final PLSA_FOREST_TRUST_INFORMATION.ByReference pfti = new PLSA_FOREST_TRUST_INFORMATION.ByReference();
    	assertEquals(WinError.NO_ERROR, Netapi32.INSTANCE.DsGetForestTrustInformation(
    			domainController, null, 0, pfti));

    	assertTrue(pfti.fti.RecordCount >= 0);

    	for (final PLSA_FOREST_TRUST_RECORD precord : pfti.fti.getEntries()) {
    		final LSA_FOREST_TRUST_RECORD.UNION data = precord.tr.u;
			switch(precord.tr.ForestTrustType) {
			case NTSecApi.ForestTrustTopLevelName:
    		case NTSecApi.ForestTrustTopLevelNameEx:
    			assertTrue(data.TopLevelName.Length > 0);
    			assertTrue(data.TopLevelName.MaximumLength > 0);
    			assertTrue(data.TopLevelName.MaximumLength >= data.TopLevelName.Length);
    			assertTrue(data.TopLevelName.getString().length() > 0);
    			break;
    		case NTSecApi.ForestTrustDomainInfo:
    			assertTrue(data.DomainInfo.DnsName.Length > 0);
    			assertTrue(data.DomainInfo.DnsName.MaximumLength > 0);
    			assertTrue(data.DomainInfo.DnsName.MaximumLength >= data.DomainInfo.DnsName.Length);
    			assertTrue(data.DomainInfo.DnsName.getString().length() > 0);
    			assertTrue(data.DomainInfo.NetbiosName.Length > 0);
    			assertTrue(data.DomainInfo.NetbiosName.MaximumLength > 0);
    			assertTrue(data.DomainInfo.NetbiosName.MaximumLength >= data.DomainInfo.NetbiosName.Length);
    			assertTrue(data.DomainInfo.NetbiosName.getString().length() > 0);
    			assertTrue(Advapi32.INSTANCE.IsValidSid(data.DomainInfo.Sid));
    			assertTrue(Advapi32Util.convertSidToStringSid(data.DomainInfo.Sid).startsWith("S-"));
    			break;
			}
    	}

    	assertEquals(WinError.ERROR_SUCCESS, Netapi32.INSTANCE.NetApiBufferFree(
    			pfti.getPointer()));
    }


    public void testDsEnumerateDomainTrusts() {
    	if (Netapi32Util.getJoinStatus() != LMJoin.NETSETUP_JOIN_STATUS.NetSetupDomainName)
    		return;

    	final IntByReference domainTrustCount = new IntByReference();
        final PointerByReference domainsPointerRef = new PointerByReference();
        assertEquals(WinError.NO_ERROR, Netapi32.INSTANCE.DsEnumerateDomainTrusts(null,
                DsGetDC.DS_DOMAIN_VALID_FLAGS, domainsPointerRef, domainTrustCount));
    	assertTrue(domainTrustCount.getValue() >= 0);

        final DS_DOMAIN_TRUSTS domainTrustRefs = new DS_DOMAIN_TRUSTS(domainsPointerRef.getValue());
        final DS_DOMAIN_TRUSTS[] domainTrusts = (DS_DOMAIN_TRUSTS[]) domainTrustRefs.toArray(new DS_DOMAIN_TRUSTS[domainTrustCount.getValue()]);

    	for(final DS_DOMAIN_TRUSTS trust : domainTrusts) {
			assertTrue(trust.DnsDomainName.length() > 0);
			assertTrue(Advapi32.INSTANCE.IsValidSid(trust.DomainSid));
			assertTrue(Advapi32Util.convertSidToStringSid(trust.DomainSid).startsWith("S-"));
			assertTrue(Ole32Util.getStringFromGUID(trust.DomainGuid).startsWith("{"));
    	}


    	assertEquals(WinError.ERROR_SUCCESS, Netapi32.INSTANCE.NetApiBufferFree(domainTrustRefs.getPointer()));
    }

}
