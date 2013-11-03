# Process this file with autoconf to produce a configure script.

AC_PREREQ([2.68])

AC_INIT([libffi], [3.0.12-snapshot], [berryzplus@gmail.com])

AC_CONFIG_HEADERS([fficonfig.h])
AC_CONFIG_MACRO_DIR([m4])

# Checks for system characteristics.
LIBFFI_TARGET

# Checks for programs.
AM_PROG_AS
AM_PROG_CC_C_O
AC_PROG_LIBTOOL


AX_COMPILER_VENDOR
AX_CC_MAXOPT
# The AX_CFLAGS_WARN_ALL macro doesn't currently work
# for sunpro compiler and microsoft compiler.
if test "$ax_cv_c_compiler_vendor" != "sun" && "$ax_cv_c_compiler_vendor" != "microsoft"; then
  AX_CFLAGS_WARN_ALL
fi
if test "x$GCC" = "xyes"; then
    CFLAGS="$CFLAGS -fexceptions"
    touch local.exp
else
    cat > local.exp <<EOF
set CC_FOR_TARGET "$CC"
EOF
fi


dnl configure.host
case "${host}" in
  frv*-elf)
    LDFLAGS=`echo $LDFLAGS | sed "s/\-B[^ ]*libgloss\/frv\///"`\ -B`pwd`/../libgloss/frv/
    ;;
esac


AC_C_BIGENDIAN
AC_C_INLINE
AC_C_LONG_DOUBLE
GCC_AS_CFI_PSEUDO_OP

# Checks for libraries.

# Checks for header files.
AC_CHECK_HEADERS([sys/mman.h])

# Checks for typedefs, structures, and compiler characteristics.
AC_TYPE_SIZE_T
AC_TYPE_INT8_T
AC_TYPE_UINT8_T
AC_TYPE_INT16_T
AC_TYPE_UINT16_T
AC_TYPE_INT32_T
AC_TYPE_UINT32_T
AC_TYPE_INT64_T
AC_TYPE_UINT64_T
AC_TYPE_OFF_T
AC_CHECK_TYPES([ptrdiff_t])

# Test for 64-bit build.
AC_CHECK_SIZEOF([size_t])

AC_FUNC_ALLOCA
AC_CHECK_FUNCS([mmap memcpy])
AC_FUNC_MMAP_BLACKLIST


if test "x$TARGET" = "xSPARC"; then
  LIBFFI_AS_SPARC_UA_PCREL
  LIBFFI_AS_REGISTER_PSEUDO_OP
fi
if test x$TARGET = xX86 || test x$TARGET = xX86_WIN32 || test x$TARGET = xX86_64; then
  LIBFFI_AS_X86_PCREL
  LIBFFI_AS_ASCII_PSEUDO_OP
  LIBFFI_AS_STRING_PSEUDO_OP
fi
if test x$TARGET = xX86_WIN64; then
  LT_SYS_SYMBOL_USCORE
  if test "x$sys_symbol_underscore" = xyes; then
    AC_DEFINE([SYMBOL_UNDERSCORE], [1],
      [Define if symbols are underscored.])
  fi
fi

FFI_EXEC_TRAMPOLINE_TABLE=0
case "$target" in
  *arm*-apple-darwin*)
    FFI_EXEC_TRAMPOLINE_TABLE=1
    AC_DEFINE([FFI_EXEC_TRAMPOLINE_TABLE], [1],
              [Cannot use PROT_EXEC on this target, so, we revert to
                alternative means])
  ;;
  *-apple-darwin1* | *-*-freebsd* | *-*-kfreebsd* | *-*-openbsd* | *-pc-solaris*)
    AC_DEFINE([FFI_MMAP_EXEC_WRIT], [1],
              [Cannot use malloc on this target, so, we revert to
                alternative means])
  ;;
esac
AM_CONDITIONAL(FFI_EXEC_TRAMPOLINE_TABLE, test x$FFI_EXEC_TRAMPOLINE_TABLE = x1)

if test x$TARGET = xX86_64; then
  LIBFFI_AS_X86_64_UNWIND_SECTION_TYPE
fi
if test "x$GCC" = "xyes"; then
  LIBFFI_RO_EH_FRAME
  LIBFFI_HIDDEN_VISIBILITY_ATTRIBUTE

  # These variables are only ever used when we cross-build to X86_WIN32.
  # And we only support this with GCC, so...
  if test -n "$with_cross_host" &&
     test x"$with_cross_host" != x"no"; then
    toolexecdir='$(exec_prefix)/$(target_alias)'
    toolexeclibdir='$(toolexecdir)/lib'
  else
    toolexecdir='$(libdir)/gcc-lib/$(target_alias)'
    toolexeclibdir='$(libdir)'
  fi
  multi_os_directory=`$CC -print-multi-os-directory`
  case $multi_os_directory in
    .) ;; # Avoid trailing /.
    *) toolexeclibdir=$toolexeclibdir/$multi_os_directory ;;
  esac
fi

AM_MAINTAINER_MODE

dnl The -no-testsuite modules omit the test subdir.
AM_CONDITIONAL(TESTSUBDIR, test -d $srcdir/testsuite)


AC_ARG_ENABLE([debug],
[  --enable-debug          debugging mode], [
  if test "$enable_debug" = "yes"; then
    AC_DEFINE([FFI_DEBUG], [1], [Define this if you want extra debugging.])
  fi])
AM_CONDITIONAL(FFI_DEBUG, test "$enable_debug" = "yes")

AC_ARG_ENABLE([structs],
[  --disable-structs       omit code for struct support], [
  if test "$enable_structs" = "no"; then
    AC_DEFINE([FFI_NO_STRUCTS], [1], [Define this is you do not want support for aggregate types.])
  fi])

AC_ARG_ENABLE([raw-api],
[  --disable-raw-api       make the raw api unavailable], [
  if test "$enable_raw_api" = "no"; then
    AC_DEFINE([FFI_NO_RAW_API], [1], [Define this is you do not want support for the raw API.])
  fi])

AC_ARG_ENABLE([purify-safety],
[  --enable-purify-safety  purify-safe mode], [
  if test "$enable_purify_safety" = "yes"; then
    AC_DEFINE([USING_PURIFY], [1], [Define this if you are using Purify and want to suppress spurious messages.])
  fi])

# On PaX enable kernels that have MPROTECT enable we can't use PROT_EXEC.
AC_ARG_ENABLE([pax_emutramp],
[  --enable-pax_emutramp   enable pax emulated trampolines, for we can't use PROT_EXEC], [
  if test "$enable_pax_emutramp" = "yes"; then
    AC_DEFINE([FFI_MMAP_EXEC_EMUTRAMP_PAX], [1],
      [Define this if you want to enable pax emulated trampolines])
  fi])

AC_CONFIG_COMMANDS([include], [test -d include || mkdir include])
AC_CONFIG_COMMANDS([src], [
  test -d src || mkdir src
  test -d src/$TARGETDIR || mkdir src/$TARGETDIR
], [TARGETDIR="$TARGETDIR"])

# Also AC_SUBST this variable for ffi.h.
AC_SUBST([TARGETDIR])
AC_SUBST([TARGET])
AC_SUBST([SHELL])
AC_SUBST([AM_RUNTESTFLAGS])
AC_SUBST([AM_LTLDFLAGS])
AC_SUBST([HAVE_LONG_DOUBLE])
AC_SUBST([FFI_EXEC_TRAMPOLINE_TABLE])
AC_SUBST([toolexecdir])
AC_SUBST([toolexeclibdir])

AC_CONFIG_LINKS(include/ffitarget.h:src/$TARGETDIR/ffitarget.h)
AC_CONFIG_FILES(include/Makefile include/ffi.h libffi.pc testsuite/Makefile man/Makefile Makefile)

AH_BOTTOM([
#ifdef HAVE_HIDDEN_VISIBILITY_ATTRIBUTE
#ifdef LIBFFI_ASM
#define FFI_HIDDEN(name) .hidden name
#else
#define FFI_HIDDEN __attribute__ ((visibility ("hidden")))
#endif
#else
#ifdef LIBFFI_ASM
#define FFI_HIDDEN(name)
#else
#define FFI_HIDDEN
#endif
#endif
])

AC_OUTPUT
