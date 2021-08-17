package net.omny.test;



import static org.junit.Assert.assertEquals;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.nio.CharBuffer;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import net.omny.route.Code;
import net.omny.route.Response;
import net.omny.utils.Ex;
import net.omny.utils.HTTP.Version;
import net.omny.views.TextView;

public class ResponseTest{

	@Test
	public void test404ResponseAndHeader() {
		Response response = new Response();
		response.setHttpVersion(Version.V1_1);
		response.setResponseCode(Code.E404_NOT_FOUND);
		response.setHeader("Server", "Nginx");
		
		assertEquals("HTTP/1.1 404 Not Found\r\nServer: Nginx\r\n\r\n", response.toString());
	}
	
	@Test
	public void testFullResponseProcess() {
		TextView txtView = new TextView("This is a text !!!\r\n");

		StringWriter fakeClientSocket = new StringWriter();
		
		BufferedWriter writer = new BufferedWriter(fakeClientSocket);
		
		Response response = new Response();
		response.setHttpVersion(Version.V1_1);
		response.setResponseCode(Code.S200_OK);
		
		CharBuffer responseBodyBuffer = CharBuffer.allocate(512);
		txtView.write(responseBodyBuffer);
		
		Ex.grab(() -> {
			// Buffer contains response header
			writer.write(response.toChars());
			writer.flush();
			// The buffer contains the response body as byte array
			writer.write(responseBodyBuffer.array());
			writer.flush();
			// End of HTTP response following the HTTP specs
			writer.write("\r\n");
			writer.flush();
		});
		StringBuffer fullHTTPResponse = fakeClientSocket.getBuffer();
		String resultString = fullHTTPResponse.toString().trim()+"\r\n\r\n";
		assertEquals("HTTP/1.1 200 OK\r\nServer: Omny\r\n\r\nThis is a text !!!\r\n\r\n", resultString);
	}
	
}
