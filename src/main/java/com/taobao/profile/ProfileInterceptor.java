package com.taobao.profile;

import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

public class ProfileInterceptor {
    @RuntimeType
    public static Object intercept(@Origin Method method, @SuperCall Callable<Object> callable) throws Exception {
        TimeProfiler.Start(method);
        try{
            return callable.call();
        }finally {
            TimeProfiler.End(method);
        }
    }
}
