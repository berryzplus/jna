## Process this with automake to create Makefile.in
#
# Makefile for JNA native bits
# Copyright (c) 2007-2012 Timothy Wall All Rights Reserved

ACLOCAL_AMFLAGS = -I libffi/libltdl/m4

# make jnidispatch.dll as shared library ,and install to lib direcotry
# '*.la' is name convention for libtool command
lib_LTLIBRARIES = libjnidispatch.la

# sources of libJna.la
libjnidispatch_la_SOURCES = dispatch.c callback.c dll-callback.c jnidispatch.rc \
					com_sun_jna_Function.h com_sun_jna_Native.h com_sun_jna_win32_DLLCallback.h \
					fficonfig.h ffitarget.h ffi.h jni.h jni-md.h psapi.lib 

# LDFLAGS for libjnidispatch.la
libjnidispatch_la_LDFLAGS = -module -shared
libjnidispatch_la_LDFLAGS += -avoid-version
libjnidispatch_la_LDFLAGS += -no-undefined

libjnidispatch_la_LIBADD = $(top_srcdir)/libffi/.libs/libffi.la 
libjnidispatch_la_LIBADD += $(top_srcdir)/libffi/.libs/libpsapi.la 

AM_CPPFLAGS = -I$(top_srcdir) -I$(top_srcdir)/include \
				 -I$(top_srcdir)/libffi -I$(top_srcdir)/libffi/include \
				 -I"/c/Progra~1/Java/jdk1.7.0_45/include/win32" \
				 -I"/c/Progra~1/Java/jdk1.7.0_45/include" 

libtool: $(LIBTOOL_DEPS)
	$(SHELL) ./config.status libtool
