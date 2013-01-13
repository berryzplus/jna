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

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Sspi.CredHandle;
import com.sun.jna.platform.win32.Sspi.CtxtHandle;
import com.sun.jna.platform.win32.Sspi.PSecPkgInfo;
import com.sun.jna.platform.win32.Sspi.SecBufferDesc;
import com.sun.jna.platform.win32.Sspi.SecPkgInfo;
import com.sun.jna.platform.win32.Sspi.TimeStamp;
import com.sun.jna.platform.win32.WinNT.HANDLEByReference;
import com.sun.jna.ptr.IntByReference;

/**
 * @author dblock[at]dblock[dot]org
 */
public class Secur32Test extends TestCase {

    public static void main(final String[] args) {
        junit.textui.TestRunner.run(Secur32Test.class);
    }

    public void testGetUserNameEx() {
    	final IntByReference len = new IntByReference();
    	Secur32.INSTANCE.GetUserNameEx(
    			Secur32.EXTENDED_NAME_FORMAT.NameSamCompatible, null, len);
    	assertTrue(len.getValue() > 0);
    	final char[] buffer = new char[len.getValue() + 1];
    	assertTrue(Secur32.INSTANCE.GetUserNameEx(
    			Secur32.EXTENDED_NAME_FORMAT.NameSamCompatible, buffer, len));
    	final String username = Native.toString(buffer);
    	assertTrue(username.length() > 0);
    }

    public void testAcquireCredentialsHandle() {
    	final CredHandle phCredential = new CredHandle();
    	final TimeStamp ptsExpiry = new TimeStamp();
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.AcquireCredentialsHandle(
    			null, "Negotiate", Sspi.SECPKG_CRED_OUTBOUND, null, null, null,
    			null, phCredential, ptsExpiry));
    	assertTrue(phCredential.dwLower != null);
    	assertTrue(phCredential.dwUpper != null);
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.FreeCredentialsHandle(
    			phCredential));
    }

    public void testAcquireCredentialsHandleInvalidPackage() {
    	final CredHandle phCredential = new CredHandle();
    	final TimeStamp ptsExpiry = new TimeStamp();
    	assertEquals(WinError.SEC_E_SECPKG_NOT_FOUND, Secur32.INSTANCE.AcquireCredentialsHandle(
    			null, "PackageDoesntExist", Sspi.SECPKG_CRED_OUTBOUND, null, null, null,
    			null, phCredential, ptsExpiry));
    }

    public void testInitializeSecurityContext() {
    	final CredHandle phCredential = new CredHandle();
    	final TimeStamp ptsExpiry = new TimeStamp();
    	// acquire a credentials handle
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.AcquireCredentialsHandle(
    			null, "Negotiate", Sspi.SECPKG_CRED_OUTBOUND, null, null, null,
    			null, phCredential, ptsExpiry));
    	// initialize security context
    	final CtxtHandle phNewContext = new CtxtHandle();
    	final SecBufferDesc pbToken = new SecBufferDesc(Sspi.SECBUFFER_TOKEN, Sspi.MAX_TOKEN_SIZE);
    	final IntByReference pfContextAttr = new IntByReference();
    	final int rc = Secur32.INSTANCE.InitializeSecurityContext(phCredential, null,
    			Advapi32Util.getUserName(), Sspi.ISC_REQ_CONNECTION, 0,
    			Sspi.SECURITY_NATIVE_DREP, null, 0, phNewContext, pbToken,
    			pfContextAttr, null);
    	assertTrue(rc == WinError.SEC_I_CONTINUE_NEEDED || rc == WinError.SEC_E_OK);
    	assertTrue(phNewContext.dwLower != null);
    	assertTrue(phNewContext.dwUpper != null);
    	assertTrue(pbToken.pBuffers[0].getBytes().length > 0);
    	// release
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.DeleteSecurityContext(
    			phNewContext));
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.FreeCredentialsHandle(
    			phCredential));
    }

    public void testAcceptSecurityContext() {
    	// client ----------- acquire outbound credential handle
    	final CredHandle phClientCredential = new CredHandle();
    	final TimeStamp ptsClientExpiry = new TimeStamp();
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.AcquireCredentialsHandle(
    			null, "Negotiate", Sspi.SECPKG_CRED_OUTBOUND, null, null, null,
    			null, phClientCredential, ptsClientExpiry));
    	// client ----------- security context
    	final CtxtHandle phClientContext = new CtxtHandle();
    	final IntByReference pfClientContextAttr = new IntByReference();
		// server ----------- acquire inbound credential handle
    	final CredHandle phServerCredential = new CredHandle();
    	final TimeStamp ptsServerExpiry = new TimeStamp();
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.AcquireCredentialsHandle(
    			null, "Negotiate", Sspi.SECPKG_CRED_INBOUND, null, null, null,
    			null, phServerCredential, ptsServerExpiry));
    	// server ----------- security context
		final CtxtHandle phServerContext = new CtxtHandle();
    	SecBufferDesc pbServerToken = null;
    	final IntByReference pfServerContextAttr = new IntByReference();
    	int clientRc = WinError.SEC_I_CONTINUE_NEEDED;
    	int serverRc = WinError.SEC_I_CONTINUE_NEEDED;
    	do {
        	// client ----------- initialize security context, produce a client token
    		// client token returned is always new
        	final SecBufferDesc pbClientToken = new SecBufferDesc(Sspi.SECBUFFER_TOKEN, Sspi.MAX_TOKEN_SIZE);
        	if (clientRc == WinError.SEC_I_CONTINUE_NEEDED) {
	        	// server token is empty the first time
	        	final SecBufferDesc pbServerTokenCopy = pbServerToken == null
	        		? null : new SecBufferDesc(Sspi.SECBUFFER_TOKEN, pbServerToken.getBytes());
	        	clientRc = Secur32.INSTANCE.InitializeSecurityContext(
	    				phClientCredential,
	    				phClientContext.isNull() ? null : phClientContext,
	        			Advapi32Util.getUserName(),
	        			Sspi.ISC_REQ_CONNECTION,
	        			0,
	        			Sspi.SECURITY_NATIVE_DREP,
	        			pbServerTokenCopy,
	        			0,
	        			phClientContext,
	        			pbClientToken,
	        			pfClientContextAttr,
	        			null);
	    		assertTrue(clientRc == WinError.SEC_I_CONTINUE_NEEDED || clientRc == WinError.SEC_E_OK);
        	}
        	// server ----------- accept security context, produce a server token
    		if (serverRc == WinError.SEC_I_CONTINUE_NEEDED) {
	    		pbServerToken = new SecBufferDesc(Sspi.SECBUFFER_TOKEN, Sspi.MAX_TOKEN_SIZE);
	    		final SecBufferDesc pbClientTokenByValue = new SecBufferDesc(Sspi.SECBUFFER_TOKEN, pbClientToken.getBytes());
	    		serverRc = Secur32.INSTANCE.AcceptSecurityContext(phServerCredential,
	    				phServerContext.isNull() ? null : phServerContext,
	    				pbClientTokenByValue,
	    				Sspi.ISC_REQ_CONNECTION,
	    				Sspi.SECURITY_NATIVE_DREP,
	    				phServerContext,
	    				pbServerToken,
	    				pfServerContextAttr,
	    				ptsServerExpiry);
	    		assertTrue(serverRc == WinError.SEC_I_CONTINUE_NEEDED || serverRc == WinError.SEC_E_OK);
    		}
    	} while(serverRc != WinError.SEC_E_OK || clientRc != WinError.SEC_E_OK);
    	// release server context
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.DeleteSecurityContext(
    			phServerContext));
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.FreeCredentialsHandle(
    			phServerCredential));
    	// release client context
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.DeleteSecurityContext(
    			phClientContext));
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.FreeCredentialsHandle(
    			phClientCredential));
    }

    public void testImpersonateRevertSecurityContext() {
    	// client ----------- acquire outbound credential handle
    	final CredHandle phClientCredential = new CredHandle();
    	final TimeStamp ptsClientExpiry = new TimeStamp();
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.AcquireCredentialsHandle(
    			null, "Negotiate", Sspi.SECPKG_CRED_OUTBOUND, null, null, null,
    			null, phClientCredential, ptsClientExpiry));
    	// client ----------- security context
    	final CtxtHandle phClientContext = new CtxtHandle();
    	final IntByReference pfClientContextAttr = new IntByReference();
		// server ----------- acquire inbound credential handle
    	final CredHandle phServerCredential = new CredHandle();
    	final TimeStamp ptsServerExpiry = new TimeStamp();
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.AcquireCredentialsHandle(
    			null, "Negotiate", Sspi.SECPKG_CRED_INBOUND, null, null, null,
    			null, phServerCredential, ptsServerExpiry));
    	// server ----------- security context
		final CtxtHandle phServerContext = new CtxtHandle();
    	SecBufferDesc pbServerToken = null;
    	final IntByReference pfServerContextAttr = new IntByReference();
    	int clientRc = WinError.SEC_I_CONTINUE_NEEDED;
    	int serverRc = WinError.SEC_I_CONTINUE_NEEDED;
    	do {
        	// client ----------- initialize security context, produce a client token
    		// client token returned is always new
        	final SecBufferDesc pbClientToken = new SecBufferDesc(Sspi.SECBUFFER_TOKEN, Sspi.MAX_TOKEN_SIZE);
        	if (clientRc == WinError.SEC_I_CONTINUE_NEEDED) {
	        	// server token is empty the first time
	        	final SecBufferDesc pbServerTokenCopy = pbServerToken == null
	        		? null : new SecBufferDesc(Sspi.SECBUFFER_TOKEN, pbServerToken.getBytes());
	        	clientRc = Secur32.INSTANCE.InitializeSecurityContext(
	    				phClientCredential,
	    				phClientContext.isNull() ? null : phClientContext,
	        			Advapi32Util.getUserName(),
	        			Sspi.ISC_REQ_CONNECTION,
	        			0,
	        			Sspi.SECURITY_NATIVE_DREP,
	        			pbServerTokenCopy,
	        			0,
	        			phClientContext,
	        			pbClientToken,
	        			pfClientContextAttr,
	        			null);
	    		assertTrue(clientRc == WinError.SEC_I_CONTINUE_NEEDED || clientRc == WinError.SEC_E_OK);
        	}
        	// server ----------- accept security context, produce a server token
    		if (serverRc == WinError.SEC_I_CONTINUE_NEEDED) {
	    		pbServerToken = new SecBufferDesc(Sspi.SECBUFFER_TOKEN, Sspi.MAX_TOKEN_SIZE);
	    		final SecBufferDesc pbClientTokenByValue = new SecBufferDesc(Sspi.SECBUFFER_TOKEN, pbClientToken.getBytes());
	    		serverRc = Secur32.INSTANCE.AcceptSecurityContext(phServerCredential,
	    				phServerContext.isNull() ? null : phServerContext,
	    				pbClientTokenByValue,
	    				Sspi.ISC_REQ_CONNECTION,
	    				Sspi.SECURITY_NATIVE_DREP,
	    				phServerContext,
	    				pbServerToken,
	    				pfServerContextAttr,
	    				ptsServerExpiry);
	    		assertTrue(serverRc == WinError.SEC_I_CONTINUE_NEEDED || serverRc == WinError.SEC_E_OK);
    		}
    	} while(serverRc != WinError.SEC_E_OK || clientRc != WinError.SEC_E_OK);
    	// impersonate
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.ImpersonateSecurityContext(
    			phServerContext));
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.RevertSecurityContext(
    			phServerContext));
    	// release server context
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.DeleteSecurityContext(
    			phServerContext));
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.FreeCredentialsHandle(
    			phServerCredential));
    	// release client context
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.DeleteSecurityContext(
    			phClientContext));
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.FreeCredentialsHandle(
    			phClientCredential));
    }

    public void testEnumerateSecurityPackages() {
    	final IntByReference pcPackages = new IntByReference();
    	final PSecPkgInfo.ByReference pPackageInfo = new PSecPkgInfo.ByReference();
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.EnumerateSecurityPackages(
    			pcPackages, pPackageInfo));
    	final SecPkgInfo.ByReference[] packagesInfo = pPackageInfo.toArray(
    			pcPackages.getValue());
    	for(final SecPkgInfo.ByReference packageInfo : packagesInfo) {
    		assertTrue(packageInfo.Name.length() > 0);
    		assertTrue(packageInfo.Comment.length() >= 0);
    	}
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.FreeContextBuffer(
    			pPackageInfo.getPointer()));
    }

    public void testQuerySecurityContextToken() {
    	// client ----------- acquire outbound credential handle
    	final CredHandle phClientCredential = new CredHandle();
    	final TimeStamp ptsClientExpiry = new TimeStamp();
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.AcquireCredentialsHandle(
    			null, "Negotiate", Sspi.SECPKG_CRED_OUTBOUND, null, null, null,
    			null, phClientCredential, ptsClientExpiry));
    	// client ----------- security context
    	final CtxtHandle phClientContext = new CtxtHandle();
    	final IntByReference pfClientContextAttr = new IntByReference();
		// server ----------- acquire inbound credential handle
    	final CredHandle phServerCredential = new CredHandle();
    	final TimeStamp ptsServerExpiry = new TimeStamp();
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.AcquireCredentialsHandle(
    			null, "Negotiate", Sspi.SECPKG_CRED_INBOUND, null, null, null,
    			null, phServerCredential, ptsServerExpiry));
    	// server ----------- security context
		final CtxtHandle phServerContext = new CtxtHandle();
    	final SecBufferDesc pbServerToken = new SecBufferDesc(Sspi.SECBUFFER_TOKEN, Sspi.MAX_TOKEN_SIZE);
    	final IntByReference pfServerContextAttr = new IntByReference();
    	int clientRc = WinError.SEC_I_CONTINUE_NEEDED;
    	int serverRc = WinError.SEC_I_CONTINUE_NEEDED;
    	do {
    		// client token returned is always new
        	final SecBufferDesc pbClientToken = new SecBufferDesc(Sspi.SECBUFFER_TOKEN, Sspi.MAX_TOKEN_SIZE);
        	// client ----------- initialize security context, produce a client token
    		if (clientRc == WinError.SEC_I_CONTINUE_NEEDED) {
	        	// server token is empty the first time
	    		clientRc = Secur32.INSTANCE.InitializeSecurityContext(
	    				phClientCredential,
	    				phClientContext.isNull() ? null : phClientContext,
	        			Advapi32Util.getUserName(),
	        			Sspi.ISC_REQ_CONNECTION,
	        			0,
	        			Sspi.SECURITY_NATIVE_DREP,
	        			pbServerToken,
	        			0,
	        			phClientContext,
	        			pbClientToken,
	        			pfClientContextAttr,
	        			null);
	    		assertTrue(clientRc == WinError.SEC_I_CONTINUE_NEEDED || clientRc == WinError.SEC_E_OK);
    		}
        	// server ----------- accept security context, produce a server token
    		if (serverRc == WinError.SEC_I_CONTINUE_NEEDED) {
	    		serverRc = Secur32.INSTANCE.AcceptSecurityContext(phServerCredential,
	    				phServerContext.isNull() ? null : phServerContext,
	    				pbClientToken,
	    				Sspi.ISC_REQ_CONNECTION,
	    				Sspi.SECURITY_NATIVE_DREP,
	    				phServerContext,
	    				pbServerToken,
	    				pfServerContextAttr,
	    				ptsServerExpiry);
	    		assertTrue(serverRc == WinError.SEC_I_CONTINUE_NEEDED || serverRc == WinError.SEC_E_OK);
    		}
    	} while(serverRc != WinError.SEC_E_OK || clientRc != WinError.SEC_E_OK);
    	// query security context token
    	final HANDLEByReference phContextToken = new HANDLEByReference();
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.QuerySecurityContextToken(
    			phServerContext, phContextToken));
    	// release security context token
    	assertTrue(Kernel32.INSTANCE.CloseHandle(phContextToken.getValue()));
    	// release server context
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.DeleteSecurityContext(
    			phServerContext));
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.FreeCredentialsHandle(
    			phServerCredential));
    	// release client context
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.DeleteSecurityContext(
    			phClientContext));
    	assertEquals(WinError.SEC_E_OK, Secur32.INSTANCE.FreeCredentialsHandle(
    			phClientCredential));
    }

    public void testCreateEmptyToken() {
        final SecBufferDesc token = new SecBufferDesc(Sspi.SECBUFFER_TOKEN, Sspi.MAX_TOKEN_SIZE);
        assertEquals(1, token.pBuffers.length);
        assertEquals(1, token.cBuffers);
        assertEquals(Sspi.SECBUFFER_TOKEN, token.pBuffers[0].BufferType);
        assertEquals(Sspi.MAX_TOKEN_SIZE, token.pBuffers[0].cbBuffer);
        assertEquals(token.getBytes().length, token.pBuffers[0].getBytes().length);
    }
}
