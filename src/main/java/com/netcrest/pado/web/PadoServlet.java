package com.netcrest.pado.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.netcrest.pado.IPado;
import com.netcrest.pado.Pado;
import com.netcrest.pado.data.jsonlite.JsonLite;
import com.netcrest.pado.log.Logger;

/**
 * PadoServlet is a web container for accessing Pado IBiz classes in the form of
 * RESTful API. It supports five types of requests: login, logout, catalog, and
 * ibiz. Note that the query command is not an IBiz command.The request and
 * response messages follow the conventions described below.
 * <p>
 * <h2>Request:</h2>
 * <p>
 * A request must be provided in the form of JSON string representation as
 * follows:
 * <p>
 * <b>Login</b>
 * 
 * <pre>
 * method   : "login"
 * appid    : app ID
 * domain   : optional domain name
 * username : valid user name
 * password : user password
 * </pre>
 * <p>
 * <b>Logout</b>
 * 
 * <pre>
 * token  : session token obtained from successful login
 * method : "logout"
 * </pre>
 * 
 * <b>Catalog</b>
 * 
 * <pre>
 * token   : session token obtained from successful login
 * method  : "catalog"
 * filter  : Optional regular expression for filtering the catalog. If not specified then the entire catalog is returned.
 * </pre>
 * 
 * <b>IBiz</b>
 * 
 * <pre>
 * token   : session token obtained from successful login
 * type    : "session" to keep and use the created IBiz object for the life of session
 *           "new" or undefined to create IBiz object for the request.
 * ibiz    : fully qualified class name of IBiz class
 * cargs[] : array of IBiz constructor arguments
 * method  : IBiz method name (case sensitive)
 * args[]  : array of method arguments
 * </pre>
 * 
 * <h2>Response:</h2> A response contains a JSON string representation in the
 * following format:
 * <p>
 * <b>Login</b>
 * 
 * <pre>
 * appid    : app ID
 * username : user name
 * token    : session token if successful login
 * status   : 0 if success, non-zero if failed
 * message  : login message
 * </pre>
 * 
 * <b>Logout</b>
 * 
 * <pre>
 * token    : session token
 * status   : 0 if success, non-zero if failed
 * message  : logout message
 * </pre>
 * 
 * <b>Catalog</b>
 * 
 * <pre>
 * token    : session token
 * status   : 0 if success, non-zero if failed
 * result   : a catalog of all IBiz objects allowed for the app ID 
 * message  : error message
 * </pre>
 * 
 * <b>IBiz</b>
 * 
 * <pre>
 * token    : session token
 * status   : 0 if success, non-zero if failed
 * result   : method returned value if any
 * message  : error message
 * </pre>
 * 
 * @author dpark
 *
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class PadoServlet extends HttpServlet
{

	private static final long serialVersionUID = 1L;

	// private static String locators;
	// private static boolean connected;

	private BizJsonManager bizJsonManager;
	
	/**
	 * sysPado is the system-level Pado instance that could be used for 
	 * all user requests if user authentication is not required or handled by
	 * the app server.
	 */
	private IPado sysPado;

	@Override
	public void init() throws ServletException
	{
		bizJsonManager = new BizJsonManager();

//		String pageSize = getServletConfig().getInitParameter("index.pageSize");
//		if (pageSize == null) {
//			pageSize = "10000";
//		}
//		System.setProperty("pado.index.pageSize", pageSize);
//		String locators = getServletConfig().getInitParameter("locators");
//		if (locators == null) {
//			locators = "localhost:20000";
//		}
//		String appId = getServletConfig().getInitParameter("appid");
//		if (appId == null) {
//			appId = "sys";
//		}
//		String username = getServletConfig().getInitParameter("username");
//		String password = getServletConfig().getInitParameter("password");
//		if (password == null) {
//			password = "";
//		}
//		Pado.connect(locators, false);
//		
//		
//		sysPado = Pado.login(appId, "pado", username, password.toCharArray());
//		
		
		String padoHome = System.getProperty("pado.home.dir");
		if (padoHome == null) {
			padoHome = getServletContext().getRealPath("/WEB-INF");
			System.setProperty("pado.home.dir", padoHome);
		}
		
		// padoPropertyFile - Default: etc/client/pado.properties
		String padoPropertyFile = getServletConfig().getInitParameter("pado.properties");
		if (padoPropertyFile == null) {
			padoPropertyFile = "etc/client/pado.properties";
		}
		if (padoPropertyFile.startsWith("/") == false) {
			padoPropertyFile = padoHome + "/" + padoPropertyFile;
		}
		System.setProperty("pado.properties", padoPropertyFile);
		
		// gemfirePropertyFile - Default: etc/client/client.properties
		String gemfirePropertyFile = getServletConfig().getInitParameter("gemfirePropertyFile");
		if (gemfirePropertyFile == null) {
			gemfirePropertyFile = "etc/client/client.properties";
		}
		if (gemfirePropertyFile.startsWith("/") == false) {
			gemfirePropertyFile = padoHome + "/" + gemfirePropertyFile;
		}
		System.setProperty("gemfirePropertyFile", gemfirePropertyFile);
		
		// gemfireSecurityPropertyFile - No default. It not defined then leave it null.
		String gemfireSecurityPropertyFile = getServletConfig().getInitParameter("gemfireSecurityPropertyFile");
		if (gemfireSecurityPropertyFile != null && gemfirePropertyFile.startsWith("/") == false) {
			gemfireSecurityPropertyFile = padoHome + "/" + gemfirePropertyFile;
			System.setProperty("gemfireSecurityPropertyFile", gemfirePropertyFile);
		}
		
		Pado.connect();
		sysPado = Pado.login();
	}

	@Override
	public void destroy()
	{
		super.destroy();
		bizJsonManager.disconnectFromPado();
	}
	
	private JsonLite updateArrayParameter(JsonLite jl)
	{
		Set<String> keySet = jl.keySet();
		for (String key : keySet) {
			if (key.endsWith("[]")) {
				String arrayKey = key;
				String keyPrefix = key.substring(0, key.indexOf("[]"));
				String[] args = (String[])jl.get(arrayKey);
				if (args != null) {
					ArrayList<Integer> nullIndexList = new ArrayList<Integer>(args.length + 5);
					String arrayKeyRegEx = keyPrefix + "\\[.*\\]";
					Set<Map.Entry<String, Object>> set = jl.entrySet();
					for (Map.Entry<String, Object> entry : set) {
						String key2 = entry.getKey();
						if (key2.equals(arrayKey)) {
							continue;
						} else if (key2.matches(arrayKeyRegEx)) {
							String[] value = (String[])entry.getValue();
							int index = key2.indexOf("[");
							String indexStr = key2.substring(index+1, key2.lastIndexOf(']'));
							index = Integer.parseInt(indexStr);
							nullIndexList.add(index);
						}
					}
					
					Collections.sort(nullIndexList);
					String[] newArgs = new String[args.length + nullIndexList.size()];
					for (int i = 0; i < newArgs.length; i++) {
						newArgs[i] = ""; // marker
					}
					for (int nullIndex : nullIndexList) {
						newArgs[nullIndex]  = null;
					}
					int argIndex = 0;
					for (int i = 0; i < newArgs.length; i++) {
						if (newArgs[i] != null) {
							newArgs[i] = args[argIndex++];
						}
					}
					jl.put(arrayKey, newArgs);
				}
			}
		}		
		return jl;
	}
	
	@Override
	public void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException,
			ServletException
	{
		final JsonLite jl = new JsonLite(request.getParameterMap());
		
		// post data in json format
//		StringBuffer jb = new StringBuffer();
//		String line = null;
//		try {
//			java.io.BufferedReader reader = request.getReader();
//			while ((line = reader.readLine()) != null)
//				jb.append(line);
//		} catch (Exception e) {
//			/* report an error */ 
//		}

		updateArrayParameter(jl);
		JsonLite retval;
		try {
			retval = bizJsonManager.invokeBiz(jl);
		} catch (final Exception e) {
			Logger.error("Error invoking Web Service", e);
			retval = new JsonLite();
			final String ibizClassName = (String)BizJsonManager.getFirstElementIfArray(jl, "ibiz");
			final String goMethodName = (String)BizJsonManager.getFirstElementIfArray(jl, "method");
			final Object token = BizJsonManager.getFirstElementIfArray(jl, "token");
			if (ibizClassName != null) {
				retval.put("ibiz", ibizClassName);
			}
			if (goMethodName != null) {
				retval.put("ibiz", goMethodName);
			}
			if (token != null) {
				retval.put("token", token.toString());
			}
			retval.put("status", -1);
			retval.put("message", e.getClass().getSimpleName() + ": " + e.getMessage());
		}
//		if (((Integer)retval.get("status")) < 0) {
//			response.setStatus(HttpServletResponse.SC_NOT_ACCEPTABLE);
//			response.sendError((Integer)retval.get("status"), (String)retval.get("message"));
//		} else {
			response.setCharacterEncoding("utf8");
			response.setContentType("application/json");
			final PrintWriter out = response.getWriter();
			out.print(retval);
//		}
	}

	@Override
	public void doPost(final HttpServletRequest request, final HttpServletResponse response) throws IOException,
			ServletException
	{
		doGet(request, response);
	}

	public void setBizJsonManager(final BizJsonManager bizJsonManager)
	{
		this.bizJsonManager = bizJsonManager;
	}
}
