package com.hp.oo.engine.queue.entities;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.builder.EqualsBuilder;

import java.io.Serializable;

/**
 * User: Amit Levin
 * Date: 10/09/12
 * Time: 09:39
 */
public class Payload implements Cloneable, Serializable {

    private static final long serialVersionUID = 1198403948027561284L;

	private boolean compressed;
	private boolean encrypt;
	private byte[] data;

	public Payload() {
		compressed = false;
		encrypt = false;
		data = null;
	}

	public Payload(boolean compressed, boolean encrypt, byte[] data) {
		this.compressed = compressed;
		this.encrypt = encrypt;
		this.data = data;
	}

	public boolean isCompressed() {
		return compressed;
	}

	public boolean isEncrypt() {
		return encrypt;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Payload)) return false;

		Payload that = (Payload)obj;

		return new EqualsBuilder()
				.append(this.compressed, that.compressed)
				.append(this.encrypt, that.encrypt)
				.append(this.data, that.data)
				.isEquals();
	}

	@SuppressWarnings("CloneDoesntDeclareCloneNotSupportedException")
	@Override
	public Object clone() {
		try {
			Payload cloned = (Payload) super.clone();
			cloned.data = ArrayUtils.clone(data);
			return cloned;
		} catch (CloneNotSupportedException e) {
			System.out.println(e);
			return null;
		}
	}
}
