import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class RTPServer {
	private static final int MAXBUFFER = 1000;
	private DatagramSocket recvSocket;
	private DatagramSocket sendSocket;

	DatagramPacket recvPacket;
	DatagramPacket sendPacket;

	private int dstPort;
	private InetAddress dstAddress;
	private int srcPort;
	private int threshold;
	private int sequenceNumber;
	private String filename;

	private ArrayList<RTPPacket> packetSendBuffer;
	private ArrayList<RTPPacket> packetReceivedBuffer;

	private int wSize;

	private int state = 0; //0==closed, 1==listen, 2==listen wait for ack, 3==established

	public RTPServer(int srcPort, int wSize) throws SocketException {
		this.srcPort = srcPort;
		this.wSize = wSize;

		filename = "bufferFile";
		packetSendBuffer = new ArrayList<RTPPacket>();
		packetReceivedBuffer = new ArrayList<RTPPacket>();
		recvSocket = new DatagramSocket(srcPort);
		sendSocket = new DatagramSocket(srcPort);
	}

	public void sendRTPPacket(byte[] data) throws IOException {
		RTPPacket sendPacket;
		RTPHeader sendHeader = new RTPHeader(srcPort, dstPort, sequenceNumber);

		sendPacket = new RTPPacket(sendHeader, data);
		sendPacket.updateChecksum();

		data = sendPacket.getPacketByteArray();

		sendSocket.send(new DatagramPacket(data, data.length, dstAddress, dstPort));
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
		
		int packetChecksum = packet.getHeader().getChecksum();
		int calculatedChecksum = packet.calculateChecksum();
		return (packetChecksum == calculatedChecksum);
	}

	public void listen() {
		while (true) {
			DatagramPacket packet = new DatagramPacket(new byte[MAXBUFFER], MAXBUFFER);
			try {
				recvSocket.receive(packet);
				byte[] receivedData = new byte[packet.getLength()];
				receivedData = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());

				RTPPacket receivedRTPPacket = new RTPPacket(receivedData);
				RTPHeader receivedHeader = receivedRTPPacket.getHeader();

				//Validate Checksum
				if (receivedHeader.getChecksum() == receivedRTPPacket.calculateChecksum()) {
					//Handshake
					if (state == 1 && receivedHeader.isSYN()) {
						dstPort = packet.getPort();
						dstAddress = packet.getAddress();
						RTPHeader responseHeader = new RTPHeader(srcPort, recvPacket.getPort(), 0); //0 for now
						responseHeader.setSYN(true);
						responseHeader.setACK(true);
						RTPPacket responsePacket = new RTPPacket(responseHeader, null);
						responsePacket.updateChecksum();

						byte[] packetBytes = responsePacket.getPacketByteArray();
						sendPacket = new DatagramPacket(packetBytes, packetBytes.length, recvPacket.getAddress(), recvPacket.getPort());
						sendSocket.send(sendPacket);
						state = 2;
					}
					//3rd part of handshake
					if (state == 2 && receivedHeader.isACK()) {
						state = 3;
					}

					//put incoming packets into receive buffer
					if (state == 3) {
						packetReceivedBuffer.add(receivedRTPPacket);
						RTPHeader responseHeader = new RTPHeader(srcPort, recvPacket.getPort(), 0); //0 for now
						responseHeader.setACK(true);
						responseHeader.setseqNum(receivedHeader.getseqNum());
						RTPPacket responsePacket = new RTPPacket(responseHeader, null);
						responsePacket.updateChecksum();

						byte[] packetBytes = responsePacket.getPacketByteArray();
						sendPacket = new DatagramPacket(packetBytes, packetBytes.length, recvPacket.getAddress(), recvPacket.getPort());
						sendSocket.send(sendPacket);
					}

					//fin indicates end of file
					if (state == 3 && receivedHeader.isFIN()) {
						ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
						for (RTPPacket p : packetReceivedBuffer) {
							outputStream.write(p.getData());
						}
						createFileFromByteArray(filename, outputStream.toByteArray());
						packetReceivedBuffer.clear();	
					}
				}


				// System.out.println(packet.getAddress() + " " + packet.getPort() + ": " + new String(packet.getData()));
				// String packetText = new String(packet.getData(), "UTF-8").toUpperCase().trim();
				// System.out.println(packetText);
				// RTPPacket responsePacket = new RTPPacket(srcPort, packet.getPort(), packetText.getBytes());
				// byte[] responseArray = responsePacket.getPacketByteArray();
				// socket.send(new DatagramPacket(responseArray, responseArray.length));
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

	//TODO: IMPLEMENT GO BACK N
	//need more states? probably
	public void sendFilePackets(String fName) {
		File file = new File(fName);
		
		byte[] byteArray = new byte[(int) file.length()];
		
		try {
			FileInputStream fileInputStream = new FileInputStream(file);
			fileInputStream.read(byteArray);

			//make uppercase or some shit
			
			byte[] packetBytes;
			
			if (byteArray.length < MAXBUFFER) {
				packetBytes = new byte[byteArray.length];
			} else {
				packetBytes = new byte[MAXBUFFER];
			}
			
			int packetNumber = 0;
			RTPHeader rtpHeader = new RTPHeader(srcPort, dstPort, packetNumber);
			RTPPacket rtpPacket;
			
			for (int i = 0; i < byteArray.length; i++) {
				packetBytes[i] = byteArray[(packetNumber * (MAXBUFFER-1)) + i];
				
				if (i % MAXBUFFER == 0 && !(i < MAXBUFFER)) {
					rtpHeader.setseqNum(sequenceNumber++);
					rtpPacket = new RTPPacket(rtpHeader, packetBytes);
					rtpPacket.updateChecksum();

					packetNumber += 1;
					sendRTPPacket(rtpPacket.getPacketByteArray());
				}
			}
			
			rtpHeader.setFIN(true);
			rtpHeader.setseqNum(sequenceNumber++);
			rtpPacket = new RTPPacket(rtpHeader, packetBytes);
			rtpPacket.updateChecksum();
			sendRTPPacket(rtpPacket.getPacketByteArray());
			
			
			fileInputStream.close();
		} catch (FileNotFoundException e) {
			System.out.println("File was not found.");		
		} catch (IOException e) {
			System.out.println("Error reading file.");
			
		}
	}

	public static void main(String args[]) throws Exception {
		RTPServer server = new RTPServer(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
		server.recvPacket = new DatagramPacket(new byte[MAXBUFFER], MAXBUFFER);
		server.state = 1;
		server.listen();
	}
}