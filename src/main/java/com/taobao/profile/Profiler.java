/**
 * (C) 2011-2012 Alibaba Group Holding Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 */
package com.taobao.profile;

import com.taobao.profile.dependence_query.RecordSlowQuery;
import com.taobao.profile.dependence_query.SlowQueryData;
import com.taobao.profile.runtime.MethodCache;
import com.taobao.profile.runtime.MethodInfo;
import com.taobao.profile.runtime.ProfStack;
import com.taobao.profile.runtime.ThreadData;
import com.taobao.profile.utils.DailyRollingFileWriter;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 此类收集应用代码的运行时数据
 * 
 * @author luqi
 * @since 2010-6-23
 */
public class Profiler {
	/**
	 * 注入类数
	 */
	public static AtomicInteger instrumentClassCount = new AtomicInteger(0);
	/**
	 * 注入方法数
	 */
	public static AtomicInteger instrumentMethodCount = new AtomicInteger(0);

	private final static int size = 65535;
	/**
	 * 线程数组
	 */
	public static ThreadData[] threadProfile = new ThreadData[size];

	/**
	 * 记录慢日志的数组
	 */
	public static SlowQueryData[] slowQueryProfile = new SlowQueryData[size];

	private static DailyRollingFileWriter fileWriter = new DailyRollingFileWriter(Manager.getSlowLogPath());

	/**
	 * 方法开始时调用,采集开始时间
	 * 
	 * @param methodId
	 */
	public static void Start(int methodId) {
		if (!Manager.instance().canProfile()) {
			return;
		}
		long threadId = Thread.currentThread().getId();
		if (threadId >= size) {
			return;
		}

		long startTime;
		if (Manager.isNeedNanoTime()) {
			startTime = System.nanoTime();
		} else {
			startTime = System.currentTimeMillis();
		}
		try {
			ThreadData thrData = threadProfile[(int) threadId];
			if (thrData == null) {
				thrData = new ThreadData();
				threadProfile[(int) threadId] = thrData;
			}

			long[] frameData = new long[3];
			frameData[0] = methodId;
			frameData[1] = thrData.stackNum;
			frameData[2] = startTime;
			thrData.stackFrame.push(frameData);
			thrData.stackNum++;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 方法退出时调用,采集结束时间
	 * 
	 * @param methodId
	 */
	public static void End(int methodId) {
		if (!Manager.instance().canProfile()) {
			return;
		}
		long threadId = Thread.currentThread().getId();
		if (threadId >= size) {
			return;
		}

		long endTime;
		if (Manager.isNeedNanoTime()) {
			endTime = System.nanoTime();
		} else {
			endTime = System.currentTimeMillis();
		}
		try {
			ThreadData thrData = threadProfile[(int) threadId];
			if (thrData == null || thrData.stackNum <= 0 || thrData.stackFrame.size() == 0) {
				// 没有执行start,直接执行end/可能是异步停止导致的
				return;
			}
			// 栈太深则抛弃部分数据
			if (thrData.profileData.size() > 20000) {
				thrData.stackNum--;
				thrData.stackFrame.pop();
				return;
			}
			thrData.stackNum--;
			long[] frameData = thrData.stackFrame.pop();
			long id = frameData[0];
			if (methodId != id) {
				return;
			}
			long useTime = endTime - frameData[2];
			if (Manager.isNeedNanoTime()) {
				if (useTime > 500000) {
					frameData[2] = useTime;
					thrData.profileData.push(frameData);
				}
			} else if (useTime > 1) {
				frameData[2] = useTime;
				thrData.profileData.push(frameData);
			}

			if(thrData.stackNum == 0){
				outputSlowLog(thrData);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void clearData() {
		for (int index = 0; index < threadProfile.length; index++) {
			ThreadData profilerData = threadProfile[index];
			if (profilerData == null) {
				continue;
			}
			profilerData.clear();
		}

		for (int index = 0; index < slowQueryProfile.length; index++) {
			SlowQueryData profilerData = slowQueryProfile[index];
			if (profilerData == null) {
				continue;
			}
			profilerData.clear();
		}
	}

	/** add for dependence**/

	/***
	 * 获取线程ID；如果不在需要profile的区间内；则返回-1
	 * @return
	 */
	private static long getThreadID(){
		if (!Manager.instance().canProfile()) {
			return -1;
		}
		long threadId = Thread.currentThread().getId();
		if (threadId >= size) {
			return -1;
		}
		return threadId;
	}

	/**
	 * 获取时间；用于计算函数执行时间；支持纳秒取值；
	 * @return
	 */
	private static long getCurTime(){
		long curTime;
		if (Manager.isNeedNanoTime()) {
			curTime = System.nanoTime();
		} else {
			curTime = System.currentTimeMillis();
		}
		return curTime;
	}

	/**
	 * 获取当前线程的信息;如果不存在则会重新分配一个；
	 * @param threadId
	 * @return
	 */
	private static SlowQueryData getThreadData(long threadId){
		SlowQueryData thrData = slowQueryProfile[(int) threadId];
		if (thrData == null) {
			thrData = new SlowQueryData();
			slowQueryProfile[(int) threadId] = thrData;
		}
		return thrData;
	}

	/**
	 * 是否需要记录;只超过10ms的查询
	 * @param useTime
	 * @return
	 */
	private static boolean isNeedRecord(long useTime){
		int time = Manager.getRecordTime();
		if (Manager.isNeedNanoTime()) {
			time = time * 1000000;
			if (useTime > time) {
				return true;
			}
		} else if (useTime > time) {
			return true;
		}
		return false;
	}

	/**
	 * 弹出方法的堆栈信息；
	 * @param thrData
	 * @return
	 */
	private static Object[] popStack(SlowQueryData thrData){
		if(thrData==null){
			return null;
		}
		// 栈太深则抛弃部分数据
		if (thrData.profileData.size() > 20000) {
			thrData.stackNum--;
			thrData.stackFrame.pop();
			return null;
		}
		thrData.stackNum--;
		Object[] frameData = thrData.stackFrame.pop();
		return frameData;
	}

	/**
	 * 开始记录mysql的信息
	 * @param host
	 * @param port
	 * @param db
	 * @param sql
	 */
	public static void start4Mysql(String host,int port,String db,String sql){
		long threadId = getThreadID();

		if(threadId==-1){
			return;
		}

		if(Manager.getRecordTime()==-1){
			return;
		}

		long startTime = getCurTime();
		try {
			SlowQueryData thrData = getThreadData(threadId);

			Object[] frameData = new Object[6];
			frameData[0] = thrData.stackNum;
			frameData[1] = startTime;
			frameData[2] = host;
			frameData[3] = port;
			frameData[4] = db;
			frameData[5] = sql;
			thrData.stackFrame.push(frameData);
			thrData.stackNum++;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * mysql记录结束
	 */
	public static void end4Mysql(){
		long threadId = getThreadID();

		if(threadId==-1){
			return;
		}

		if(Manager.getRecordTime()==-1){
			return;
		}

		long endTime = getCurTime();

		SlowQueryData thrData = getThreadData(threadId);
		Object[] frameData = popStack(thrData);
		if(frameData==null){
			return ;
		}

		RecordSlowQuery record = new RecordSlowQuery();
		Map<String, String> map = new HashMap<String, String>();

		map.put("host", (String) frameData[2]);
		map.put("port", frameData[3].toString());
		map.put("db", (String) frameData[4]);
		map.put("sql", (String) frameData[5]);
		record.setRequestDesc(map);
		record.setUseTime(endTime - (Long) frameData[1]);
		record.setType("MYSQL");

		StringBuilder sb = new StringBuilder();
		sb.append("MYSQL");
		sb.append((String) frameData[2]);
		sb.append(frameData[3].toString());
		sb.append((String) frameData[4]);

		if(!isNeedRecord(record.getUseTime())){
			return;
		}
		map.put("nanoTime", Manager.isNeedNanoTime() + "");

		thrData.profileData.push(record);
	}

	private static void outputSlowLog(ThreadData thrData){
		ProfStack<long[]> stack = thrData.profileData;
		if(stack == null){
			return;
		}
		long[] element = stack.peek();

		long timeConsume = element[2];
		boolean needLog = false;
		if (Manager.isNeedNanoTime()) {
			if(timeConsume > Manager.getProfileThreshold() * 1000000){
				needLog = true;
			}
		}else if(timeConsume > Manager.getProfileThreshold()){
			needLog = true;
		}
		if (needLog) {
			// 输出日志
			StringBuilder sb = new StringBuilder();
			sb.append("method cost:").append(timeConsume).append("ms");

			while((element = stack.pop()) != null){
				sb.append("\r\n\t");
				long deep = element[1];
				for (int i = 0; i < deep; i++) {
					sb.append("-");
				}
				Long consume = element[2];
				sb.append(consume * 100 / timeConsume).append("%");
				sb.append("  ").append(consume).append("ms");
				sb.append("  ").append(getMethodInfo(element[0]));
			}

			fileWriter.append(sb.toString());
			fileWriter.flushAppend();
			//TODO 退出时需要关闭fileWriter
		}

		thrData.clear();
	}

	private static String getMethodInfo(long methodId){
		MethodInfo m = MethodCache.getMethodInfo(methodId);
		StringBuilder sb = new StringBuilder();
		appendShortClassName(sb, m.getMClassName());
		sb.append(".");
		sb.append(m.getMMethodName());

		return sb.toString();
	}

	private static void appendShortClassName(StringBuilder sb, String className){
//		sb.append(className);
		String arr[] = className.split("\\.");
		for(int i=0;i<arr.length;i++){
			if(i < arr.length - 1) {
				sb.append(arr[i].substring(0, 1)).append(".");
			}else{
				sb.append(arr[i]);
			}
		}
	}

	public static void main(String[] args) {
		String className = ProfStack.class.getName();

		StringBuilder sb = new StringBuilder();
		String arr[] = className.split("\\.");
		for(int i=0;i<arr.length;i++){
			if(i < arr.length - 1) {
				sb.append(arr[i].substring(0, 1)).append(".");
			}else{
				sb.append(arr[i]);
			}
		}

		System.out.println(sb.toString());
	}

}
