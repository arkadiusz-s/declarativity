package org.apache.hadoop.fs.bfs;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.apache.hadoop.fs.FSInputStream;

import bfs.BFSChunkInfo;
import bfs.BFSClient;
import bfs.BFSFileInfo;
import bfs.Conf;

public class BFSInputStream extends FSInputStream {
	private BFSClient bfs;
	private String path;
	private List<BFSChunkInfo> chunkList;
	private boolean isClosed;
	private boolean atEOF;

	// Byte-wise offset into the logical file contents
	private long position;

	// Index into "chunkList" identifying the current chunk we're positioned at.
	// These fields are updated by updatePosition().
	private int currentChunkIdx;
	private int currentChunkOffset;
	private BFSChunkInfo currentChunk;
	private Set<String> chunkLocations;

	public BFSInputStream(String path, BFSClient bfs) throws IOException {
		this.bfs = bfs;
		this.path = path;
		this.isClosed = false;
		this.atEOF = false;
		this.chunkList = bfs.getChunkList(path);
		updatePosition(0);
	}

	@Override
	public long getPos() throws IOException {
		return this.position;
	}

	@Override
	public void seek(long newPos) throws IOException {
		updatePosition(newPos);
	}

	private void updatePosition(long newPos) throws IOException {
		if (newPos < 0)
			throw new IOException("cannot seek to negative position");

		int chunkNum = (int) (newPos / Conf.getChunkSize());
		int chunkOffset = (int) (newPos % Conf.getChunkSize());

		if (chunkNum > chunkList.size())
			throw new IOException("cannot seek past end of file");

		if (chunkList.isEmpty()) {
			if (chunkOffset > 0)
				throw new IOException("cannot seek past end of file");

			this.atEOF = true;
		} else {
			if (chunkNum != this.currentChunkIdx || this.chunkLocations == null) {
				this.currentChunk = this.chunkList.get(chunkNum);
				this.chunkLocations = bfs.getChunkLocations(this.currentChunk.getId());
			}

			if (chunkOffset > this.currentChunk.getLength())
				throw new IOException("cannot seek past end of file");

			if (chunkOffset == this.currentChunk.getLength() &&
				this.currentChunkIdx == this.chunkList.size())
				this.atEOF = true;
			else
				this.atEOF = false;
		}

		this.currentChunkIdx = (int) chunkNum;
		this.currentChunkOffset = (int) chunkOffset;
		this.position = newPos;
	}

	@Override
	public boolean seekToNewSource(long targetPos) throws IOException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int read() throws IOException {
		if (this.isClosed)
			throw new IOException("cannot read from closed file");
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int read(byte[] buf, int offset, int len) throws IOException {
		// TODO Auto-generated method stub
		return -1;
	}

	@Override
	public void close() throws IOException {
		if (this.isClosed)
			return;

		this.isClosed = true;
	}

	/*
	 * We don't support marks, for now.
	 */
	@Override
	public boolean markSupported() {
		return false;
	}

	@Override
	public void mark(int readLimit) {
		// Do nothing
	}

	@Override
	public void reset() throws IOException {
		throw new IOException("Mark not supported");
	}
}
