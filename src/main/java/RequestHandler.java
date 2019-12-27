import com.github.alikemalocalan.tunnel.utils.DnsOverHttps;
import com.lambdista.util.Try;
import org.apache.commons.io.IOUtils;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpHeaders;
import rawhttp.core.RawHttpRequest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.concurrent.*;

public class RequestHandler implements Runnable {

	ExecutorService executor = Executors.newFixedThreadPool(5);

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
	public RequestHandler(Socket clientSocket) throws IOException {

		this.clientSocket = clientSocket;
		Try<Integer> ops = Try.apply(() -> {
			this.clientSocket.setSoTimeout(10000);
			proxyToClientBw = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

			// Send Connection established to the client
			String line = "HTTP/1.0 200 Connection established\r\n" +
					"Proxy-Agent: ProxyServer/1.0\r\n" +
					"\r\n";
			proxyToClientBw.write(line);
			proxyToClientBw.flush();

			request = new RawHttp().parseRequest(clientSocket.getInputStream());
			RawHttpHeaders headers = RawHttpHeaders.newBuilder(request.getHeaders()).remove("Proxy-Connection").build();
			request.withHeaders(headers, false);
			return 1;
		});



		if (ops.isFailure()) {
			Try.apply(() -> {
				ops.failed().get().printStackTrace();
				proxyToClientBw.close();
				clientSocket.close();
				return 1;
			});
		}

	}


	/**
	 * Reads and examines the requestString and calls the appropriate method based
	 * on the request type.
	 */
	@Override
	public void run() {
		// Check request type
		if(request != null)
		if (request.getMethod().toUpperCase().equals("CONNECT") && clientSocket.isConnected()) {
			handleHttpsRequest(clientSocket, request);
		} else handleHttpRequest(clientSocket, request);
	}

	private void handleHttpRequest(Socket clientSocket, RawHttpRequest request) {

		System.out.println("HTTP Request for : " + request.getUri() + "\n");
		// Extract the URL and port of remote
		String url = DnsOverHttps.lookUp(request.getUri().getHost());
		int port = Try.apply(() -> request.getUri().getPort()).map(p -> {
			if (p == -1) return 80;
			else return p;
		}).getOrElse(80);

		// Open a socket to the remote server
		Socket serverSocket = Try.apply(() -> new Socket(url, port)).get();

		Future<Try<Integer>> firstChunk = (Future<Try<Integer>>) executor.submit(() -> {
			Try.apply(() -> {
				request.writeTo(serverSocket.getOutputStream());
				return 1;
			});

		});

		try {
			firstChunk.get(2,TimeUnit.SECONDS);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			e.printStackTrace();
		}


		Future<Try<Integer>> dataFut = executor.submit(() -> {
			// Listen to remote server and relay to client
			Try<Integer> datas = Try.apply(() -> {
				final byte[] requestByte = new byte[4096];
				int bytes_read;
				while ((bytes_read = serverSocket.getInputStream().read(requestByte)) != -1) {
					clientSocket.getOutputStream().write(requestByte, 0, bytes_read);
					clientSocket.getOutputStream().flush();
				}
				return 1;

			});

			return datas;


		});

		Try.apply(() -> dataFut.get(10, TimeUnit.SECONDS));

		/*new Thread(() -> {
			Try<Integer> lastOps = Try.apply(() -> {
				final InputStream tmp = clientSocket.getInputStream();
				Try<Integer> isRequest = Try.apply(() -> {
					RawHttpRequest lastRequest = new RawHttp().parseRequest(tmp);
					RawHttpHeaders headers = RawHttpHeaders.newBuilder(lastRequest.getHeaders()).remove("Proxy-Connection").build();
					lastRequest.withHeaders(headers, false);
					lastRequest.writeTo(serverSocket.getOutputStream());
					clientSocket.close();
					serverSocket.close();
					return 1;
				});

				if (isRequest.isFailure()) {
					IOUtils.copy(tmp, serverSocket.getOutputStream());
				}
				return 1;
			});
			if (lastOps.isFailure()) {
				Try.apply(() -> {
					lastOps.failed().get().printStackTrace();
					return 1;
				});
			}

		}).start();*/
	}

	private void handleHttpsRequest(Socket clientSocket, RawHttpRequest request) {

		// Extract the URL and port of remote
		System.out.println("HTTPS Request for : " + request.getUri() + "\n");
		String url = DnsOverHttps.lookUp(request.getUri().getHost());
		Try<Integer> portOps = Try.apply(() -> request.getUri().getPort());

		int port = portOps.getOrElse(443);

		if (portOps.isFailure()) {
			Try.apply(() -> {
				System.out.println("HTTPS Error for : " + url + ":" + port + "\n");
				portOps.failed().get().printStackTrace();
				return 1;
			});
		}

		// Open a socket to the remote server
		Socket proxyToServerSocket = Try.apply(() -> new Socket(url, port)).get();

		new Thread(() ->
		{

			Try<Integer> ops = Try.apply(() -> IOUtils.copy(clientSocket.getInputStream(), proxyToServerSocket.getOutputStream()));
			if (ops.isFailure()) {
				Try.apply(() -> {
					clientSocket.close();
					proxyToServerSocket.close();
					return 1;
				});
			}
		}
		).start();

		// Listen to remote server and relay to client

		new Thread(() ->
		{

			Try<Integer> ops = Try.apply(() -> IOUtils.copy(proxyToServerSocket.getInputStream(), clientSocket.getOutputStream()));
			if (ops.isFailure()) {
				Try.apply(() -> {
					sendErrorResponse();
					clientSocket.close();
					proxyToServerSocket.close();
					return 1;
				});
			}
		}
		).start();


	}

	private void sendErrorResponse() {
		String line = "HTTP/1.0 504 Timeout Occured after 10s\n" +
				"User-Agent: ProxyServer/1.0\n" +
				"\r\n";
		try {
			proxyToClientBw.write(line);
			proxyToClientBw.flush();
		} catch (IOException ignored) {
		}
	}
}




