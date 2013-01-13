package com.sun.jna;

import java.lang.reflect.Method;

/** Conversion context from a Java {@link Callback} result to a native value. */
public class CallbackResultContext extends ToNativeContext {
    private final Method method;
    CallbackResultContext(final Method callbackMethod) {
        this.method = callbackMethod;
    }
    public Method getMethod() { return method; }
}
