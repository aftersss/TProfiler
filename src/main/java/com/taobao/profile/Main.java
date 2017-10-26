/**
 * (C) 2011-2012 Alibaba Group Holding Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 */
package com.taobao.profile;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import com.taobao.profile.config.ProfFilter;
import com.taobao.profile.runtime.MethodCache;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.matcher.*;
import net.bytebuddy.utility.JavaModule;

/**
 * TProfiler入口
 * 
 * @author luqi
 * @since 2010-6-23
 */
public class Main {

	/**
	 * @param args
	 * @param inst
	 */
	public static void premain(String args, Instrumentation inst) {
		System.out.println("TProfiler premain executed");
		Manager.instance().initialization();
//		inst.addTransformer(new ProfTransformer());

		final TypeDescription notLoadType = new TypeDescription.ForLoadedType(SecurityManager.class);
		final ElementMatcher<MethodDescription> methodMatcher = ElementMatchers.<MethodDescription>any()
				.and(ElementMatchers.not(ElementMatchers.isGetter()))
				.and(ElementMatchers.not(ElementMatchers.isSetter()))
				.and(ElementMatchers.not(ElementMatchers.isDefaultConstructor()))
				.and(ElementMatchers.not(ElementMatchers.isConstructor()))
				.and(ElementMatchers.not(ElementMatchers.isDefaultFinalizer()))
				.and(ElementMatchers.not(ElementMatchers.isFinalizer()))
				.and(ElementMatchers.not(ElementMatchers.isDefaultMethod()))
				.and(ElementMatchers.not(ElementMatchers.isEquals()))
				.and(ElementMatchers.not(ElementMatchers.isHashCode()))
				.and(ElementMatchers.not(ElementMatchers.isToString()))
				.and(ElementMatchers.not(ElementMatchers.<MethodDescription>isNative()))
				.and(ElementMatchers.not(ElementMatchers.isClone()));
		new AgentBuilder.Default()
//				.type(ElementMatchers.<TypeDescription>nameStartsWith("cn.com.duiba"))
				.type(new NameMatcher<TypeDescription>(null){

					@Override
					public boolean matches(TypeDescription target) {
						if((target.getModifiers() & Opcodes.ACC_INTERFACE) != 0){//当前类是interface
							return false;
						}
						if((target.getModifiers() & Opcodes.ACC_ENUM) != 0){//当前类是 enum
							return false;
						}
						if((target.getModifiers() & Opcodes.ACC_ANNOTATION) != 0){//当前类是 annotation
							return false;
						}
						String actualName = target.getActualName();

						if(actualName == null){
							return false;
						}

						if (!ProfFilter.isNeedInject(actualName)) {
							return false;
						}
						if (ProfFilter.isNotNeedInject(actualName)) {
							return false;
						}

						if(target.isAssignableTo(notLoadType)){
							return false;
						}

						return true;
					}

				}, new EqualityMatcher<ClassLoader>(""){
					@Override
					public boolean matches(ClassLoader target) {
						if (target != null && ProfFilter.isNotNeedInjectClassLoader(target.getClass().getName())) {
							return false;
						}
						return true;
					}
				})
				.transform(new AgentBuilder.Transformer() {
							   @Override
							   public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, final TypeDescription typeDescription, ClassLoader classLoader, JavaModule module) {
								   return builder
								   .method(new BooleanMatcher<MethodDescription>(true){
									   @Override
									   public boolean matches(MethodDescription target) {
										   //如果方法没有实现toString，这里仍然能拿到父类Object的方法，其他继承情况类似，所以这里要再次判断方法来自的类在白名单中
										   String exactClassName = typeDescription.getActualName();
										   String className = target.getDeclaringType().getActualName();
										   if(!exactClassName.equals(className)){
											   return false;
										   }

//										   if (!ProfFilter.isNeedInject(className)) {
//											   return false;
//										   }
//										   if (ProfFilter.isNotNeedInject(className)) {
//											   return false;
//										   }
										   // 排除get set等方法
										   return methodMatcher.matches(target);
									   }
								   })
								   .intercept(MethodDelegation.to(ProfileInterceptor.class));
							   }
						   }
				)
				.installOn(inst);

		//TODO 使用byte buddy有个很大的缺点，会污染报错堆栈，堆栈更复杂了，有空改成asm试试，对比下启动速度和运行速度
		//TODO 熟读官网文档看怎么优化性能
		Manager.instance().startupThread();
	}

	public static class User{
		private String name = "hwq";
		public String getName(){
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			return name;
		}
		public void setName(String name){
			this.name = name;
			this.toString();
		}
		public String toString(){
			this.getName();
			return "name";
		}
	}

	public static void main(String[] args) throws IllegalAccessException, InstantiationException {
		Class<?> dynamicType = new ByteBuddy()
				.subclass(User.class)
//				.method(ElementMatchers.<MethodDescription>any())
				.method(ElementMatchers.<MethodDescription>any()
						.and(ElementMatchers.not(ElementMatchers.isGetter()))
						.and(ElementMatchers.not(ElementMatchers.isSetter()))
						.and(ElementMatchers.not(ElementMatchers.isDefaultConstructor()))
						.and(ElementMatchers.not(ElementMatchers.isConstructor()))
						.and(ElementMatchers.not(ElementMatchers.isDefaultFinalizer()))
						.and(ElementMatchers.not(ElementMatchers.isFinalizer()))
						.and(ElementMatchers.not(ElementMatchers.isDefaultMethod()))
						.and(ElementMatchers.not(ElementMatchers.isEquals()))
						.and(ElementMatchers.not(ElementMatchers.isHashCode()))
						.and(ElementMatchers.not(ElementMatchers.isToString()))
						.and(ElementMatchers.not(ElementMatchers.<MethodDescription>isNative()))
						.and(ElementMatchers.not(ElementMatchers.isClone()))
				)
				.intercept(MethodDelegation.to(ProfileInterceptor.class))
				.make()
				.load(Main.class.getClassLoader(),
						ClassLoadingStrategy.Default.WRAPPER)
				.getLoaded();

		User obj = (User) dynamicType.newInstance();
		obj.setName("hequn");
		System.out.println(obj.getName());
		System.out.println(obj.toString());
	}
}
