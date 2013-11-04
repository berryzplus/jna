dnl Process this file with autoconf to produce a configure script.

AC_PREREQ([2.68])
AC_INIT([jnidispatch], [1.11], [berryzplus@gmail.com])

AC_CONFIG_HEADERS([jnaconfig.h])
AC_CONFIG_SRCDIR([dispatch.c])
AC_CONFIG_MACRO_DIR([libffi/libltdl/m4])
AC_CONFIG_AUX_DIR([libffi/libltdl/config])

# Checks for programs.
AC_PROG_CC

LT_CONFIG_LTDL_DIR([libffi/libltdl])
LT_INIT([dlopen win32-dll])
LTDL_INIT([convenience installable])

AM_INIT_AUTOMAKE([foreign subdir-objects])

# Checks for header files.
AC_FUNC_ALLOCA
AC_CHECK_HEADERS([malloc.h stdint.h stdlib.h string.h sys/param.h unistd.h wchar.h])

# Checks for typedefs, structures, and compiler characteristics.
AC_TYPE_INT16_T
AC_TYPE_INT32_T
AC_TYPE_INT64_T
AC_TYPE_INT8_T
AC_TYPE_SIZE_T

# Checks for library functions.
AC_FUNC_MALLOC
AC_CHECK_FUNCS([memset])

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
