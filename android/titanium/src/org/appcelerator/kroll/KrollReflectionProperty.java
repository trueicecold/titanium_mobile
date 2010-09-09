/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.kroll;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.appcelerator.kroll.util.KrollReflectionUtils;
import org.appcelerator.titanium.util.AsyncResult;
import org.appcelerator.titanium.util.Log;
import org.mozilla.javascript.Context;

import android.app.Activity;

public class KrollReflectionProperty implements KrollDynamicProperty {
	private static final String TAG = "KrollReflectionProperty";
	
	protected boolean get, set, retain;
	protected String name;
	protected KrollProxy proxy;
	protected Method getMethod, setMethod;
	protected boolean runOnUiThread = false;
	
	public KrollReflectionProperty(KrollProxy proxy, String name, boolean get, boolean set, String getMethodName, String setMethodName, boolean retain) {
		this.name = name;
		this.get = get;
		this.set = set;
		this.proxy = proxy;
		
		if (get && getMethodName != null) {
			this.getMethod = KrollReflectionUtils.getMethod(proxy.getClass(), getMethodName);
		}
		if (set && setMethodName != null) {
			this.setMethod = KrollReflectionUtils.getMethod(proxy.getClass(), setMethodName);
		}
		this.retain = retain;
	}
	
	protected Object safeInvoke(KrollInvocation invocation, Method method, Object... args) {
		try {
			if (!runOnUiThread) {
				return method.invoke(proxy, args);
			} else {
				Activity activity = invocation.getTiContext().getActivity();
				if (invocation.getTiContext().isUIThread()) {
					return method.invoke(proxy, args);
				} else {
					final KrollInvocation fInv = invocation;
					final Object[] fArgs = args;
					final Method fMethod = method;
					final AsyncResult result = new AsyncResult();
					
					activity.runOnUiThread(new Runnable() {
						public void run() {
							try {
								Object retVal = fMethod.invoke(proxy, fArgs);
								result.setResult(retVal);
							} catch (Exception e) {
								result.setResult(e);
							}
						}
					});
					
					Object retVal = result.getResult();
					if (retVal instanceof Exception) {
						throw (Exception)retVal;
					} else {
						return retVal;
					}
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Exception getting/setting property: " + name, e);
			Context.throwAsScriptRuntimeEx(e);
			return KrollProxy.UNDEFINED;
		}
	}
	
	@Override
	public Object get(KrollInvocation invocation, String name) {
		if (supportsGet(name)) {
			return safeInvoke(invocation, getMethod, invocation);
		}
		
		return KrollConverter.getInstance().convertNative(invocation, proxy.getProperty(name));
	}

	@Override
	public void set(KrollInvocation invocation, String name, Object value) {
		if (supportsSet(name)) {
			if (retain) {
				proxy.setProperty(name, value);
			}
			safeInvoke(invocation, setMethod, invocation, value);
		} else {
			Object convertedValue = KrollConverter.getInstance().convertJavascript(invocation, value, Object.class);
			proxy.setProperty(name, convertedValue, true);
		}
	}

	@Override
	public boolean supportsGet(String name) {
		return get && getMethod != null;
	}

	@Override
	public boolean supportsSet(String name) {
		return set && setMethod != null;
	}

	public void setRunOnUiThread(boolean runOnUiThread) {
		this.runOnUiThread = runOnUiThread;
	}
}