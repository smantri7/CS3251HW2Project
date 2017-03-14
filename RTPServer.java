import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;

public class RTPServer {
	private static final int MAXBUFFER = 1000;
	private DatagramSocket socket;

	DatagramPacket recvPacket;
	DatagramPacket sendPacket;

	private int srcPort;
	private int threshold;
	private int sequenceNumber;
	private String filename;

	private ArrayList<RTPPacket> packetSendBuffer;
	private ArrayList<RTPPacket> packetReceivedBuffer;

	private int wSize;

	private int state = 0; //0==closed, 1==listen, 2==established

	public RTPServer(int srcPort, int wSize) throws SocketException {
		this.srcPort = srcPort;
		this.wSize = wSize;

		packetSendBuffer = new ArrayList<RTPPacket>();
		packetReceivedBuffer = new ArrayList<RTPPacket>();
		socket = new DatagramSocket(srcPort);
	}

	public void sendRTPPacket(byte[] data, InetAddress dstAddress, int dstPort) throws IOException {
		RTPPacket sendPacket;
		RTPHeader sendHeader = new RTPHeader(srcPort, dstPort, sequenceNumber);

		sendPacket = new RTPPacket(sendHeader, data);
		sendPacket.updateChecksum();

		data = sendPacket.getPacketByteArray();

		socket.send(new DatagramPacket(data, data.length, dstAddress, dstPort));
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

	public void handshake() {
		while (state == 1) {
			recvPacket = new DatagramPacket(new byte[MAXBUFFER], MAXBUFFER);
			try {
				socket.receive(recvPacket);
				byte[] receivedData = new byte[recvPacket.getLength()];
				receivedData = Arrays.copyOfRange(recvPacket.getData(), 0, recvPacket.getLength());
				RTPPacket receivedRTPPacket = new RTPPacket(receivedData);
				RTPHeader receivedHeader = receivedRTPPacket.getHeader();

				if (receivedHeader.isSYN()) {
					RTPHeader responseHeader = new RTPHeader(srcPort, recvPacket.getPort(), 0); //0 for now
					responseHeader.setSYN(true);
					responseHeader.setACK(true);
					RTPPacket responsePacket = new RTPPacket(responseHeader, null);
					responsePacket.updateChecksum();

					byte[] packetBytes = responsePacket.getPacketByteArray();
					sendPacket = new DatagramPacket(packetBytes, packetBytes.length, recvPacket.getAddress(), recvPacket.getPort());
					socket.send(sendPacket);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void listen() {
		while (true) {
			DatagramPacket packet = new DatagramPacket(new byte[MAXBUFFER], MAXBUFFER);
			try {
				socket.receive(packet);
				System.out.println(packet.getAddress() + " " + packet.getPort() + ": " + new String(packet.getData()));
				String packetText = new String(packet.getData(), "UTF-8").toUpperCase().trim();
				System.out.println(packetText);
				RTPPacket responsePacket = new RTPPacket(srcPort, packet.getPort(), packetText.getBytes());
				byte[] responseArray = responsePacket.getPacketByteArray();
				socket.send(new DatagramPacket(responseArray, responseArray.length));
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

	//LISTEN FUNCTION FSM HERE

	public static void main(String args[]) throws Exception {
		RTPServer server = new RTPServer(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
		server.recvPacket = new DatagramPacket(new byte[MAXBUFFER], MAXBUFFER);
		server.state = 1;
		server.listen();
	}
}