public class RTPServer {
	private final int MAXBUFFER = 1000;
	private DatagramSocket socket;

	DatagramPacket recvPacket;
	DatagramPacket sendPacket;

	private int srcPort;
	private int threshold;
	private int sequenceNumber;
	private String filename;

	private ArrayList<RTPPacket> packetSendBuffer;
	private ArrayList<RTPPacket> packetReceivedBuffer;

	private int state = 0; //0==closed, 1==listen, 2==established

	public RTPServer(int srcPort, int wSize) {
		this.srcPort = srcPort;
		this.wSize = wSize

		packetSendBuffer = new ArrayList<RTPPacket>();
		packetReceivedBuffer = new ArrayList<RTPPacket>();

		try {
			socket = new DatagramSocket(srcPort);
		} catch (SocetException e) {
			e.printStackTrace();
		}
	}

	public void sendRTPPacket(byte[] data, InetAddress dstAddress, int dstPort) throws IOException {
		RTPPacket sendPacket;
		RTPHeader sendHeader = new RTPHeader(srcPort, dstPort, sequenceNumber);

		sendPacket = new RTPPacket(sendHeader, data);
		sendPacket.updateChecksum();

		data = sendPacket.getPacketByteArray();

		socket.send(new DatagramPacket(data, data.length, dstAddress, dstPort);
	}

	public void createFileFromByteArray(String filename, byte[] fileByteArray) {
		try {
			FileOutputStream fileOutputStream = new FileOutputStream(filename);
			fileOutputStream.write(fileByteArray);
		} catch (FileNotFoundException e) {
			System.out.println("File was not found.");
		} catch (IOException e) {
			System.out.println("Error writing file.");
		}
	}

	public static boolean validateChecksum(RTPPacket packet) {
		
		// Obtains the checksum from the passed in packet.
		int packetChecksum = packet.getHeader().getChecksum();
		
		// Recalculates a checksum for the packet for comparison.
		int calculatedChecksum = packet.calculateChecksum();

		// Returns the result of comparing the two checksums.
		return (packetChecksum == calculatedChecksum);
	}

	//LISTEN FUNCTION FSM HERE

	public static void main(String args[]) throws Exception {
		RTPServer server = new RTPServer(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
		server.recvPacket = new DatagramPacket(new byte[MAXBUFFER], MAXBUFFER);
		server.state = 1;
	}
}