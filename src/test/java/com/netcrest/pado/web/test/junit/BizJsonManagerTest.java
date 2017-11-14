package com.netcrest.pado.web.test.junit;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.netcrest.pado.Pado;
import com.netcrest.pado.data.jsonlite.JsonLite;
import com.netcrest.pado.web.BizJsonManager;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class BizJsonManagerTest
{	
	private static BizJsonManager bizJsonManager = new BizJsonManager();
	private static JsonLite reply;
	
//	@BeforeClass
//	public static void loginPado() throws Exception
//	{
//		if (Pado.isClosed()) {
//			System.setProperty("gemfirePropertyFile", "etc/client/client.properties");
//			Pado.connect("localhost:20000", false);
//		}
//		testLogin();
//	}
	
	@AfterClass
	public static void closePado()
	{
		Pado.close();
	}

	@BeforeClass
	public static void testLogin() throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException, IOException
	{
		System.out.println("BizJsonManagerTest.testLogin()");
		System.out.println("------------------------------");
		JsonLite request = new JsonLite();
		request.put("method", "login");
		request.put("appid", "sys");
		request.put("domain", "pado");
		request.put("username", "dpark");
		request.put("password", "password");
		reply = bizJsonManager.invokeBiz(request);
		System.out.println(reply);
		System.out.println();
	}
	
//	@Test
//	public void testGridMapBizPut() throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException, IOException
//	{
//		System.out.println("BizJsonManagerTest.testGridMapBizPut()");
//		System.out.println("--------------------------------------");
//		JsonArray args = new JsonArray();
//		args.add(new JsonPrimitive("key1"));
//		args.add(new JsonPrimitive("value2"));
//		JsonObject request = new JsonObject();
//		request.addProperty("token", bizJsonManager.getStringValue(reply, "token"));
//		request.addProperty("ibiz", "com.netcrest.pado.biz.IGridMapBiz");
//		JsonArray cargs = new JsonArray();
//		cargs.add(new JsonPrimitive("test"));
//		request.add("cargs", cargs);
//		request.addProperty("method", "put");
//		request.add("args", args);
//		reply = bizJsonManager.invokeBiz(request);
//		System.out.println(reply);
//		System.out.println();
//	}
//	
//	@Test
//	public void testGridMapGet() throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException, IOException
//	{
//		System.out.println("BizJsonManagerTest.testGridMapBizGet()");
//		System.out.println("--------------------------------------");
//		JsonObject request = new JsonObject();
//		request.addProperty("token", bizJsonManager.getStringValue(reply, "token"));
//		request.addProperty("ibiz", "com.netcrest.pado.biz.IGridMapBiz");
//		request.addProperty("method", "get");
//		JsonArray cargs = new JsonArray();
//		cargs.add(new JsonPrimitive("test"));
//		request.add("cargs", cargs);
//		JsonArray args = new JsonArray();
//		args.add(new JsonPrimitive("key1"));
//		request.add("args", args);
//		reply = bizJsonManager.invokeBiz(request);
//		System.out.println(reply);
//		System.out.println();
//	}
//	
	@Test
	public void testGridMapBizPut() throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException, IOException
	{
		System.out.println("BizJsonManagerTest.testGridMapBizPut()");
		System.out.println("--------------------------------------");
		Object[] args = new Object[] {
				"key1", "value1" 
		};
		JsonLite request = new JsonLite();
		request.put("token", reply.get("token"));
		request.put("ibiz", "com.netcrest.pado.biz.IGridMapBiz");
		Object[] cargs = new Object[] {
				"test"
		};
		request.put("cargs", cargs);
		request.put("method", "put");
		request.put("args", args);
		reply = bizJsonManager.invokeBiz(request);
		System.out.println(reply);
		System.out.println();
	}
	
	@Test
	public void testGridMapGet() throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException, IOException
	{
		System.out.println("BizJsonManagerTest.testGridMapBizGet()");
		System.out.println("--------------------------------------");
		JsonLite request = new JsonLite();
		request.put("token", reply.get("token"));
		request.put("ibiz", "com.netcrest.pado.biz.IGridMapBiz");
		request.put("method", "get");
		Object[] cargs = new Object[] {
			"test"
		};
		request.put("cargs", cargs);
		Object[] args = new Object[] {
				"key1"
		};
		request.put("args", args);
		reply = bizJsonManager.invokeBiz(request);
		System.out.println(reply);
		System.out.println();
	}
}
