package com.sun.jna;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

public class AnnotatedLibraryTest extends TestCase {

    @Retention(RetentionPolicy.RUNTIME)
    public @interface TestAnnotation {
    }

    public interface AnnotatedLibrary extends Library {
        @TestAnnotation boolean isAnnotated();
    }

    public class TestInvocationHandler implements InvocationHandler {
        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
            return Boolean.valueOf(method.getAnnotations().length == 1);
        }
    }

    // There's a rumor that some VMs don't copy annotation information to
    // dynamically generated proxies.  Detect it here.
    public void testProxyMethodHasAnnotations() throws Exception {
        final AnnotatedLibrary a = (AnnotatedLibrary)
            Proxy.newProxyInstance(getClass().getClassLoader(),
                                   new Class[] { AnnotatedLibrary.class },
                                   new TestInvocationHandler());
        assertTrue("Proxy method not annotated", a.isAnnotated());
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface FooBoolean {}
    public static interface AnnotationTestLibrary extends Library {
        @FooBoolean
        boolean returnInt32Argument(boolean b);
    }
    public void testAnnotationsOnMethods() throws Exception {
        final int MAGIC = 0xABEDCF23;
        final Map options = new HashMap();
        final boolean[] hasAnnotation = {false, false};
        final DefaultTypeMapper mapper = new DefaultTypeMapper();
        mapper.addTypeConverter(Boolean.class, new TypeConverter() {
            public Object toNative(final Object value, final ToNativeContext ctx) {
                final MethodParameterContext mcontext = (MethodParameterContext)ctx;
                hasAnnotation[0] = mcontext.getMethod().getAnnotation(FooBoolean.class) != null;
                return new Integer(Boolean.TRUE.equals(value) ? MAGIC : 0);
            }
            public Object fromNative(final Object value, final FromNativeContext context) {
                final MethodResultContext mcontext = (MethodResultContext)context;
                hasAnnotation[1] = mcontext.getMethod().getAnnotation(FooBoolean.class) != null;
                return Boolean.valueOf(((Integer) value).intValue() == MAGIC);
            }
            public Class nativeType() {
                return Integer.class;
            }
        });

        options.put(Library.OPTION_TYPE_MAPPER, mapper);
        final AnnotationTestLibrary lib = (AnnotationTestLibrary)
            Native.loadLibrary("testlib", AnnotationTestLibrary.class, options);
        assertEquals("Failed to convert integer return to boolean TRUE", true,
                     lib.returnInt32Argument(true));
        assertTrue("Failed to get annotation from ParameterContext", hasAnnotation[0]);
        assertTrue("Failed to get annotation from ResultContext", hasAnnotation[1]);
    }

    public static void main(final String[] args) {
        junit.textui.TestRunner.run(AnnotatedLibraryTest.class);
    }
}
