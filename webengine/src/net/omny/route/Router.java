package net.omny.route;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import net.omny.route.handlers.DefaultHandler;
import net.omny.route.handlers.PriorityHandler;
import net.omny.route.handlers.RequestHandler;
import net.omny.route.impl.FileRoute;
import net.omny.route.impl.LoadedFileRoute;
import net.omny.utils.Debug;
import net.omny.utils.Ex;
import net.omny.utils.Primitive;
import net.omny.views.View;

/**
 * 
 */
public class Router {

	public enum StaticPolicy {

		FOR_EACH_REQUEST, ON_STARTUP_LOAD, REQUEST_AND_LOAD;

		private StaticPolicy() {
		}
	}

	@Getter
	private Map<String, Map<Method, RouteData>> routes = new HashMap<>();
	@Getter
	private EnumMap<PriorityHandler, List<RequestHandler>> handlers = 
		new EnumMap<>(PriorityHandler.class);

	public Router() {
		// By default
		// This default handler handle static routing
		// And non-params URL dependent
		handler(new DefaultHandler());
	}

	// =========================================
	// Routing functions

	public Router route(Router router) {
		this.routes.putAll(router.routes);
		this.handlers.putAll(router.handlers);
		return this;
	}

	/**
	 * 
	 * @author Fabien CAYRE (Computer)
	 *
	 * @param object
	 * @return
	 * @date 17/08/2021
	 */
	public Router route(Class<?> clazz) {
		// Get all the method
		return Ex.grab(() -> {
			Object routesObj = clazz.getConstructor().newInstance();
			return route(routesObj);
		});
	}

	/**
	 * 
	 * @author Fabien CAYRE (Computer)
	 *
	 * @param object
	 * @return
	 * @date 17/08/2021
	 */
	public Router route(Object object) {
		Class<?> clazz = object.getClass();

		for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
			if (method.canAccess(object)) {
				if (method.isAnnotationPresent(HTTP.class)) {
					// This is a routable function
					Class<?> returnType = method.getReturnType();
					if (View.class.isAssignableFrom(returnType)) {
						// The return type is implementing View interface
						if (method.getParameterCount() == 2) {
							if (method.getParameterTypes()[0] == Request.class && method.getParameterTypes()[1] == Response.class) {
								// Both res and req are present as parameter
								HTTP annotation = method.getAnnotation(HTTP.class);
								// We get annotation content
								route(annotation.url(), (req, res) -> {
									return Ex.grab(() -> (View) method.invoke(object, req, res));
								}, annotation.method());
							}
						}
					}
				}
			}
		}

		for (Field field : clazz.getDeclaredFields()) {
			if (field.canAccess(object)) {
				if (field.isAnnotationPresent(HTTP.class)) {
					if (Route.class.isAssignableFrom(field.getType())) {
						HTTP annotation = field.getAnnotation(HTTP.class);
						route(annotation.url(), Ex.grab(() -> (Route) field.get(object)), annotation.method());
					}
				}
			}
		}

		// Get all the method
		return this;
	}

	/**
	 * Static routing for files like CSS, JS etc...
	 * 
	 * @author Fabien CAYRE (Computer)
	 *
	 * @param staticFolder path to folder (relative)
	 * @return the router
	 * @date 22/08/2021
	 */
	public Router staticRoute(String staticFolder) {
		return staticRoute(staticFolder, StaticPolicy.ON_STARTUP_LOAD);
	}

	/**
	 * Static routing for files like CSS, JS etc...
	 * 
	 * @author Fabien CAYRE (Computer)
	 *
	 * @param staticFolder staticFolder path to folder (relative)
	 * @param policy       Policy of static files routing
	 * @return
	 * @date 22/08/2021
	 */
	public Router staticRoute(String staticFolder, StaticPolicy policy) {
		if (policy == null)
			return this;
		if (policy == StaticPolicy.ON_STARTUP_LOAD) {
			File rootFolder = new File(staticFolder);
			if (rootFolder.isFile()) {
				throw new IllegalArgumentException("Require a folder, file was provide");
			}
			for (File subFile : rootFolder.listFiles())
				routeFile("", subFile);
		}
		return this;
	}

	private void routeFile(String path, File file) {
		if (file.isDirectory()) {
			for (File subFile : file.listFiles())
				routeFile(path + "/" + file.getName(), subFile);
		}
		Debug.debug("Routing {" + path + "/" + file.getName() + "}");
		route(path + "/" + file.getName(), new LoadedFileRoute(file), Method.GET);
	}

	public Router route(String path, Route route, Method method) {
		if (this.routes.containsKey(path)) {
			// It already contains path with a map
			if (this.routes.get(path).containsKey(method)) {
				// It already have a map with same path and same method
				// we throw exception
				throw new IllegalStateException("There is already a route with this path AND method");
			} else
				// We add this routes
				this.routes.get(path).put(method, new RouteData(route));
		} else {
			// there is no path with this name
			Map<Method, RouteData> map = new HashMap<>();
			map.put(method, new RouteData(route));
			this.routes.put(path, map);
		}
		return this;
	}

	public Router route(String path, String filePath) {
		return route(path, new FileRoute(filePath), Method.GET);
	}


	// =========================================
	// Handlers functions

	/**
	 * Add an handler to the current list of request handlers
	 * 
	 * @param handler The request handler
	 * @return The router object
	 */
	public Router handler(RequestHandler handler){
		return handler(handler, PriorityHandler.DEFAULT);
	}


	/**
	 * Add an handler to the current list of request handlers with priority defined
	 * @param handler The request handler
	 * @param priority The priority of this handler
	 * @return The router object
	 */
	public Router handler(RequestHandler handler, PriorityHandler priority){
		if(this.handlers.containsKey(priority)){
			// This priority already exists and there is a list linked to
			this.handlers.get(priority).add(handler);
		}else{
			// This priority doesn't exists
			// We must create a new List containing the handler
			// Then put it to this priority
			List<RequestHandler> list = new ArrayList<>();
			list.add(handler);
			this.handlers.put(priority, list);
		}
		return this;
	}

	/**
	 * Handle routing and finding response
	 * 
	 * @author Fabien CAYRE (Computer)
	 *
	 * @param request The request of the client
	 * @param client  The socket to write to
	 * @return true if at least one route is the path, false otherwise
	 * @date 15/08/2021
	 */
	public boolean handleRoute(Request request, Socket client) throws IOException {

		// Processing request handlers...
		for(PriorityHandler priority : this.handlers.keySet()){
			for(RequestHandler handler : this.handlers.get(priority)){
				if(handler.handle(this, request, client))
					// If handler returns true
					// Then we must stop processing more
					return true;
			}
		}


		// Dynamic routing
		RouteLoop: for (String path : this.routes.keySet()) {
			// Split the current path to the divison
			// "/foo/bar/baz" => ["foo", "bar", "baz"]
			// Used to detect the params in URL

			// For example if our router register a route like this "/player/:id"
			// We can detect the route "/player/54" (the value in params is treated as
			// String"

			// TODO use Regex for better handling
			String[] division = path.split("\\\\");
			String[] currentUrlDivision = request.getPath().split("\\\\");

			// The number of division is different from the current request division
			if (division.length != currentUrlDivision.length) {
				continue;
			}
			// We compare each division
			Map<String, String> params = new HashMap<>();
			for (int i = 0; i < currentUrlDivision.length; i++) {
				if (!division[i].equals(currentUrlDivision[i])) {
					// Path are different
					continue RouteLoop;
				}
				if (division[i].startsWith(":")) {
					// If it's a URL param
					String paramName = division[i].substring(1);
					String paramValue = currentUrlDivision[i];
					params.put(paramName, paramValue);
				}
			}
			RouteData routeData = null;
			if((routeData = this.routes.get(path).get(request.getMethod())) == null){
				// Path exist but not with this method
				continue RouteLoop;
			}
			Debug.debug("Found route for " + request.getPath());

			// current division path are the same
			// It's the same route
			request.setParams(params);
			Response response = new Response(request);

			View view = routeData.getRoute().handle(request, response);
			view.write(response);
			// The content length of the response body, in bytes

			if (response.isBinary()) {
				Debug.debug("File is binary");
				client.getOutputStream().write(response.toString().getBytes(StandardCharsets.UTF_8));

				client.getOutputStream().write(Primitive.toArray(response.getBody()));

				client.getOutputStream().write("\r\n\r\n".getBytes());

				client.getOutputStream().flush();
			} else {
				BufferedWriter clientWriter = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));
				// Writing header
				// Writing body content
				// End of HTTP response following the HTTP specs
				clientWriter.write(response.toString());
				// Flush the stream
				clientWriter.flush();
			}
			return true;
		}
		// Returning a 404 Not Found
		//IT'S VERY IMPORTANT, IT MUST STAY AT THE END OF EVERY ROUTES
		Response response = new Response(request);
		response.setResponseCode(Code.E404_NOT_FOUND);

		var clientStream = client.getOutputStream();
		var clientStreamWriter = new OutputStreamWriter(clientStream);

		BufferedWriter clientWriter = new BufferedWriter(clientStreamWriter);
		// Writing header
		clientWriter.write(response.toString());
		// End of HTTP response following the HTTP specs
		clientWriter.write("\r\n");
		// Flush the stream
		clientWriter.flush();
		return false;
	}

}