package org.apache.hadoop.mapred.bufmanager;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.WritableUtils;
import org.apache.hadoop.io.compress.CodecPool;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.DefaultCodec;
import org.apache.hadoop.ipc.RPC;
import org.apache.hadoop.ipc.RPC.Server;
import org.apache.hadoop.mapred.IFile;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.JobID;
import org.apache.hadoop.mapred.MapOutputFile;
import org.apache.hadoop.mapred.TaskAttemptID;
import org.apache.hadoop.mapred.TaskID;
import org.apache.hadoop.mapred.TaskTracker;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.util.ReflectionUtils;

public class BufferController extends Thread implements BufferUmbilicalProtocol {
	private class RequestHandler extends Thread {
		
		public void run() {
			List<TaskAttemptID> handleCommitted = new ArrayList<TaskAttemptID>();
			while (true) {
				handleCommitted.clear();
				handleCommitted.addAll(committed);

				for (TaskAttemptID taskid : handleCommitted) {
					List<BufferRequest> handleRequests = new ArrayList<BufferRequest>();
					synchronized (requests) {
						if (requests.containsKey(taskid)) {
							handleRequests.addAll(requests.get(taskid));
						}
					}
					
					if (handleRequests.size() > 0) {
						handleRequests = handle(taskid, handleRequests);
						
						synchronized (requests) {
							requests.get(taskid).removeAll(handleRequests);
							if (requests.get(taskid).size() == 0) {
								requests.remove(taskid);
							}
						}
					}
				}

				synchronized (requests) {
					try {
						if (requests.size() > 0) {
							requests.wait(1000);
						}
						else {
							requests.wait();
						}
					} catch (InterruptedException e) { }
				}
			}
		}
		
		private List<BufferRequest> handle(TaskAttemptID taskid, List<BufferRequest> handle) {
			List<BufferRequest> handled = new ArrayList<BufferRequest>();
			try {
				JobConf job = tracker.getJobConf(taskid);
				for (BufferRequest request : handle) {
					if (request.open(job)) {
						executor.execute(request);
						handled.add(request);
					}
					else {
						System.err.println("\tRequestHandler: can't open request " + request);
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			return handled;
		}
	}
	
    private TaskTracker tracker;
    
    private RequestHandler requestHandler;
    
	private String hostname;
	
	private Executor executor;

	private Map<TaskAttemptID, TreeSet<BufferRequest>> requests = new HashMap<TaskAttemptID, TreeSet<BufferRequest>>();
	
	private Set<TaskAttemptID> committed = Collections.synchronizedSet(new HashSet<TaskAttemptID>());
	
	private Server server;

	private ServerSocketChannel channel;
	
	private int controlPort;
	

	public BufferController(TaskTracker tracker) throws IOException {
		this.tracker   = tracker;
		this.requestHandler = new RequestHandler();
		this.executor  = Executors.newCachedThreadPool();
		this.hostname  = InetAddress.getLocalHost().getCanonicalHostName();
	}

	public static InetSocketAddress getControlAddress(Configuration conf) {
		try {
			int port = conf.getInt("mapred.buffer.manager.data.port", 9021);
			String address = InetAddress.getLocalHost().getCanonicalHostName();
			address += ":" + port;
			return NetUtils.createSocketAddr(address);
		} catch (Throwable t) {
			return NetUtils.createSocketAddr("localhost:9021");
		}
	}

	public static InetSocketAddress getServerAddress(Configuration conf) {
		try {
			String address = InetAddress.getLocalHost().getCanonicalHostName();
			int port = conf.getInt("mapred.buffer.manager.control.port", 9020);
			address += ":" + port;
			return NetUtils.createSocketAddr(address);
		} catch (Throwable t) {
			return NetUtils.createSocketAddr("localhost:9020");
		}
	}

	private void initialize() throws IOException {
		Configuration conf = tracker.conf();
		int maxMaps = conf.getInt("mapred.tasktracker.map.tasks.maximum", 2);
		int maxReduces = conf.getInt("mapred.tasktracker.reduce.tasks.maximum", 2);

		InetSocketAddress serverAddress = getServerAddress(conf);
		this.server = RPC.getServer(this, serverAddress.getHostName(), serverAddress.getPort(), 
				maxMaps + maxReduces, false, conf);
		this.server.start();

		this.requestHandler.start();
		
		/** The server socket and selector registration */
		InetSocketAddress controlAddress = getControlAddress(conf);
		this.controlPort = controlAddress.getPort();
		this.channel = ServerSocketChannel.open();
		this.channel.socket().bind(controlAddress);
	}
	
	public void free(JobID jobid) {
	}
	
	@Override
	public void commit(TaskAttemptID taskid) throws IOException {
		synchronized (requests) {
			if (!taskid.isMap()) { 
				MapOutputFile mof = new MapOutputFile(taskid.getJobID());
				Path file = mof.getOutputFile(taskid);
				FileSystem fs = FileSystem.getLocal(tracker.conf());
				if (!fs.exists(file)) {
					System.err.println("Task " + taskid + " committed without a final output -> " + file);
					throw new IOException("No final output file!");
				}
			}

			this.committed.add(taskid);
			this.requests.notifyAll();
		}
	}
	
	@Override
	public BufferRequest getRequest(TaskAttemptID taskid) throws IOException {
		synchronized (requests) {
			if (this.requests.containsKey(taskid)) {
				for (BufferRequest request : this.requests.get(taskid)) {
					if (request.delivered == false) {
						request.delivered = true;
						return request;
					}
				}
			}
			return null;
		}
	}

	@Override
	public void request(BufferRequest request) throws IOException {
		synchronized (requests) {
			if (request.source().equals(hostname)) {
				register(request); // Request is local!
			}
			else {
				Socket socket        = null;
				DataOutputStream out = null;
				try {
					InetSocketAddress controlSource = NetUtils.createSocketAddr(request.source() + ":" + this.controlPort);
					socket = new Socket();
					for (int i = 0; i < 10; i++) {
						try { socket.connect(controlSource);
						} catch (ConnectException e) {
							if (i < 10) {
								try { Thread.sleep(1000);
								} catch (InterruptedException e1) { }
								continue;
							}
							throw e;
						}
						break;
					}
					out = new DataOutputStream(socket.getOutputStream());
					request.write(out);
				}
				finally {
					if (out != null) {
						out.flush();
						out.close();
					}
					else if (socket != null) {
						socket.close();
					}
				}
			}
		}
	}

	@Override
	public void run() {
		try {
			initialize();
		} catch (IOException e) {
			e.printStackTrace();
		}

		while (true) {
			SocketChannel channel = null;
			try {
				channel = this.channel.accept();
				BufferRequest request = new BufferRequest();
				DataInputStream in = new DataInputStream(channel.socket().getInputStream());
				request.readFields(in);
				
				register(request);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			finally {
				try {
					if (channel != null) channel.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	private void register(BufferRequest request) throws IOException {
		synchronized(requests) {
			if (!this.requests.containsKey(request.taskid())) {
				this.requests.put(request.taskid(), new TreeSet<BufferRequest>());
			}
			this.requests.get(request.taskid()).add(request);
			this.requests.notifyAll();
		}

	}
	
	@Override
	public long getProtocolVersion(String protocol, long clientVersion)
	throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}



}
