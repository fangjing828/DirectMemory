package org.directmemory.demo;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.directmemory.cache.CacheManager;
import org.directmemory.misc.DummyPojo;
import org.directmemory.measures.Ram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Servlet implementation class DemoServlet
 */
public class DemoServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	private int cacheSize = Ram.Mb(10);
	private CacheManager cache = new CacheManager(1000, cacheSize, 1);
       
	private static Logger logger=LoggerFactory.getLogger(DemoServlet.class);

	/**
     * @see HttpServlet#HttpServlet()
     */
    public DemoServlet() {
        super();
		logger.debug("started");
		
	    for (int i = 0; i < cacheSize / 1024 / 1.25; i++) {
			cache.put("test" + i, new DummyPojo("test"+i, 1024));
	    }
	    
	    logger.debug("finished " + cache.toString());
    }

	private DummyPojo fakeBusinessMethodWithCache(String key) throws IOException, ClassNotFoundException, InterruptedException {
		DummyPojo obj = (DummyPojo)cache.get(key);
		if (obj == null) {
			logger.debug("key not found - faking business method execution time");
			Thread.sleep(100);
			cache.put(key, new DummyPojo(key, 1024));
			logger.debug("stored object " + key);
			obj = (DummyPojo)cache.get(key);
			logger.debug("retrieved object " + key);
		}
		return obj;
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String key = request.getParameter("key");
		try {
			DummyPojo obj = fakeBusinessMethodWithCache(key);
			response.getOutputStream().println("retrieved object " + obj.name);
			response.getOutputStream().println(obj.toString());
			response.getOutputStream().println("<br /><a href=\"index.htm\">start over</a>");
			logger.debug("cache: " + cache.toString());
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			@SuppressWarnings("unused")
			DummyPojo obj = fakeBusinessMethodWithCache(request.getParameter("key"));
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		response.sendRedirect("DemoServlet?key=" + request.getParameter("key"));
		logger.debug("sent redirect to DemoServlet?key=" + request.getParameter("key"));
	}

}
;