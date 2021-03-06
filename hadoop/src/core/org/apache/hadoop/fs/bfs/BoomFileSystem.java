package org.apache.hadoop.fs.bfs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.BlockLocation;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.util.Progressable;

import bfs.BFSChunkInfo;
import bfs.Conf;

public class BoomFileSystem extends FileSystem {
	private BFSClientProtocol bfs;
	private URI uri;
	private Path workingDir = new Path("/");

	@Override
	public void initialize(URI uri, Configuration conf) throws IOException {
		setConf(conf);

		this.bfs = BFSClientWrapper.getInstance(conf);
		this.uri = URI.create(uri.getScheme() + "://" + uri.getAuthority());
	}

	@Override
	public FSDataOutputStream append(Path path, int bufferSize,
			Progressable progress) throws IOException {
		System.out.println("BFS#append() called for " + path);
		BFSOutputStream bos = new BFSOutputStream(getPathName(path), this.bfs);
		return new FSDataOutputStream(bos, statistics);
	}

	@Override
	public FSDataOutputStream create(Path path, FsPermission permission,
			boolean overwrite, int bufferSize, short replication,
			long blockSize, Progressable progress) throws IOException {
		System.out.println("BFS#create() called for " + path);

		/* XXX: not very efficient/clean, and not atomic either */
		if (overwrite)
			delete(path, false);

		if (this.bfs.createFile(getPathName(path)) == false)
			throw new IOException("failed to create file " + path);

		return append(path, bufferSize, progress);
	}

	@Override
	public FSDataInputStream open(Path path, int bufferSize) throws IOException {
		System.out.println("BFS#open() called for " + path);
		return new FSDataInputStream(new BFSInputStream(getPathName(path), this.bfs));
	}

	@Override
	public boolean delete(Path path) throws IOException {
		return delete(path, true);
	}

	/*
	 * XXX: we currently ignore "recursive"; we should instead raise an error if
	 * recursive is false and the target path is a non-empty directory.
	 */
	@Override
	public boolean delete(Path path, boolean recursive) throws IOException {
		System.out.println("BFS#delete() called for " + path);
		return this.bfs.delete(getPathName(path));
	}

	@Override
	public FileStatus getFileStatus(Path path) throws IOException {
		System.out.println("BFS#getFileStatus() called for " + path);
		FileStatus result = this.bfs.getFileStatus(getPathName(path));
		if (result == null)
			throw new FileNotFoundException("File not found: " + path);

		return result;
	}

	@Override
	public FileStatus[] listStatus(Path path) throws IOException {
		System.out.println("BFS#listStatus() called for " + path);
		return this.bfs.getDirListing(getPathName(path));
	}

	@Override
	public boolean mkdirs(Path path, FsPermission permission) throws IOException {
		System.out.println("BFS#mkdirs() called for " + path);
		// This command should create all the directories in the given path that
		// don't already exist (i.e. it should function like "mkdir -p"). Since
		// BFS requires that we create directories one at a time, we start at
		// the root path and iterate
		path = makeAbsolute(path);
		List<Path> pathHierarchy = new ArrayList<Path>();
		for (Path p = path; p != null; p = p.getParent()) {
			pathHierarchy.add(p);
		}
		Collections.reverse(pathHierarchy);

		for (Path p : pathHierarchy) {
			// We assume that the root directory ("/") always exists
			if (p.getParent() == null)
				continue;

			this.bfs.createDir(getPathName(p));
		}

		return true;
	}

	@Override
	public boolean rename(Path src, Path dst) throws IOException {
		System.out.println("BFS#rename() called for " + src + " => " + dst);
		if (getPathName(src).equals(getPathName(dst)))
			return true;

		return this.bfs.rename(getPathName(src), getPathName(dst));
	}

	@Override
	public URI getUri() {
		return this.uri;
	}

	@Override
	public Path getWorkingDirectory() {
		return this.workingDir;
	}

	@Override
	public void setWorkingDirectory(Path new_dir) {
		this.workingDir = makeAbsolute(new_dir);
	}

	@Override
	public void close() throws IOException {
		System.out.println("BoomFileSystem#close() called");
		super.close();
		this.bfs.shutdown();
	}

	@Override
	public BlockLocation[] getFileBlockLocations(FileStatus file, long start, long len)
	throws IOException {
		String pathName = getPathName(file.getPath());
		BFSChunkInfo[] chunkList = bfs.getChunkList(pathName);

		int chunkIdx = 0;
		long chunkOffset = 0;
		if (start > 0) {
			for (chunkIdx = 0; chunkIdx < chunkList.length; chunkIdx++) {
				int chunkLen = chunkList[chunkIdx].getLength();

				if (start < chunkLen)
					break;

				start -= chunkLen;
				chunkOffset += chunkLen;
			}

			// Is the requested "start" pos beyond EOF?
			if (chunkIdx == chunkList.length)
				return null;
		}

		List<BlockLocation> blockLocs = new LinkedList<BlockLocation>();
		while (true) {
			BFSChunkInfo chunk = chunkList[chunkIdx];

			// Use BFSChunkInfo to compute BlockLocation
			String[] chunkLocs = this.bfs.getChunkLocations(pathName, chunk.getId());
			String[] chunkHosts = new String[chunkLocs.length];
			String[] chunkEndpoints = new String[chunkLocs.length];
			for (int i = 0; i < chunkLocs.length; i++) {
				String[] parts = chunkLocs[i].split(":");
				String host = parts[1];
		        int controlPort = Integer.parseInt(parts[2]);
		        int dataPort = Conf.findDataNodeDataPort(host, controlPort);

		        chunkHosts[i] = host;
		        chunkEndpoints[i] = host + ":" + dataPort;
			}

			BlockLocation loc = new BlockLocation(chunkEndpoints, chunkHosts,
					                              chunkOffset, chunk.getLength());
			blockLocs.add(loc);

			chunkOffset += chunk.getLength();
			int chunkLen = chunk.getLength();
			if (start > 0) {
				chunkLen -= start;
				start = 0;
			}
			len -= Math.min(len, chunkLen);
			if (len == 0)
				break;
            else
                chunkIdx++;
		}

		BlockLocation[] result = new BlockLocation[blockLocs.size()];
		return blockLocs.toArray(result);
	}

	private String getPathName(Path path) {
		checkPath(path);
		String result = makeAbsolute(path).toUri().getPath();
		return result;
	}

	private Path makeAbsolute(Path path) {
		if (path.isAbsolute())
			return path;

		return new Path(this.workingDir, path);
	}
}
