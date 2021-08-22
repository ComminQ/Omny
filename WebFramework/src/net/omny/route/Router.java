package net.omny.route;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import net.omny.utils.Debug;
import net.omny.utils.Primitive;
import net.omny.views.View;

public class Router {

	public enum StaticPolicy{
		
		FOR_EACH_REQUEST,
		ON_STARTUP_LOAD,
		REQUEST_AND_LOAD;
		
		private Consumer<Router> onChoose;

		private StaticPolicy() {}

		private StaticPolicy(Consumer<Router> onChoose) {
			this.onChoose = onChoose;
		}
	}
	
	
	private Map<String, Route> routes = new HashMap<>();

	public Router() {
		
	}
	
	public Router route(Router router) {
		this.routes.putAll(router.routes);
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
	public Router route(Object object) {
		// TODO Find all routes, using annotations
		Class<?> clazz = object.getClass();
		// Get all the method 
		return this;
	}
	
	/**
	 * Static routing for files like CSS, JS etc...
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
	 * @author Fabien CAYRE (Computer)
	 *
	 * @param staticFolder staticFolder path to folder (relative)
	 * @param policy Policy of static files routing
	 * @return
	 * @date 22/08/2021
	 */
	public Router staticRoute(String staticFolder, StaticPolicy policy) {
		if(policy == null) return this;
		if(policy == StaticPolicy.ON_STARTUP_LOAD) {
			File rootFolder = new File(staticFolder);
			if(rootFolder.isFile()) {
				throw new IllegalArgumentException("Require a folder, file was provide");
			}
			for(File subFile : rootFolder.listFiles())
				routeFile("", subFile);
		}
		return this;
	}
	
	private void routeFile(String path, File file) {
		if(file.isDirectory()) {
			for(File subFile : file.listFiles())
				routeFile(path+"/"+file.getName(), subFile);
		}
		Debug.debug("Routing {"+path+"/"+file.getName()+"}");
		route(path+"/"+file.getName(), new FileRoute(file));
	}
	
	public Router route(String path, Route route) {
		this.routes.put(path, route);
		return this;
	}
	
	public Router route(String path, String filePath) {
		return route(path, new FileRoute(filePath));
	}
	
	/**
	 * 
	 * @author Fabien CAYRE (Computer)
	 *
	 * @param request The request of the client
	 * @return true if at least one route is the path, false otherwise
	 * @date 15/08/2021
	 */
	public boolean handleRoute(Request request, Socket client) throws IOException{
		//TODO static files
		
		// Dynamic routing
		RouteLoop: for(Map.Entry<String, Route> path : this.routes.entrySet()) {
			// Split the current path to the divison
			// "/foo/bar/baz" => ["foo", "bar", "baz"]
			// Used to detect the params in URL
			
			// For example if our router register a route like this "/player/:id"
			// We can detect the route "/player/54" (the value in params is treated as String"
			
			
			//TODO use Regex for better handling
			String[] division = path.getKey().split("\\\\");
			String[] currentUrlDivision = request.getPath().split("\\\\");
			
			// The number of division is different from the current request division 
			if(division.length != currentUrlDivision.length) {
				continue;
			}
			// We compare each division
			Map<String, String> params = new HashMap<>();
			for(int i = 0; i < currentUrlDivision.length; i++) {
				if(!division[i].equals(currentUrlDivision[i])) {
					// Path are different
					continue RouteLoop;
				}
				if(division[i].startsWith(":")) {
					// If it's a URL param
					String paramName = division[i].substring(1);
					String paramValue = currentUrlDivision[i];
					params.put(paramName, paramValue);
				}
			}

			Debug.debug("Found route for "+request.getPath());
			
			// current division path are the same
			// It's the same route
			request.setParams(params);
			Response response = new Response(request);
			
			View view = path.getValue().handle(request, response);
			view.write(response);
			// The content length of the response body, in bytes

			if(response.isBinary()) {
				Debug.debug("File is binary");
				client.getOutputStream().write(response.toString().getBytes(StandardCharsets.UTF_8));
				
				client.getOutputStream().write(Primitive.toArray(response.getBody()));
				
				client.getOutputStream().write("\r\n\r\n".getBytes());
				
				client.getOutputStream().flush();
			}else {
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
