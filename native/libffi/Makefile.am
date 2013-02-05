## Process this with automake to create Makefile.in

AUTOMAKE_OPTIONS = foreign subdir-objects

ACLOCAL_AMFLAGS = -I m4

SUBDIRS = include testsuite man

EXTRA_DIST = LICENSE ChangeLog.v1 ChangeLog.libgcj ChangeLog.libffi \
	configure.host libtool-version msvcc.sh	\
	src/ia64/ia64_flags.h src/powerpc/asm.h	\
	generate-ios-source-and-headers.py	\
	generate-osx-source-and-headers.py

info_TEXINFOS = doc/libffi.texi

## ################################################################

##
## This section is for make and multilib madness.
##

# Work around what appears to be a GNU make bug handling MAKEFLAGS
# values defined in terms of make variables, as is the case for CC and
# friends when we are called from the top level Makefile.
AM_MAKEFLAGS = \
	"AR_FLAGS=$(AR_FLAGS)" \
	"CC_FOR_BUILD=$(CC_FOR_BUILD)" \
	"CFLAGS=$(CFLAGS)" \
	"CFLAGS_FOR_BUILD=$(CFLAGS_FOR_BUILD)" \
	"CFLAGS_FOR_TARGET=$(CFLAGS_FOR_TARGET)" \
	"INSTALL=$(INSTALL)" \
	"INSTALL_DATA=$(INSTALL_DATA)" \
	"INSTALL_PROGRAM=$(INSTALL_PROGRAM)" \
	"INSTALL_SCRIPT=$(INSTALL_SCRIPT)" \
	"JC1FLAGS=$(JC1FLAGS)" \
	"LDFLAGS=$(LDFLAGS)" \
	"LIBCFLAGS=$(LIBCFLAGS)" \
	"LIBCFLAGS_FOR_TARGET=$(LIBCFLAGS_FOR_TARGET)" \
	"MAKE=$(MAKE)" \
	"MAKEINFO=$(MAKEINFO) $(MAKEINFOFLAGS)" \
	"PICFLAG=$(PICFLAG)" \
	"PICFLAG_FOR_TARGET=$(PICFLAG_FOR_TARGET)" \
	"RUNTESTFLAGS=$(RUNTESTFLAGS)" \
	"SHELL=$(SHELL)" \
	"exec_prefix=$(exec_prefix)" \
	"infodir=$(infodir)" \
	"libdir=$(libdir)" \
	"mandir=$(mandir)" \
	"prefix=$(prefix)" \
	"AR=$(AR)" \
	"AS=$(AS)" \
	"CC=$(CC)" \
	"LD=$(LD)" \
	"NM=$(NM)" \
	"RANLIB=$(RANLIB)" \
	"DESTDIR=$(DESTDIR)"

# Subdir rules rely on $(FLAGS_TO_PASS)
FLAGS_TO_PASS = $(AM_MAKEFLAGS)

MAKEOVERRIDES=

toolexeclib_LTLIBRARIES = libffi.la
noinst_LTLIBRARIES = libffi_convenience.la

libffi_la_SOURCES = src/prep_cif.c src/types.c \
		src/raw_api.c src/java_raw_api.c src/closures.c

#init with no sources.
nodist_libffi_la_SOURCES =

if X86
  EXTRA_DIST += src/x86/ffitarget.h src/x86/ffi.c src/x86/sysv.S
  nodist_libffi_la_SOURCES += src/x86/ffi.c src/x86/sysv.S
endif
if X86_FREEBSD
  EXTRA_DIST += src/x86/ffitarget.h src/x86/ffi.c src/x86/freebsd.S
  nodist_libffi_la_SOURCES += src/x86/ffi.c src/x86/freebsd.S
endif
if X86_WIN32
  EXTRA_DIST += src/x86/ffitarget.h src/x86/ffi.c src/x86/win32.S
  nodist_libffi_la_SOURCES += src/x86/ffi.c src/x86/win32.S
endif
if X86_WIN64
  EXTRA_DIST += src/x86/ffitarget.h src/x86/ffi.c src/x86/win64.S
  nodist_libffi_la_SOURCES += src/x86/ffi.c src/x86/win64.S
endif
if X86_DARWIN
  EXTRA_DIST += src/x86/ffitarget.h src/x86/ffi.c src/x86/darwin.S src/x86/ffi64.c src/x86/darwin64.S
  nodist_libffi_la_SOURCES += src/x86/ffi.c src/x86/darwin.S src/x86/ffi64.c src/x86/darwin64.S
endif
if X86_64
  EXTRA_DIST += src/x86/ffitarget.h src/x86/ffi.c src/x86/sysv.S src/x86/ffi64.c src/x86/unix64.S
  nodist_libffi_la_SOURCES += src/x86/ffi.c src/x86/sysv.S src/x86/ffi64.c src/x86/unix64.S
endif
if MIPS
  nodist_libffi_la_SOURCES += src/mips/ffi.c src/mips/o32.S src/mips/n32.S
endif
if BFIN
  nodist_libffi_la_SOURCES += src/bfin/ffi.c src/bfin/sysv.S
endif
if SPARC
  nodist_libffi_la_SOURCES += src/sparc/ffi.c src/sparc/v8.S src/sparc/v9.S
endif
if ALPHA
  nodist_libffi_la_SOURCES += src/alpha/ffi.c src/alpha/osf.S
endif
if IA64
  nodist_libffi_la_SOURCES += src/ia64/ffi.c src/ia64/unix.S
endif
if M32R
  nodist_libffi_la_SOURCES += src/m32r/ffi.c src/m32r/sysv.S
endif
if M68K
  nodist_libffi_la_SOURCES += src/m68k/ffi.c src/m68k/sysv.S
endif
if MICROBLAZE
  nodist_libffi_la_SOURCES += src/microblaze/ffi.c src/microblaze/sysv.S
endif
if AARCH64
  nodist_libffi_la_SOURCES += src/aarch64/ffi.c src/aarch64/sysv.S
endif
if AVR32
  nodist_libffi_la_SOURCES += src/avr32/ffi.c src/avr32/sysv.S
endif
if LIBFFI_CRIS
  nodist_libffi_la_SOURCES += src/cris/ffi.c src/cris/sysv.S
endif
if FRV
  nodist_libffi_la_SOURCES += src/frv/ffi.c src/frv/eabi.S
endif
if S390
  nodist_libffi_la_SOURCES += src/s390/ffi.c src/s390/sysv.S
endif
if SH
  nodist_libffi_la_SOURCES += src/sh/ffi.c src/sh/sysv.S
endif
if SH64
  nodist_libffi_la_SOURCES += src/sh64/ffi.c src/sh64/sysv.S
endif
if PA_LINUX
  nodist_libffi_la_SOURCES += src/pa/ffi.c src/pa/linux.S
endif
if PA_HPUX
  nodist_libffi_la_SOURCES += src/pa/ffi.c src/pa/hpux32.S
endif
if TILE
  nodist_libffi_la_SOURCES += src/tile/ffi.c src/tile/tile.S
endif
if XTENSA
  nodist_libffi_la_SOURCES += src/xtensa/ffi.c src/xtensa/sysv.S
endif
if ARM
  nodist_libffi_la_SOURCES += src/arm/ffi.c src/arm/sysv.S
  if FFI_EXEC_TRAMPOLINE_TABLE
  nodist_libffi_la_SOURCES += src/arm/trampoline.S
  endif
endif
if POWERPC
  nodist_libffi_la_SOURCES += src/powerpc/ffi.c src/powerpc/sysv.S src/powerpc/ppc_closure.S src/powerpc/linux64.S src/powerpc/linux64_closure.S
endif
if POWERPC_FREEBSD
  nodist_libffi_la_SOURCES += src/powerpc/ffi.c src/powerpc/sysv.S src/powerpc/ppc_closure.S
endif
if POWERPC_AIX
  nodist_libffi_la_SOURCES += src/powerpc/ffi_darwin.c src/powerpc/aix.S src/powerpc/aix_closure.S
endif
if POWERPC_DARWIN
  nodist_libffi_la_SOURCES += src/powerpc/ffi_darwin.c src/powerpc/darwin.S src/powerpc/darwin_closure.S
endif

if FFI_DEBUG
nodist_libffi_la_SOURCES += src/debug.c
endif

libffi_convenience_la_SOURCES = $(libffi_la_SOURCES)
nodist_libffi_convenience_la_SOURCES = $(nodist_libffi_la_SOURCES)

libffi_la_LDFLAGS = -no-undefined -version-info `grep -v '^\#' $(srcdir)/libtool-version` $(LTLDFLAGS) $(AM_LTLDFLAGS)

AM_CPPFLAGS = -Iinclude -I$(top_srcdir)/include -I$(top_srcdir)/src

