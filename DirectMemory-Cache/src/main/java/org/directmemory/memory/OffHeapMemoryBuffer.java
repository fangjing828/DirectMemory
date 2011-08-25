package org.directmemory.memory;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import org.josql.Query;
import org.josql.QueryExecutionException;
import org.josql.QueryParseException;
import org.josql.QueryResults;

import ch.qos.logback.classic.Logger;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class OffHeapMemoryBuffer {
	protected ByteBuffer buffer;
	public List<Pointer> pointers = new ArrayList<Pointer>();
//	public List<Pointer> pointers = new CopyOnWriteArrayList<Pointer>();
	AtomicInteger used = new AtomicInteger();
	public int bufferNumber;
	
	
	public int used() {
		return used.get();
	}
	
	public int capacity(){
		return buffer.capacity();
	}
	
	public static OffHeapMemoryBuffer createNew(int capacity, int bufferNumber) {
		return new OffHeapMemoryBuffer(ByteBuffer.allocateDirect(capacity), bufferNumber); 
	}
	
	public static OffHeapMemoryBuffer createNew(int capacity) {
		return new OffHeapMemoryBuffer(ByteBuffer.allocateDirect(capacity), -1); 
	}
	
	private OffHeapMemoryBuffer(ByteBuffer buffer, int bufferNumber) {
		this.buffer = buffer;
		this.bufferNumber = bufferNumber;
		createAndAddFirstPointer();
	}

	private Pointer createAndAddFirstPointer() {
		Pointer first = new Pointer();
		first.bufferNumber = bufferNumber;
		first.start = 0;
		first.free = true;
		first.end = buffer.capacity()-1;
		pointers.add(first);
		return first;
	}
	
	public Pointer slice(Pointer existing, int capacity) {
		Pointer fresh = new Pointer();
		fresh.bufferNumber = existing.bufferNumber;
		fresh.start = existing.start;
		fresh.end = fresh.start+capacity;
		fresh.free = true;
		existing.start+=capacity+1;
		return fresh;
	}

	
	public Pointer firstMatch(int capacity) {
		for (Pointer ptr : pointers) {
			if (ptr.free && ptr.end >= capacity) {
				return ptr;
			}
		}
		return null;
	}
	
	public Pointer store(byte[] payload) {
		return store(payload, -1);
	}
	
	public byte[] retrieve(Pointer pointer) {
		if (!pointer.expired()) {
			pointer.lastHit = System.currentTimeMillis();
			pointer.hits++;
			final ByteBuffer buf = buffer.slice();
			buf.position(pointer.start);
			buf.limit(pointer.end+pointer.start);
			final byte[] swp = new byte[pointer.end-pointer.start];
			buf.get(swp);
			return swp;
		} else {
			free(pointer);
			return null;
		}
	}
	
	
	public void free(Pointer pointer2free) {
		pointer2free.free = true;
		pointer2free.created = 0;
		pointer2free.lastHit = 0;
		pointer2free.hits = 0;
		pointer2free.expiresIn = 0;
		used.addAndGet(-( pointer2free.end-pointer2free.start));
		pointers.add(pointer2free);
	}
	
	public void clear() {
		pointers.clear();
		createAndAddFirstPointer();
		buffer.clear();
		used.set(0);
	}

	public Pointer store(byte[] payload, Date expires) {
		return store(payload, 0, expires.getTime());
	}
	
	public Pointer store(byte[] payload, long expiresIn) {
		return store(payload, expiresIn, 0);
	}
	
	private synchronized Pointer store(byte[] payload, long expiresIn, long expires) {
		Pointer goodOne = firstMatch(payload.length);
		
		if (goodOne == null ) {
			throw new NullPointerException("did not find a suitable buffer");
		}
		
		Pointer fresh = slice(goodOne, payload.length);


		fresh.created = System.currentTimeMillis();
		if (expiresIn > 0) {
			fresh.expiresIn = expiresIn;
			fresh.expires = 0;
		} else if (expires > 0) {
			fresh.expiresIn = 0;
			fresh.expires = expires;
		}
		
		fresh.free = false;
		used.addAndGet(payload.length);
		ByteBuffer buf = buffer.slice();
		buf.position(fresh.start);
		try {
			buf.put(payload);
		} catch (BufferOverflowException e) {
			// RpG not convincing - let's fix it later
			goodOne.start = fresh.start;
			goodOne.end = buffer.limit();
			return null;
		}
		pointers.add(fresh);
		return fresh;
	}
	
	private QueryResults select(String whereClause) throws QueryParseException, QueryExecutionException {
		Query q = new Query ();
		q.parse ("SELECT * FROM " + Pointer.class.getCanonicalName() + "  WHERE " + whereClause);
		QueryResults qr = q.execute (pointers);
		return qr;
	}
	
	@SuppressWarnings("unchecked")
	private List<Pointer> filter(final String whereClause) {
		try {
			return select(whereClause).getResults();
		} catch (QueryParseException e) {
			e.printStackTrace();
		} catch (QueryExecutionException e) {
			e.printStackTrace();
		}
		return (List<Pointer>) new ArrayList<Pointer>();
	}
	
	private void free(List<Pointer> pointers) {
		for (Pointer expired : pointers) {
			free(expired);
		}
	}
	
	public void disposeExpiredRelative() {
		free(filter("free=false and expiresIn > 0 and (expiresIn+created) <= " + System.currentTimeMillis()));
	}
	
	public void disposeExpiredAbsolute() {
		free(filter("free=false and expires > 0 and (expires) <= " + System.currentTimeMillis()));
	}
	
	public static long crc32(byte[] payload) {
		final Checksum checksum = new CRC32();
		checksum.update(payload,0,payload.length);
		return checksum.getValue();
	}

	public Pointer update(Pointer pointer, byte[] payload) {
		free(pointer);
		return store(payload);
	}
	
}
