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
import java.nio.charset.StandardCharsets;
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
	private String filename;

	private ArrayList<RTPPacket> packetSendBuffer;
	private ArrayList<RTPPacket> packetReceivedBuffer;
	private ArrayList<RTPPacket> packetList;

	private int wSize;
	private int rtt = 1;

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

	public void sendRTPPacket(byte[] data, int sequenceNumber) throws IOException {
		RTPHeader responseHeader = new RTPHeader(srcPort, recvPacket.getPort(), 0); //0 for now
		responseHeader.setACK(true);
		responseHeader.setseqNum(sequenceNumber);
		RTPPacket responsePacket = new RTPPacket(responseHeader, data);
		responsePacket.updateChecksum();
		byte[] packetBytes = responsePacket.getEntireByteArray();
		sendPacket = new DatagramPacket(packetBytes, packetBytes.length, dstAddress, 2001);
		System.out.printf("Sent packet: %d\n",sequenceNumber);
		sendSocket.send(sendPacket);
	}

	public void createFileFromByteArray(byte[] fileByteArray) {
		packetList = new ArrayList<RTPPacket>();
		String ans = new String(fileByteArray,StandardCharsets.UTF_8);
		ans = ans.toUpperCase();
		byte[] bytes = ans.getBytes(StandardCharsets.UTF_8);
		int seq = 100;
		for(int i = 0; i < bytes.length; i += MAXBUFFER) {
			byte[] packetBytes = null;
			if(i + MAXBUFFER >= bytes.length) {
				packetBytes = new byte[bytes.length % MAXBUFFER];
			} else {
				packetBytes = new byte[MAXBUFFER];
			}
			RTPHeader getsomehead = new RTPHeader();
			getsomehead.setseqNum(seq);
			//System.out.println(getsomehead.getseqNum());
			RTPPacket cuck = new RTPPacket(getsomehead,packetBytes);
			packetList.add(cuck);
			seq++;
		}
		try {
			System.out.println("Trying to send packets...");
			sendFilePackets();
		} catch(Exception e) {
			System.out.println(e.getMessage());
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
				byte[] receivedData = new byte[packet.getLength()];
				receivedData = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());

				RTPPacket receivedRTPPacket = new RTPPacket(receivedData);
				RTPHeader receivedHeader = receivedRTPPacket.getHeader();
				//System.out.println(receivedHeader.isSYN());
				//Validate Checksum
				//System.out.println(receivedHeader.getChecksum());
				//System.out.println(receivedRTPPacket.calculateChecksum());
				//put incoming packets into receive buffer
				//fin indicates end of file
				if (state == 3 && receivedHeader.isFIN()) {
					ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
					for (RTPPacket p : packetReceivedBuffer) {
						outputStream.write(p.getData());
					}
					System.out.println("Switching to receive...");
					createFileFromByteArray(outputStream.toByteArray());
					packetReceivedBuffer.clear();
					state = 1;	
					//server send state//
				}
				if (state == 3) {
					packetReceivedBuffer.add(receivedRTPPacket);
					RTPHeader responseHeader = new RTPHeader(srcPort, recvPacket.getPort(), 0); //0 for now
					responseHeader.setACK(true);
					responseHeader.setseqNum(receivedHeader.getseqNum() + 1);
					RTPPacket responsePacket = new RTPPacket(responseHeader, null);
					responsePacket.updateChecksum();

					byte[] packetBytes = responsePacket.getEntireByteArray();
					sendPacket = new DatagramPacket(packetBytes, packetBytes.length, dstAddress, 2001);
					//System.out.println("Sent ACK packet in state 3");
					//System.out.printf("Seq number is: %d",responsePacket.getHeader().getseqNum());
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
					} else if (state == 2 && receivedHeader.isACK()) {
						state = 3;
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
	public void sendFilePackets() throws Exception {
		RTPWindow window = new RTPWindow(0,0,packetList.size());
		if(packetList.size() < wSize) {
			window.setMax(packetList.size() - 1);
		} else {
			window.setMax(this.wSize - 1);
		}
		try {
			//Todo go back n
			boolean finished = false;
			int tries = 0;
			while(!finished) {
				//send N packets
				for (int i = window.getMin(); i < window.getMax() + 1; i++) {
					RTPPacket constantine = packetList.get(i);
					//constantine.getHeader().setTimestamp((int) ((System.currentTimeMillis()/1000) % 3600));
					//System.out.println(constantine.getHeader().getseqNum());
					sendRTPPacket(constantine.getEntireByteArray(),constantine.getHeader().getseqNum());
				}
				boolean wait = true;
				while(wait) {
					try {
						DatagramPacket packet = new DatagramPacket(new byte[MAXBUFFER],MAXBUFFER);
						recvSocket.receive(packet);
						byte[] receivedData = new byte[packet.getLength()];
						receivedData = Arrays.copyOfRange(packet.getData(),0,packet.getLength());
						RTPPacket receivedP = new RTPPacket(receivedData);
						RTPHeader receivedH = receivedP.getHeader();
						System.out.println(receivedH.getseqNum());
						if(receivedH.isACK()) { //TIMEOUT IMPLEMENTATION
						//we want to update window pointers
							tries = 0; //reset
							if(packetList.size() == window.getMax() + 1) {
								System.out.println("Finished sending!");
								finished = true;
								RTPHeader rtpHeader = new RTPHeader();
								rtpHeader.setFIN(true);
								rtpHeader.setseqNum(packetList.size());
								rtpHeader.setTimestamp((int) ((System.currentTimeMillis()/1000) % 3600));
								RTPPacket rtpPacket = new RTPPacket(rtpHeader, null);
								rtpPacket.updateChecksum();
								sendRTPPacket(rtpPacket.getEntireByteArray(),rtpPacket.getHeader().getseqNum());
								wait = false;
								finished = true;
								System.out.println("Go back to listen state?");
								break;
							}
							wait = updateWindowSend(receivedP, window);
						} else if(receivedP.getHeader().isNACK()) { //TIMEOUT IMPLEMENTATION
							tries++;
							System.out.println("Timed out. Resending...");
							wait = false;
							if(tries == 3) {
								System.out.println("Client may have crashed...");
								return;
							}
						} else {
							System.out.println("Waiting...");
						}
					} catch(Exception e) {
						System.out.println(e.getMessage());
						return;
					}
				}
			}
		} catch(Exception e) {
			System.out.println(e.getMessage());
			return;
		}			
	}

	public boolean updateWindowSend(RTPPacket packet, RTPWindow window) {
		for(int i = window.getMin(); i < window.getMax() + 1; i++) {
			if(packet.getHeader().getseqNum() >= packetList.get(i).getHeader().getseqNum()) {
				window.setrList(i,true);
			}
		}
		boolean move = true;
		for(int x = window.getMin(); x < window.getMax(); x++) {
			if(!window.getrList()[x]) {
				move = false;
			}
		}
		if(move) {
			window.setMin(window.getMin() + wSize + 1);
			int max = (window.getMin() + wSize > packetList.size()) ? packetList.size() - 1 : window.getMin() + wSize;
			window.setMax(max);
		}
		return !move;
	}

	public boolean timeout(RTPPacket packet) {
		return (((int) ((System.currentTimeMillis()/1000) % 3600)) - packet.getHeader().getTimestamp()) > 2*rtt;
	}

	public static void main(String args[]) throws Exception {
		RTPServer server = new RTPServer(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
		server.recvPacket = new DatagramPacket(new byte[MAXBUFFER], MAXBUFFER);
		server.state = 1;
		server.listen();
	}
}