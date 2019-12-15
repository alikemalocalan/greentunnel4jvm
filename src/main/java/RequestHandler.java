import org.apache.commons.io.IOUtils;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpRequest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class RequestHandler implements Runnable {

	/**
	 * Socket connected to client passed by Proxy server
	 */
	Socket clientSocket;

	/**
	 * Send data from proxy to client
	 */
	BufferedWriter proxyToClientBw;

	RawHttpRequest request = null;

	/**
	 * Creates a ReuqestHandler object capable of servicing HTTP(S) GET requests
	 *
	 * @param clientSocket socket connected to the client
	 */
	public RequestHandler(Socket clientSocket) {
		this.clientSocket = clientSocket;
		try {
			this.clientSocket.setSoTimeout(10000);
			proxyToClientBw = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

			// Send Connection established to the client
			String line = "HTTP/1.0 200 Connection established\r\n" +
					"Proxy-Agent: ProxyServer/1.0\r\n" +
					"\r\n";
			proxyToClientBw.write(line);
			proxyToClientBw.flush();

			request = new RawHttp().parseRequest(clientSocket.getInputStream());
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	/**
	 * Reads and examines the requestString and calls the appropriate method based
	 * on the request type.
	 */
	@Override
	public void run() {
		// Check request type
		if (request.getMethod().equals("CONNECT")) {
			System.out.println("HTTPS Request for : " + request.getUri() + "\n");
			handleHTTPSRequest();
		}
	}

	private void handleHTTPSRequest() {

		// Extract the URL and port of remote 
		String url = request.getUri().getHost();
		int port = request.getUri().getPort();

		try {
			// Open a socket to the remote server 
			Socket proxyToServerSocket = new Socket(url, port);
			proxyToServerSocket.setSoTimeout(20000);

			/*
			 Create a new thread to listen to client and transmit to server
			 Thread that is used to transmit data read from client to server when using HTTPS
			 Reference to this is required, so it can be closed once completed.
			 */
			new Thread(() ->
			{
				try {
					IOUtils.copy(
							clientSocket.getInputStream(), proxyToServerSocket.getOutputStream());
				} catch (IOException e) {
					try {
						proxyToServerSocket.close();
					} catch (IOException ignored) {
					}
				}
			}
			).start();

			// Listen to remote server and relay to client

			new Thread(() ->
			{
				try {
					IOUtils.copy(proxyToServerSocket.getInputStream(), clientSocket.getOutputStream());
				} catch (IOException e) {
					try {
						proxyToServerSocket.close();
					} catch (IOException ignored) {
					}
				}
			}
			).start();



		} catch (SocketTimeoutException e) {
			String line = "HTTP/1.0 504 Timeout Occured after 10s\n" +
					"User-Agent: ProxyServer/1.0\n" +
					"\r\n";
			try {
				proxyToClientBw.write(line);
				proxyToClientBw.flush();
			} catch (IOException ignored) {
			}
		} catch (Exception e) {
			System.out.println("Error on HTTPS : " + request.getUri());
			e.printStackTrace();
		}
	}
}




