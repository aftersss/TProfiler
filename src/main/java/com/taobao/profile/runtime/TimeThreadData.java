/**
 * (C) 2011-2012 Alibaba Group Holding Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 */
package com.taobao.profile.runtime;


import java.lang.reflect.Method;

/**
 * 此类用来记录线程性能分析数据
 * 
 */
public class TimeThreadData {
	/**
	 * 性能分析数据
	 */
	public ProfStack<Object[]> profileData = new ProfStack<Object[]>();
	/**
	 * 栈帧
	 */
	public ProfStack<Object[]> stackFrame = new ProfStack<Object[]>();
	/**
	 * 当前栈深度
	 */
	public int stackNum = 0;

	/**
	 * 这个标记用于防止出现StackOverflowError
	 */
	private boolean isLogging = false;

	/**
	 * 这个标记用于标记是否可以打印慢日志，当堆栈中某个方法命中需要profile的方法时，标记为true
	 */
	private boolean canLogging = false;
	/**
	 * 命中需要profile的方法，当canLogging为真时记录此method
	 */
	private Method meetProfileMethod;

	/**
	 * 清空数据
	 */
	public void clear(){
		profileData.clear();
		stackFrame.clear();
		stackNum = 0;
		setLogging(false);
		setCanLogging(false);
		setMeetProfileMethod(null);
	}

	public boolean isLogging() {
		return isLogging;
	}

	public void setLogging(boolean logging) {
		isLogging = logging;
	}

	public boolean isCanLogging() {
		return canLogging;
	}

	public void setCanLogging(boolean canLogging) {
		this.canLogging = canLogging;
	}

	public Method getMeetProfileMethod() {
		return meetProfileMethod;
	}

	public void setMeetProfileMethod(Method meetProfileMethod) {
		this.meetProfileMethod = meetProfileMethod;
	}
}
