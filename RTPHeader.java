import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;

	public class RTPHeader {
		private int srcPort;
		private int dstPort;
		private int seqNum;
		private int windowSizeOffset;
		private int checksum;
		
		public int getSrcPort() {
			return srcPort;
		}

		public void setSrcPort(int srcPort) {
			this.srcPort = srcPort;
		}

		public int getDstPort() {
			return dstPort;
		}

		public void setDstPort(int dstPort) {
			this.dstPort = dstPort;
		}

		private boolean ACK;
		private boolean NACK;
		private boolean SYN;
		private boolean FIN;
		
		private int timestamp;
		
		public RTPHeader() {
			this.windowSizeOffset = 0;
			this.checksum = 0;
			this.ACK = false;
			this.NACK = false;
			this.SYN = false;
			this.FIN = false;
			this.timestamp = 0;
		}
		
		public RTPHeader(int srcPort, int dstPort, int seqNum) {
			this();
			this.srcPort = srcPort;
			this.dstPort = dstPort;
			this.seqNum = seqNum;
		}
		
		public void setseqNum(int seqNum) { this.seqNum = seqNum; }
		
		public int getseqNum() { return seqNum; }
		
		public void setWindowSizeOffset(int windowSizeOffset) { this.windowSizeOffset = windowSizeOffset; }
		
		public int getWindowSizeOffset() { return windowSizeOffset; }
		
		public boolean isACK() { return ACK; }

		public void setACK(boolean ACK) { this.ACK = ACK; }

		public boolean isNACK() { return NACK; }

		public void setNACK(boolean NACK) { this.NACK = NACK; }

		public boolean isSYN() { return SYN; }

		public void setSYN(boolean SYN) { this.SYN = SYN; }

		public boolean isFIN() { return FIN; }

		public void setFIN(boolean FIN) { this.FIN = FIN; }

		public void setChecksum(int checksum) { this.checksum = checksum; }
		
		public int getChecksum() { return checksum; }
		
		public byte[] getHeaderByteArray() {
			byte[] headerByteArray;

			ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES * 7);
			byteBuffer.order(ByteOrder.BIG_ENDIAN);

			byteBuffer.putInt(srcPort);
			byteBuffer.putInt(dstPort);
			byteBuffer.putInt(seqNum);
			byteBuffer.putInt(windowSizeOffset);
			byteBuffer.putInt(checksum);

			int ackByte = (ACK ? 1 : 0) << 31;
			int nackByte = (NACK ? 1 : 0) << 30;
			int synByte = (SYN ? 1 : 0) << 29;
			int finByte = (FIN ? 1 : 0) << 28;
			

			int flags = ackByte | nackByte | synByte | finByte;
			byteBuffer.putInt(flags);
			byteBuffer.putInt(timestamp);
			headerByteArray = byteBuffer.array();
			return headerByteArray;
		}
		
		public RTPHeader(byte[] headerByteArray) {
			ByteBuffer byteBuffer = ByteBuffer.wrap(headerByteArray);
			IntBuffer intBuffer = byteBuffer.asIntBuffer();
			this.srcPort = intBuffer.get(0);
			this.dstPort = intBuffer.get(1);
			this.seqNum = intBuffer.get(2);
			this.windowSizeOffset = intBuffer.get(3);
			this.checksum = intBuffer.get(4);
			int flags = intBuffer.get(5);
			this.timestamp = intBuffer.get(6);
			
			int ackInt = (flags >>> 31) & 0x1;
			int nackInt = (flags >>> 30) & 0x1;
			int synInt = (flags >>> 29) & 0x1;
			int finInt = (flags >>> 28) & 0x1;
			
			this.ACK = (ackInt != 0);
			this.NACK = (nackInt != 0);
			this.SYN = (synInt != 0);
			this.FIN = (finInt != 0);		}
		
		public int getTimestamp() {
			return timestamp;
		}
		
		public void setTimestamp(int timestamp) {
			this.timestamp = timestamp;
		}
	}
