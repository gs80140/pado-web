package com.netcrest.pado.web;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.netcrest.pado.IBiz;
import com.netcrest.pado.IBizInfo;
import com.netcrest.pado.IPado;
import com.netcrest.pado.Pado;
import com.netcrest.pado.biz.ITemporalBiz;
import com.netcrest.pado.data.jsonlite.JsonLite;
import com.netcrest.pado.exception.PadoException;
import com.netcrest.pado.exception.UnsupportedReturnType;
import com.netcrest.pado.index.provider.lucene.DateTool;
import com.netcrest.pado.index.service.IScrollableResultSet;
import com.netcrest.pado.internal.biz.util.BizUtil;
import com.netcrest.pado.internal.util.ClassUtil;
import com.netcrest.pado.log.Logger;

/**
 * BizJsonManager translates JSON requests to IBiz method calls which in turn
 * return JSON objects as responses. There are four types of JSON requests
 * supported by BizJsonManager: "login", "logout", "query", "ibiz". Note that
 * all properties listed below are case sensitive. All JSON requests are handled
 * by {@link #invokeBiz(JsonLite)}.
 * 
 * <h2>Login</h2> <h3>Request:</h3>
 * <ul>
 * <li>"method" - "login".</li>
 * <li>"appid" - App ID</li>
 * <li>"username" - User name</li>
 * <li>"password" - Password</li>
 * </ul>
 * <h3>Response:</h3>
 * <ul>
 * <li>"token" - Session token. Set upon successful login.</li>
 * <li>"status" - Response status code. 0 if success.</li>
 * <li>"message" - Response message. Used as status message.</li>
 * </ul>
 * <h2>Logout</h2> <h3>Request:</h3>
 * <ul>
 * <li>"token" - Session token.</li>
 * <li>"method" - "logout"
 * </ul>
 * <h3>Response:</h3>
 * <ul>
 * <li>"token" - Session token.</li>
 * <li>"status" - Response status code. 0 if success.</li>
 * <li>"message" - Response message. Used as status message.</li>
 * </ul>
 * <h2>Catalog</h2> <h3>Request:</h3>
 * <ul>
 * <li>"token" - Session token.</li>
 * <li>"method" - "catalog"</li>
 * <li>"filter" - Optional regular expression for filtering the catalog. If not specified then the entire catalog is returned.</li>
 * </ul>
 * <h3>Response:</h3>
 * <ul>
 * <li>"token" - Session token.</li>
 * <li>"status" - Response status code. 0 if success.</li>
 * <li>"result" - a catalog of all IBiz objects allowed for the app ID.</li>
 * </ul>
 * <h2>Query</h2> <h3>Request:</h3>
 * <ul>
 * <li>"query" - Pado Query Language (PQL) string</li>
 * <li>"validat" - Valid-at time in date format "yyyyMMddHHmmssSSS"</li>
 * <li>"asof" - As-of time in date format of "yyyyMMddHHmmssSSS"</li>
 * <li>"batch" - Streamed batch size. Default: 100</li>
 * <li>"ascend" - Ascending order flag. true to ascend, false to descend.
 * Default: true</li>
 * <li>"orderby" - Order-by field name</li>
 * <li>"refresh" - true to refresh L2 result set. Default: false</li>
 * <li>"cursor" - "next" to go to the next page (batch) of the result set.
 * "prev" to go the previous page, a numeric value to move the cursor to the
 * result index position. If the result set is not available then the query is
 * executed and the cursor is positioned at beginning of the result set.</li>
 * <li>"token" - Session token obtained upon successful login</li>
 * </ul>
 * <h3>Response:</h3>
 * <ul>
 * <li>"size" - Size of the returned result set</li>
 * <li>"total" - Total size of the result set</li>
 * <li>"cursor" - Current cursor position</li>
 * <li>"token" - Session token.</li>
 * <li>"status" - Response status code. 0 if success.</li>
 * <li>"message" - Response message. Used as status message.</li>
 * <li>"result" - Query result set.</li>
 * </ul>
 * <h2>IBiz</h2> <h3>Request:</h3>
 * <ul>
 * <li>"ibiz" - Fully-qualifying IBiz class name with comma-separated
 * constructor argument types enclosed in parentheses. Class name is case
 * sensitive.</li>
 * <li>"cargs[]" or "cargs" - IBiz constructor arguments in JsonArray. "cargs[]" takes precedence  over "cargs".</li>
 * <li>"method" - IBiz method name with comma-separated argument types enclosed
 * by parentheses. Method name is case sensitive.</li>
 * <li>"args[]" or "args" - Method arguments in JsonArray. "args[]" takes precedence  over "args".</li>
 * <li>"token" - Session token obtained upon successful login</li>
 * </ul>
 * <h3>Response:</h3>
 * <ul>
 * <li>"token" - Session token.</li>
 * <li>"status" - Response status code. 0 if success.</li>
 * <li>"message" - Response message. Used as status message.</li>
 * <li>"result" - IBiz method returned value.</li>
 * </ul>
 * 
 * @author dpark
 * 
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class BizJsonManager
{

	// <class name alias, IBiz methods>
	private static Map<String, Map<String, Method[]>> ibizClassMap = new HashMap<String, Map<String, Method[]>>();

	// indicates whether or not we are connected to Pado.
	private static boolean connected;

	private final static String TEMPORAL_BIZ_CLASS_NAME = "com.netcrest.pado.biz.ITemporalBiz";

	private String locators = "localhost:20000";

	// <token, Map<biz class name, IBiz>>
	// private Map<Object, Map<String, IBiz>> tokenBizMap = new
	// ConcurrentHashMap<Object, Map<String, IBiz>>();

	// <token, Map<query, IScrollableResultSet>>
	private Map<Object, Map<String, IScrollableResultSet<JsonLite>>> resultSetMap = new ConcurrentHashMap<Object, Map<String, IScrollableResultSet<JsonLite>>>();

	private Object getArg(final Class<?> argType, final String arg) throws NumberFormatException
	{
		if (arg == null) {
			return null;
		}
		if (argType == JsonLite.class) {
			return new JsonLite(arg);
		} else if (argType == String.class) {
			return arg;
		} else if (argType == int.class || argType == Integer.class) {
			return Integer.parseInt(arg);
		} else if (argType == double.class || argType == Double.class) {
			return Double.parseDouble(arg);
		} else if (argType == boolean.class || argType == Boolean.class) {
			return Boolean.parseBoolean(arg);
		} else if (argType == byte.class || argType == Byte.class) {
			return Byte.parseByte(arg);
		} else if (argType == char.class || argType == Character.class) {
			if (arg.length() > 0) {
				return arg.charAt(0);
			} else {
				return null;
			}
		} else if (argType == short.class || argType == Short.class) {
			return Short.parseShort(arg);
		} else if (argType == long.class || argType == Long.class) {
			return Long.parseLong(arg);
		} else if (argType == float.class || argType == Float.class) {
			return Float.parseFloat(arg);
		} else {
			return arg;
		}
	}

	/**
	 * Transforms the specified JSON element to the specified argument type.
	 * 
	 * @param suppliedArg
	 * @param argType
	 * @return
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws InvocationTargetException
	 * @throws IllegalArgumentException
	 */
	private Object getArg(final Object suppliedArg, final Class<?> argType, final Type type)
			throws InvocationTargetException, InstantiationException, IllegalAccessException, IllegalArgumentException
	{
		Object arg = null;
		if (suppliedArg == null) {
			if (argType.isPrimitive()) {
				throw new IllegalArgumentException("argument type mismatch");
			}
		} else if (isCharacter(argType) && isString(suppliedArg.getClass())) {
			arg = convertCharacterToChar((String) suppliedArg);
		} else if (isNumber(argType) && isNumber(suppliedArg.getClass())) {
			arg = convertNumberToCorrectType((Number) suppliedArg, argType);
		} else if (isBoolean(argType) && isBoolean(suppliedArg.getClass())) {
			arg = suppliedArg;
		} else if (argType.isArray() && isArray(suppliedArg.getClass())) {
			arg = convertArrayToCorrectType(suppliedArg, argType, type);
		} else if (Collection.class.isAssignableFrom(argType) && isArray(suppliedArg.getClass())) {
			arg = convertArrayToCollectionType(suppliedArg, argType, type);
		} else if (argType.isAssignableFrom(suppliedArg.getClass())) {
			arg = suppliedArg;
		} else {
			throw new IllegalArgumentException("argument type mismatch");
		}
		return arg;
	}

	private Object convertArrayToCollectionType(final Object suppliedArg, final Class<?> argType, final Type type)
			throws InstantiationException, IllegalAccessException, InvocationTargetException
	{
		final Class<?> targetCollectionType = getTargetCollectionClass(argType);
		final Collection collection = (Collection) targetCollectionType.newInstance();
		final Type elementType = getCollectionElementType(type);
		Class<?> elementClass;
		if (elementType instanceof ParameterizedType) {
			elementClass = (Class<?>) ((ParameterizedType) elementType).getRawType();
		} else {
			elementClass = (Class<?>) elementType;
		}
		for (int j = 0; j < Array.getLength(suppliedArg); j++) {
			collection.add(getArg(Array.get(suppliedArg, j), elementClass, elementType));
		}
		return collection;
	}

	private Object convertArrayToCorrectType(final Object suppliedArg, final Class<?> argType, final Type type)
			throws InvocationTargetException, InstantiationException, IllegalAccessException
	{
		final Object arg = Array.newInstance(argType.getComponentType(), Array.getLength(suppliedArg));
		for (int j = 0; j < Array.getLength(suppliedArg); j++) {
			Array.set(arg, j, getArg(Array.get(suppliedArg, j), argType.getComponentType(), type));
		}
		return arg;
	}

	private Class<?> getTargetCollectionClass(final Class<?> argType)
	{
		Class<?> targetCollectionType;
		if (Modifier.isAbstract(argType.getModifiers())) {
			if (Collection.class == argType || List.class == argType) {
				targetCollectionType = ArrayList.class;
			} else if (Set.class == argType) {
				targetCollectionType = HashSet.class;
			} else {
				throw new IllegalArgumentException("unsupported abstract collection type, use long hand notation");
			}
		} else {
			targetCollectionType = argType;
		}
		return targetCollectionType;
	}

	protected Type getCollectionElementType(final Type t)
	{
		Type typeParameter = null;
		if (t instanceof ParameterizedType) {
			final Type[] actualTypeArguments = ((ParameterizedType) t).getActualTypeArguments();
			typeParameter = actualTypeArguments[0];
		}
		return typeParameter;
	}

	private boolean isArray(final Class<?> suppliedArg)
	{
		return suppliedArg.isArray();
	}

	private boolean isString(final Class<?> suppliedArg)
	{
		return suppliedArg.equals(String.class);
	}

	private boolean isCharacter(final Class<?> argType)
	{
		return char.class.isAssignableFrom(argType) || Character.class.isAssignableFrom(argType);
	}

	private boolean isBoolean(final Class<?> argType)
	{
		return argType.equals(Boolean.class) || argType.equals(boolean.class);
	}

	private boolean isNumber(final Class<?> argType)
	{
		if (Number.class.isAssignableFrom(argType)) {
			return true;
		} else if (map.get(argType) != null) {
			return Number.class.isAssignableFrom(map.get(argType));
		}
		return false;
	}

	private char convertCharacterToChar(final String input)
	{
		if (input.length() != 1) {
			throw new IllegalArgumentException("argument type mismatch");
		}
		return input.charAt(0);
	}

	protected Object convertNumberToCorrectType(final Number suppliedNumber, final Class<?> targetClass)
	{
		Object primitive = null;

		if (targetClass == Integer.class || targetClass == int.class) {
			primitive = suppliedNumber.intValue();
		} else if (targetClass == Double.class || targetClass == double.class) {
			primitive = suppliedNumber.doubleValue();
		} else if (targetClass == Float.class || targetClass == float.class) {
			primitive = suppliedNumber.floatValue();
		} else if (targetClass == Short.class || targetClass == short.class) {
			primitive = suppliedNumber.shortValue();
		} else if (targetClass == Long.class || targetClass == long.class) {
			primitive = suppliedNumber.longValue();
		} else if (targetClass == Byte.class || targetClass == byte.class) {
			primitive = suppliedNumber.byteValue();
		}
		return primitive;
	}

	public final Map<Class<?>, Class<?>> map = new HashMap<Class<?>, Class<?>>();
	{
		map.put(boolean.class, Boolean.class);
		map.put(byte.class, Byte.class);
		map.put(short.class, Short.class);
		map.put(char.class, Character.class);
		map.put(int.class, Integer.class);
		map.put(long.class, Long.class);
		map.put(float.class, Float.class);
		map.put(double.class, Double.class);
	}

	/**
	 * Invokes the specified IBiz method with the array elements found in the
	 * specified JSON array as its arguments.
	 * 
	 * @param biz
	 *            IBiz object
	 * @param methodName
	 *            IBiz method name
	 * @param jsonArrayStr
	 *            JSON array as the method arguments
	 * 
	 * @return JSON IBiz method returned value transformed to JsonLite
	 * @throws InvocationTargetException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws IOException
	 * @throws Exception
	 */
	public Object invoke(final IBiz biz, final String methodName, final String... args) throws NoSuchMethodException,
			InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException,
			IOException
	{
		if (biz == null) {
			return null;
		}

		List<Method> matchingMethods = null;
		List<Class<?>> paramTypes = null;
		final int indexOfBracket = methodName.indexOf("(");
		if (indexOfBracket > 0) {
			final String methodNameWithOutTypes = methodName.substring(0, indexOfBracket);
			String paramStrings = methodName.substring(indexOfBracket + 1, methodName.length() - 1);
			paramStrings = paramStrings.trim();
			if (paramStrings.length() > 0) {
				try {
					paramTypes = parseParamTypes(paramStrings);
				} catch (final ClassNotFoundException e) {
					throw new NoSuchMethodException("Parameter type not found " + e);
				}
			}
			matchingMethods = findAllMatchingMethods(biz, methodNameWithOutTypes, args == null ? 0 : args.length,
					paramTypes);
		} else {
			matchingMethods = findAllMatchingMethods(biz, methodName, args == null ? 0 : args.length, null);
		}
		if (matchingMethods.size() > 1) {
			throw new NoSuchMethodException(
					"More than one matching method found, can not determine which to invoke. Specify parameter types on method name");
		}

		Object result = null;
		Method method = matchingMethods.get(0);
		if (isSupportedMethodReturnType(method) == false) {
			throw new UnsupportedReturnType("Return type unspported for web serivce invocation: " + method.toString() + ". Supported types are void, String, primitives, enum, Date and JsonLite.");
		}
		if (args == null) {
			result = invokeNoArgumentMethod(biz, method);
		} else { // method with args
			result = invokeMethodWithArguments(biz, paramTypes, args, method);
		}
		return result;
	}

	/**
	 * Returns true if the specified method is void, String, primitive, primitive wrapper,
	 * enum, java.util.Date, or JsonLite.
	 * 
	 * @param method
	 *            Method to check for supported return type.
	 */
	private boolean isSupportedMethodReturnType(Method method)
	{
		Class<?> returnType = method.getReturnType();
		boolean supported = (ClassUtil.isPrimitiveBase(returnType) 
				|| returnType.isAssignableFrom(JsonLite.class) 
				|| returnType.isAssignableFrom(Date.class)
				|| returnType == void.class)
			&& !returnType.isArray();
		return supported;
	}

	private List<Method> findAllMatchingMethods(final IBiz biz, final String methodName, final int nrOfArgs,
			final List<Class<?>> paramTypes) throws NoSuchMethodException
	{
		final List<Method> matchingMethods = new ArrayList<Method>();
		final Class<?> ibizClass = biz.getClass();
		final String id = BizUtil.getBizName(ibizClass, true);
		final Map<String, Method[]> methodMap = ibizClassMap.get(id);
		if (methodMap == null) {
			throw new NoSuchMethodException("Methods not found: method=" + methodName + ". " + biz.getClass());
		}
		final Method[] methods = methodMap.get(methodName);
		if (methods == null || methods.length == 0) {
			throw new NoSuchMethodException(methodName + " does not exist in class " + biz.getClass());
		}
		for (final Method method : methods) {
			final Class<?>[] methodParams = method.getParameterTypes();
			if (methodParams.length == nrOfArgs) {
				if (paramTypes == null) {
					matchingMethods.add(method);
				} else {
					if (parameterTypesAreCorrect(paramTypes, methodParams)) {
						matchingMethods.add(method);
					}
				}
			}
		}
		if (matchingMethods.size() == 0) {
			if (paramTypes == null) {
				throw new IllegalArgumentException("wrong number of arguments");
			} else {
				String pTypes = "";
				for (final Class<?> c : paramTypes) {
					pTypes = pTypes + c.getName() + ", ";
				}
				throw new NoSuchMethodException("No method with name " + methodName + " and parameter types " + pTypes
						+ " was found for the given input");
			}
		}
		return matchingMethods;
	}

	private boolean parameterTypesAreCorrect(final List<Class<?>> paramTypes, final Class<?>[] methodParams)
	{
		boolean allParamsMatching = true;
		for (int i = 0; i < methodParams.length; i++) {
			if (methodParams[i].isPrimitive()) {
				if (!paramTypes.get(i).isAssignableFrom(methodParams[i])) {
					allParamsMatching = false;
					break;
				}
			} else if (!methodParams[i].isAssignableFrom(paramTypes.get(i))) {
				allParamsMatching = false;
				break;
			}
		}
		return allParamsMatching;
	}

	private List<Class<?>> parseParamTypes(final String params) throws ClassNotFoundException
	{
		final List<Class<?>> paramTypes = new ArrayList<Class<?>>();
		final String[] singleParams = params.split(",");
		for (String paramString : singleParams) {
			paramString = paramString.trim().toLowerCase();
			Class<?> clazz = stringToPrimitiveClassMap.get(paramString);
			if (clazz == null) { // string does not represent primitive class
				clazz = Class.forName(paramString);
			}
			paramTypes.add(clazz);
		}
		return paramTypes;
	}

	public final Map<String, Class<?>> stringToPrimitiveClassMap = new HashMap<String, Class<?>>();
	{
		stringToPrimitiveClassMap.put("object", Object.class);
		stringToPrimitiveClassMap.put("string", String.class);
		stringToPrimitiveClassMap.put("boolean", Boolean.class);
		stringToPrimitiveClassMap.put("byte", Byte.class);
		stringToPrimitiveClassMap.put("short", Short.class);
		stringToPrimitiveClassMap.put("char", Character.class);
		stringToPrimitiveClassMap.put("int", Integer.class);
		stringToPrimitiveClassMap.put("long", Long.class);
		stringToPrimitiveClassMap.put("float", Float.class);
		stringToPrimitiveClassMap.put("double", Double.class);
	}

	private Object invokeMethodWithArguments(final IBiz biz, final List<Class<?>> paramTypes, final String[] argArray,
			final Method method) throws InvocationTargetException, InstantiationException, IllegalAccessException,
			IOException
	{
		final Object[] args = new Object[argArray.length];
		Class<?> methodParamTypes[] = method.getParameterTypes();
		for (int i = 0; i < argArray.length; i++) {
			if (paramTypes == null || paramTypes.size() <= i) {
				args[i] = getArg(methodParamTypes[i], argArray[i]);
			} else {
				args[i] = getArg(paramTypes.get(i), argArray[i]);
			}
		}
		return method.invoke(biz, args);
	}

	private Object invokeNoArgumentMethod(final IBiz biz, final Method method) throws IllegalAccessException,
			InvocationTargetException
	{
		return method.invoke(biz);
	}

	/**
	 * Registers all of the IBiz classes obtained from the catalog of the
	 * specified Pado instance.
	 * 
	 * @param pado
	 *            Pado instance
	 */
	private void register(final IPado pado)
	{
		if (pado == null) {
			return;
		}

		final Class<?> ibizClasses[] = pado.getCatalog().getAllBizClasses();
		for (final Class<?> ibizClass : ibizClasses) {
			registerSingleClass(ibizClass);
		}
	}

	protected void registerSingleClass(final Class<?> ibizClass)
	{
		final Map<String, Method[]> map = BizUtil.getMethodMap(ibizClass);
		final String name = BizUtil.getBizName(ibizClass, true);
		ibizClassMap.put(name, map);
	}

	/**
	 * Logs in to Pado with the user login information obtained from the
	 * specified JSON object. Note that if the user has already been logged on
	 * then it returns the existing Pado instance.
	 * 
	 * @param requesty
	 *            = Login request that contains the following properties:
	 *            "appid", "username", and "password"
	 * @return Pado instance upon successful login.
	 * @throws PadoException
	 *             Thrown if login fails
	 */
	protected IPado login(final JsonLite request) throws PadoException
	{

		final String appId = (String) BizJsonManager.getFirstElementIfArray(request, "appid");
		final String username = (String) BizJsonManager.getFirstElementIfArray(request, "username");
		final String pw = (String) BizJsonManager.getFirstElementIfArray(request, "password");

		IPado pado = Pado.getPado(username);
		if (pado != null) {
			return pado;
		}
		pado = Pado.login(appId, "pado", username, pw == null ? null: pw.toCharArray());
		return pado;
	}

	public JsonLite queryTemporal(final JsonLite request)
	{
		final JsonLite retval = new JsonLite();

		final Object token = BizJsonManager.getFirstElementIfArray(request, "token");
		if (token != null) {
			retval.put("token", token.toString());
		}
		String query = (String) BizJsonManager.getFirstElementIfArray(request, "query");
		if (query != null) {
			query = query.trim();
		}
		String asOfStr = (String) BizJsonManager.getFirstElementIfArray(request, "asof");
		String validAtStr = (String) BizJsonManager.getFirstElementIfArray(request, "validat");
		if (query == null || query.length() == 0) {
			updateResponseObjectUnsuccessfulStatus(retval, token, "Invalid query statement.");
			return retval;
		}

		// PQL
		final IPado pado = Pado.getPado(token);
		if (pado == null) {
			Logger.error("Error executing query. Invalid token " + token);
			updateResponseObjectUnsuccessfulStatus(retval, token,
					"Invalid Token, you are not logged in or your session has timed out.");
		} else {
			final String type = (String) getFirstElementIfArray(request, "type");
			ITemporalBiz<Object, JsonLite> temporalBiz = null;
			if (type != null && type.equalsIgnoreCase("session")) {
				Map<String, IBiz> bizMap = (Map<String, IBiz>) pado.getUserData();
				if (bizMap != null) {
					temporalBiz = (ITemporalBiz) bizMap.get(TEMPORAL_BIZ_CLASS_NAME);
				}
			}
			if (temporalBiz == null) {
				temporalBiz = pado.getCatalog().newInstance(ITemporalBiz.class);
				if (type != null && type.equalsIgnoreCase("session")) {
					Map<String, IBiz> bizMap = (Map<String, IBiz>) pado.getUserData();
					if (bizMap == null) {
						bizMap = new HashMap(5);
						pado.setUserData(bizMap);
					}
					bizMap.put(TEMPORAL_BIZ_CLASS_NAME, temporalBiz);
				}
			}
			long validAt = -1;
			long asOf = -1;
			if (validAtStr != null) {
				try {
					validAtStr = validAtStr.trim();
					validAt = DateTool.stringToTime(validAtStr);
				} catch (ParseException e) {
					updateResponseObjectUnsuccessfulStatus(retval, token,
							"Invalid validat time. Time format must be yyyyMMddHHmmssSSS");
					return retval;
				}
			}
			if (asOfStr != null) {
				try {
					asOfStr = asOfStr.trim();
					asOf = DateTool.stringToTime(asOfStr);
				} catch (ParseException e) {
					updateResponseObjectUnsuccessfulStatus(retval, token,
							"Invalid asof time. Time format must be yyyyMMddHHmmssSSS");
					return retval;
				}
			}

			boolean ascendingOrder = true;
			int batchSize = -1;
			boolean forceRebuildIndex = false;
			boolean next = true;
			int cursorPosition = -1;

			final String orderBy = (String) BizJsonManager.getFirstElementIfArray(request, "orderby");
			final String ascendingOrderStr = (String) BizJsonManager.getFirstElementIfArray(request, "ascending");
			final String batchSizeStr = (String) BizJsonManager.getFirstElementIfArray(request, "batch");
			final String refreshStr = (String) BizJsonManager.getFirstElementIfArray(request, "refresh");
			final String cursorStr = (String) BizJsonManager.getFirstElementIfArray(request, "cursor");
			if (batchSizeStr != null) {
				batchSize = Integer.parseInt(batchSizeStr);
			}
			if (refreshStr != null) {
				forceRebuildIndex = refreshStr.equalsIgnoreCase("true");
			}
			if (ascendingOrderStr != null) {
				ascendingOrder = !refreshStr.equalsIgnoreCase("false");
			}
			if (cursorStr != null) {
				try {
					cursorPosition = Integer.parseInt(cursorStr);
					if (cursorPosition < 0) {
						cursorPosition = 0;
					}
				} catch (NumberFormatException ex) {
					// ignore;
				}
				if (cursorPosition == -1) {
					next = !cursorStr.equalsIgnoreCase("prev");
				}
			}

			IScrollableResultSet<JsonLite> sr = null;
			Map<String, IScrollableResultSet<JsonLite>> map = resultSetMap.get(token);
			if (map != null) {
				sr = map.get(getQueryId(query, validAtStr, asOfStr));
			}

			if (sr == null || forceRebuildIndex) {
				if (batchSize <= 0) {
					batchSize = 100;
				}
				sr = temporalBiz.getValueResultSet(query, validAt, asOf, orderBy, ascendingOrder, batchSize,
						forceRebuildIndex);
				if (sr != null) {
					map = resultSetMap.get(token);
					if (map == null) {
						map = new ConcurrentHashMap<String, IScrollableResultSet<JsonLite>>();
						resultSetMap.put(token, map);
					}
					map.put(getQueryId(query, validAtStr, asOfStr), sr);
				}
			} else if (cursorPosition == -1) {
				if (batchSize > 0) {
					sr.setFetchSize(batchSize);
				}
				if (next) {
					if (sr.nextSet() == false) {
						updateResponseObjectUnsuccessfulStatus(retval, token, "End of result set reached.");
						return retval;
					}
				} else {
					if (sr.previousSet() == false) {
						updateResponseObjectUnsuccessfulStatus(retval, token, "Top of result set.");
						return retval;
					}
				}
			} else {
				if (batchSize > 0) {
					sr.setFetchSize(batchSize);
				}
				if (sr.goToSet(cursorPosition) == false) {
					updateResponseObjectUnsuccessfulStatus(retval, token, "End of result set reached.");
					return retval;
				}
			}
			if (sr == null) {
				if (map != null) {
					map.remove(query);
					if (map.size() == 0) {
						resultSetMap.get(token);
					}
				}
				retval.put("status", "No results.");
			} else {
				List<JsonLite> list = sr.toList();
				retval.put("result", list);
				retval.put("cursor", sr.getCurrentIndex());
				retval.put("size", list.size());
				retval.put("total", sr.getTotalSize());
			}
		}

		return retval;
	}

	private String getQueryId(String queryString, String validAtStr, String asOfStr)
	{
		return queryString + "," + validAtStr + "," + asOfStr;
	}
	
	private void parseMethodCalls(String methodCalls)
	{
		// get((String)key1);
		String split[] = methodCalls.split(";");
		MethodCall mc[] = new MethodCall[split.length];
		for (int i = 0; i < split.length; i++) {
			mc[i] = new MethodCall();
			String mcStr = split[i];
			int index = mcStr.indexOf('(');
			if (index == -1) {
				mc[i].methodName = mcStr;
			} else {
				mc[i].methodName = mcStr.substring(0, index);
				mcStr = mcStr.substring(index+1);
				
			}
			
		}
		
	}
	
	class MethodCall
	{
		String methodName;
		String arg;
	}

	/**
	 * Invokes the IBiz method found in the specified JSON request.
	 * 
	 * @param request
	 *            JSON request
	 * @return IBiz method return value
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 * @throws IOException
	 */
	public JsonLite invokeBiz(final JsonLite request) throws InstantiationException, IllegalAccessException,
			IllegalArgumentException, InvocationTargetException, IOException
	{
		final Object[] query = (Object[]) request.get("query");
		if (query != null) {
			return queryTemporal(request);
		}

		final JsonLite retval = new JsonLite();
		String ibizClassName = (String) BizJsonManager.getFirstElementIfArray(request, "ibiz");
		if (ibizClassName != null) {
			ibizClassName = ibizClassName.trim();
		}
		String bizMethodName = (String) BizJsonManager.getFirstElementIfArray(request, "method");
		if (bizMethodName != null) {
			bizMethodName = bizMethodName.trim();
		}
		final Object token = BizJsonManager.getFirstElementIfArray(request, "token");
		if (token != null) {
			retval.put("token", token.toString());
		}

		if ("login".equals(bizMethodName)) {
			final String appId = (String) BizJsonManager.getFirstElementIfArray(request, "appid");
			final String username = (String) BizJsonManager.getFirstElementIfArray(request, "username");
			retval.put("appid", appId);
			retval.put("username", username);
			try {
				final IPado pado = login(request);
				if (pado == null) {
					retval.put("status", -1);
					retval.put("message", "Login failed");
				} else {
					retval.put("gid", pado.getGridId());
					retval.put("token", pado.getToken().toString());
					retval.put("status", 0);
					retval.put("message", "Login success");
					register(pado);
				}
			} catch (final Exception ex) {
				// Invalid user name / password will come here..
				Logger.error("Error calling login", ex);
				retval.put("status", -1);
				retval.put("message", ex.toString());
				disconnectFromPadoIfConnectionError(ex);
			}
		} else if ("logout".equals(bizMethodName)) {
			if (token != null) {
				final IPado pado = Pado.getPado(token);
				if (pado != null) {
					try {
						pado.logout();
						retval.put("status", 0);
						retval.put("message", "Logout success");
					} catch (final Exception ex) {
						Logger.warning("Pado.logout() failed", ex);
						updateResponseObjectUnsuccessfulStatus(retval, token, "Logout failed: " + ex);
					}
				} else {
					updateResponseObjectUnsuccessfulStatus(retval, token,
							"Invalid Token, you are not logged in or your session has timed out.");
				}
			}
		} else if ("catalog".equals(bizMethodName)) {
			if (token != null) {
				final IPado pado = Pado.getPado(token);
				if (pado != null) {
					try {
						final String filter = (String) BizJsonManager.getFirstElementIfArray(request, "filter");
						IBizInfo[] bizInfos = pado.getCatalog().getBizInfos(filter);
						if (bizInfos != null && bizInfos.length > 0) {
							JsonLite bizInfoJls[] = new JsonLite[bizInfos.length];
							int i = 0;
							for (IBizInfo bizInfo : bizInfos) {
								bizInfoJls[i++] = bizInfo.toJson();
							}
							retval.put("catalog", bizInfoJls);
						}
						retval.put("status", 0);
						retval.put("message", "Catalog success");
					} catch (final Exception ex) {
						Logger.warning("Pado.logout() failed", ex);
						updateResponseObjectUnsuccessfulStatus(retval, token, "Logout failed: " + ex);
					}
				} else {
					updateResponseObjectUnsuccessfulStatus(retval, token,
							"Invalid Token, you are not logged in or your session has timed out.");
				}
			}
		} else {
			// IBiz method calls
			final IPado pado = Pado.getPado(token);
			if (pado == null) {
				Logger.error("Error calling " + ibizClassName + "." + bizMethodName + ". Invalid token " + token);
				updateResponseObjectUnsuccessfulStatus(retval, token,
						"Invalid Token, you are not logged in or your session has timed out.");
			} else {
				try {
					IBiz biz = null;
					final String cacheStr = (String) getFirstElementIfArray(request, "cache");
					final boolean isCacheInstance = cacheStr != null && cacheStr.equalsIgnoreCase("true");
					if (isCacheInstance) {
						Map<String, IBiz> bizMap = (Map<String, IBiz>) pado.getUserData();
						if (bizMap != null) {
							biz = bizMap.get(ibizClassName);
						}
					}

					if (biz == null) {
						String classNameWithOutTypes;
						final int indexOfBracket = ibizClassName.indexOf("(");
						if (indexOfBracket > 0) {
							classNameWithOutTypes = ibizClassName.substring(0, indexOfBracket);
						} else {
							classNameWithOutTypes = ibizClassName;
						}
						String[] cbizArgs = (String[]) request.get("cargs[]");
						if (cbizArgs == null) {
							cbizArgs = (String[]) request.get("cargs");
						}
						Object[] cargs = null;
						if (cbizArgs != null) {
							if (indexOfBracket > 0) {
								final String paramStrings = ibizClassName.substring(indexOfBracket + 1,
										ibizClassName.length() - 1);
								try {
									List<Class<?>> paramTypes = parseParamTypes(paramStrings);
									cargs = new Object[cbizArgs.length];
									for (int i = 0; i < cbizArgs.length; i++) {
										cargs[i] = getArg(paramTypes.get(i), cbizArgs[i]);
									}
								} catch (final ClassNotFoundException e) {
									throw new NoSuchMethodException("Parameter type not found " + e);
								}
							}
							if (cargs == null && cbizArgs.length > 0) {
								cargs = cbizArgs;
							}
						}
						biz = pado.getCatalog().newInstance(classNameWithOutTypes, cargs);
						if (isCacheInstance) {
							Map<String, IBiz> bizMap = (Map<String, IBiz>) pado.getUserData();
							if (bizMap == null) {
								bizMap = new HashMap(5);
								pado.setUserData(bizMap);
							}
							bizMap.put(ibizClassName, biz);
						}
					}
					if (biz == null) {
						updateResponseObjectUnsuccessfulStatus(retval, token,
								"Could not get new instance of IBiz class " + ibizClassName);
					} else {
						String[] args = (String[]) request.get("args[]");
						if (args == null) {
							args = (String[]) request.get("args");
						}
						try {
							final Object result = invoke(biz, bizMethodName, args);
							if (result == null) {
								retval.put("status", 0);
							} else { // void method called - no result
								retval.put("status", 0);
								retval.put("result", result);
							}

						} catch (final NoSuchMethodException e) {
							Logger.error("Error calling " + ibizClassName + "." + bizMethodName, e);
							updateResponseObjectUnsuccessfulStatus(retval, token, "IBiz method does not exist: "
									+ bizMethodName);
						} catch (final IllegalArgumentException e) {
							Logger.error("Error calling " + ibizClassName + "." + bizMethodName, e);
							updateResponseObjectUnsuccessfulStatus(retval, token,
									"IllegalArgumentException while invoking " + bizMethodName + ": " + e.getMessage());
						}
					}
				} catch (final InvocationTargetException e) {
					Logger.error("Error calling " + ibizClassName + "." + bizMethodName, e);
					updateResponseObjectUnsuccessfulStatus(retval, token, "Exception while trying to invoke method:"
							+ bizMethodName + " on IBiz class: " + ibizClassName + ". Message: "
							+ e.getCause().toString());
				} catch (final Exception e) {
					Logger.error("Error calling " + ibizClassName + "." + bizMethodName, e);
					updateResponseObjectUnsuccessfulStatus(retval, token, "Exception while trying to invoke method:"
							+ bizMethodName + " on IBiz class: " + ibizClassName + ". Message: " + e.toString());
				}
			}
		}
		return retval;
	}

	protected void updateResponseObjectUnsuccessfulStatus(final JsonLite retval, final Object token,
			final String message)
	{
		if (token != null) {
			retval.put("token", token.toString());
		}
		retval.put("status", -1);
		retval.put("message", message);
	}

	public void setLocators(final String locators)
	{
		this.locators = locators;
	}

	protected void connectToPado()
	{
		// The Pado connect method is static so we must guard against it being
		// called by mutliple threads.
		synchronized (BizJsonManager.class) {
			if (!connected) {
				try {
					Logger.info("Connecting to " + locators);
					Pado.connect(locators, false);
					connected = true;
				} catch (final PadoException ex) {
					Logger.severe(ex);
					disconnectFromPado();
					throw ex;
				}
			}
		}
	}

	protected void disconnectFromPado()
	{
		// The Pado close method is static so we must guard against it being
		// called by mutlipled threads.
		synchronized (BizJsonManager.class) {
			try {
				Logger.info("Disconnecting from " + locators);
				Pado.close();
			} catch (final Exception ex) {
				Logger.error(ex);
			}
			connected = false;
		}
	}

	protected void disconnectFromPadoIfConnectionError(final Exception ex)
	{
		if (ex instanceof PadoException && ex.getCause() != null
				&& ex.getCause().getClass().getSimpleName().equals("NoAvailableLocatorsException")) {
			disconnectFromPado();
		}
	}

	/**
	 * Returns the first element if the value is an array. Otherwise, returns
	 * the value.
	 * 
	 * @param jl
	 *            JsonLite object
	 * @param key
	 *            Key
	 */
	public static Object getFirstElementIfArray(JsonLite jl, String key)
	{
		if (jl == null) {
			return null;
		}
		Object value = jl.get(key);
		if (value == null) {
			return null;
		}
		if (value.getClass().isArray()) {
			if (Array.getLength(value) > 0) {
				value = Array.get(value, 0);
			}
		}
		return value;
	}

	class SessionData
	{
		SessionData(IPado pado, IBiz biz)
		{
			this.pado = pado;
			this.biz = biz;
		}

		IPado pado;
		IBiz biz;
	}
}
