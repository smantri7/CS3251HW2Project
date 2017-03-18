import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Checksum;


public class RTPPacket {

	private RTPHeader header;

	private byte[] data;

	public RTPPacket(int sourcePort, int destinationPort, byte[] data) {
		this.setHeader(new RTPHeader(sourcePort, destinationPort, 0));
		this.setData(data);
	}

	public RTPPacket(int sourcePort, int destinationPort) {
		this.setHeader(new RTPHeader(sourcePort, destinationPort, 0));
		this.setData(data);
	}

	public RTPPacket(RTPHeader header, byte[] data) {
		this.setHeader(header);
		this.setData(data);
	}

	public RTPPacket(byte[] packetByteArray) {
		byte[] headerBytes = Arrays.copyOfRange(packetByteArray, 0, 28);
		this.setHeader(new RTPHeader(headerBytes));

		if (packetByteArray.length > 28) {
			byte[] dataBytes = Arrays.copyOfRange(packetByteArray, 28, packetByteArray.length);
			this.setData(dataBytes);
		}
	}
	
	public byte[] getPacketByteArray() {
		byte[] packetByteArray;
		byte[] headerByteArray;
		
		headerByteArray = header.getHeaderByteArray();
		
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try {
			//outputStream.write(headerByteArray);
			if (data != null) {
				outputStream.write(data);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		packetByteArray = outputStream.toByteArray();
		return packetByteArray;
	}

	public byte[] getEntireByteArray() {
		byte[] packetByteArray;
		byte[] headerByteArray;
		
		headerByteArray = header.getHeaderByteArray();
		
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try {
			outputStream.write(headerByteArray);
			if (data != null) {
				outputStream.write(data);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		packetByteArray = outputStream.toByteArray();
		return packetByteArray;
	}
	
	public RTPHeader getHeader() { return header; }

	public void setHeader(RTPHeader header) { this.header = header; }

	public byte[] getData() { return data; }

	public void setData(byte[] data) { this.data = data; }

	public void updateChecksum() { header.setChecksum(calculateChecksum()); }
	
	public int calculateChecksum() {
		int checksumValue;
		
		CRC32 checksum = new CRC32();
		
		byte[] packetByteArray = getPacketByteArray();

		//packetByteArray[16] = 0x00;
		//packetByteArray[17] = 0x00;
		//packetByteArray[18] = 0x00;
		//packetByteArray[19] = 0x00;

		checksum.update(packetByteArray);
		checksumValue = (int) checksum.getValue();
		return checksumValue;
	}
}
