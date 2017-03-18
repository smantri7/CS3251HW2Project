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
import java.util.*;

public class RTPServer  {
	private static final int MAXBUFFER = 1000;
	private static final int MAXRECV = 1030;
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

	private int packetNum = 0;

	private int state = 0; //0==closed, 1==listen, 2==listen wait for ack, 3==established

	public RTPServer(int srcPort, int wSize) throws SocketException {
		this.srcPort = srcPort;
		this.wSize = wSize;

		filename = "bufferFile";
		packetSendBuffer = new ArrayList<RTPPacket>();
		packetReceivedBuffer = new ArrayList<RTPPacket>();
		recvSocket = new DatagramSocket(srcPort);
		sendSocket = new DatagramSocket(srcPort + 1);
	}

	public void sendRTPPacket(byte[] data) throws IOException {
		RTPPacket sendPacket;
		RTPHeader sendHeader = new RTPHeader(srcPort, dstPort, sequenceNumber);

		sendPacket = new RTPPacket(sendHeader, data);
		sendPacket.updateChecksum();

		data = sendPacket.getEntireByteArray();

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
			DatagramPacket packet = new DatagramPacket(new byte[MAXRECV], MAXRECV);
			packetNum += 1;
			try {
				recvSocket.receive(packet);
				System.out.println("Received a packet!");
				if(packetNum == 1413) {
					System.out.println(new String(packetReceivedBuffer.get(5).getData()));
				}
				byte[] receivedData = new byte[packet.getLength()];
				receivedData = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());

				RTPPacket receivedRTPPacket = new RTPPacket(receivedData);
				RTPHeader receivedHeader = receivedRTPPacket.getHeader();
				//System.out.println(receivedHeader.isSYN());
				//Validate Checksum
				//System.out.println(receivedHeader.getChecksum());
				//System.out.println(receivedRTPPacket.calculateChecksum());
				//put incoming packets into receive buffer
				if (state == 3) {
					packetReceivedBuffer.add(receivedRTPPacket);
					RTPHeader responseHeader = new RTPHeader(srcPort, recvPacket.getPort(), 0); //0 for now
					responseHeader.setACK(true);
					responseHeader.setseqNum(receivedHeader.getseqNum() + 1);
					RTPPacket responsePacket = new RTPPacket(responseHeader, null);
					responsePacket.updateChecksum();

					byte[] packetBytes = responsePacket.getEntireByteArray();
					sendPacket = new DatagramPacket(packetBytes, packetBytes.length, dstAddress, 2001);
					System.out.println("Sent ACK packet in state 3");
					System.out.printf("Seq number is: %d",responsePacket.getHeader().getseqNum());
					sendSocket.send(sendPacket);
				}
				if (receivedHeader.getChecksum() == receivedRTPPacket.calculateChecksum()) {
					//Handshake
					if (state == 1 && receivedHeader.isSYN()) {
						dstPort = packet.getPort();
						dstAddress = packet.getAddress();
						RTPHeader responseHeader = new RTPHeader(srcPort, recvPacket.getPort(), 0); //0 for now
						responseHeader.setSYN(true);
						responseHeader.setACK(true);
						responseHeader.setseqNum(receivedHeader.getseqNum() + 1);
						RTPPacket responsePacket = new RTPPacket(responseHeader, null);
						responsePacket.updateChecksum();

						byte[] packetBytes = responsePacket.getEntireByteArray();
						sendPacket = new DatagramPacket(packetBytes, packetBytes.length, dstAddress, 2001);
						sendSocket.send(sendPacket);
						state = 2;
					}
					//3rd part of handshake
					if (state == 2 && receivedHeader.isACK()) {
						state = 3;
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
	public void sendFilePackets(String fName) throws Exception {
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

			//Todo go back n
			boolean finished = false;
			while(!finished) {
				//send N packets
				for (int i = 0; i < byteArray.length; i++) {
					packetBytes[i] = byteArray[(packetNumber * (MAXBUFFER - 1)) + i];
					if (i % MAXBUFFER == 0 && !(i < MAXBUFFER)) {
						rtpHeader.setseqNum(sequenceNumber++);
						rtpPacket = new RTPPacket(rtpHeader, packetBytes);
						rtpPacket.updateChecksum();

						packetNumber += 1;
						sendRTPPacket(rtpPacket.getEntireByteArray());
					}
				}
				try {
					DatagramPacket packet = new DatagramPacket(new byte[MAXBUFFER],MAXBUFFER);
					recvSocket.receive(packet);
					byte[] receivedData = new byte[packet.getLength()];
					receivedData = Arrays.copyOfRange(packet.getData(),0,packet.getLength());
					RTPPacket receivedP = new RTPPacket(receivedData);
					RTPHeader receivedH = receivedP.getHeader();
					if(receivedH.getChecksum() == receivedP.calculateChecksum()) {

					} else {
						//update the window pointers?
						//add received packets to file?
					}
				} catch(Exception e) {
					System.out.println(e.getMessage());
					return;
				}

			}

			
			rtpHeader.setFIN(true);
			rtpHeader.setseqNum(sequenceNumber++);
			rtpPacket = new RTPPacket(rtpHeader, packetBytes);
			rtpPacket.updateChecksum();
			sendRTPPacket(rtpPacket.getEntireByteArray());
			
			
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