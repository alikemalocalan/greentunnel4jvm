/*
 * 	Student:		Stefano Lupo
 *  Student No:		14334933
 *  Degree:			JS Computer Engineering
 *  Course: 		3D3 Computer Networks
 *  Date:			02/04/2017
 */

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;


/**
 * The Proxy creates a Server Socket which will wait for connections on the specified port.
 * Once a connection arrives and a socket is accepted, the Proxy creates a RequestHandler object
 * on a new thread and passes the socket to it to be handled.
 * This allows the Proxy to continue accept further connections while others are being handled.
 * <p>
 * The Proxy class is also responsible for providing the dynamic management of the proxy through the console
 * and is run on a separate thread in order to not interrupt the acceptance of socket connections.
 * This allows the administrator to dynamically block web sites in real time.
 * <p>
 * The Proxy server is also responsible for maintaining cached copies of the any websites that are requested by
 * clients and this includes the HTML markup, images, css and js files associated with each webpage.
 * <p>
 * Upon closing the proxy server, the HashMaps which hold cached items and blocked sites are serialized and
 * written to a file and are loaded back in when the proxy is started once more, meaning that cached and blocked
 * sites are maintained.
 */
public class Proxy {


	// Main method for the program
	public static void main(String[] args) {
		// Create an instance of Proxy and begin listening for connections
		Proxy myProxy = new Proxy(8000);
		myProxy.listen();
	}


	private ServerSocket serverSocket;

	/**
	 * Semaphore for Proxy and Consolee Management System.
	 */
	private volatile boolean running = true;


	/**
	 * ArrayList of threads that are currently running and servicing requests.
	 * This list is required in order to join all threads on closing of server
	 */
	//static ArrayList<Thread> servicingThreads;


	/**
	 * Create the Proxy Server
	 *
	 * @param port Port number to run proxy server from.
	 */
	public Proxy(int port) {


		try {
			// Create the Server Socket for the Proxy 
			serverSocket = new ServerSocket(port);

			// Set the timeout
			//serverSocket.setSoTimeout(100000);	// debug
			System.out.println("Waiting for client on port " + serverSocket.getLocalPort() + "..");
			running = true;
		}

		// Catch exceptions associated with opening socket
		catch (SocketException se) {
			System.out.println("Socket Exception when connecting to client");
			se.printStackTrace();
		} catch (SocketTimeoutException ste) {
			System.out.println("Timeout occured while connecting to client");
		} catch (IOException io) {
			System.out.println("IO exception when connecting to client");
		}
	}


	/**
	 * Listens to port and accepts new socket connections.
	 * Creates a new thread to handle the request and passes it the socket connection and continues listening.
	 */
	public void listen() {

		while (running) {
			try {
				// serverSocket.accpet() Blocks until a connection is made
				Socket socket = serverSocket.accept();

				// Create new Thread and pass it Runnable RequestHandler
				Thread thread = new Thread(new RequestHandler(socket));

				thread.start();
			} catch (SocketException e) {
				// Socket exception is triggered by management system to shut down the proxy
				System.out.println("Server closed");
			} catch (IOException e) {
				System.out.println("Socket connection Error");
			}
		}
	}


	/**
	 * Saves the blocked and cached sites to a file so they can be re loaded at a later time.
	 * Also joins all of the RequestHandler threads currently servicing requests.
	 */
	private void closeServer() {
		System.out.println("\nClosing Server..");
		running = false;

		// Close Server Socket
		try {
			System.out.println("Terminating Connection");
			serverSocket.close();
		} catch (Exception e) {
			System.out.println("Exception closing proxy's server socket");
			e.printStackTrace();
		}

	}

}
