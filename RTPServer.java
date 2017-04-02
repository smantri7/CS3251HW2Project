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
import java.util.concurrent.TimeUnit;

public class RTPServer {
	private static final int MAXBUFFER = 1000;
	private static final int MAXRECV = 1028;
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
	private ArrayList<RTPPacket> receiveWindow; 

	private int wSize;
	private int rtt = 1;
	private int packetNum = 0;
	private int seqNum = 0;

	private int state = 0; //0==closed, 1==listen, 2==listen wait for ack, 3==established

	public RTPServer(int srcPort, int wSize) throws SocketException {
		this.srcPort = srcPort;
		this.wSize = wSize;

		filename = "bufferFile";
		packetSendBuffer = new ArrayList<RTPPacket>();
		packetReceivedBuffer = new ArrayList<RTPPacket>();
		recvSocket = new DatagramSocket(srcPort);
		recvSocket.setSoTimeout(2000);
		sendSocket = new DatagramSocket(srcPort + 1);
		sendSocket.setSoTimeout(2000);
		receiveWindow = new ArrayList<RTPPacket>(wSize);
	}

	public void sendRTPPacket(byte[] data, int sequenceNumber) throws IOException {
		RTPHeader responseHeader = new RTPHeader(srcPort, recvPacket.getPort(), 0); //0 for now
		responseHeader.setACK(true);
		responseHeader.setseqNum(sequenceNumber);
		RTPPacket responsePacket = new RTPPacket(responseHeader, data);
		responsePacket.updateChecksum();
		byte[] packetBytes = responsePacket.getEntireByteArray();
		sendPacket = new DatagramPacket(packetBytes, packetBytes.length, dstAddress, 2001);
		//System.out.printf("Sent packet: %d\n",sequenceNumber);
		sendSocket.send(sendPacket);
	}

	public void sendRPacket(byte[] data, RTPHeader header) throws IOException {
		header.setACK(true);
		RTPPacket responsePacket = new RTPPacket(header,data);
		responsePacket.updateChecksum();
		byte[] packetBytes = responsePacket.getEntireByteArray();
		sendPacket = new DatagramPacket(packetBytes,packetBytes.length,dstAddress,2001);
		sendSocket.send(sendPacket);
	}

	public void createFileFromByteArray(byte[] fileByteArray) {
		packetList = new ArrayList<RTPPacket>();
		String ans = new String(fileByteArray,StandardCharsets.UTF_8);
		ans = ans.toUpperCase();
		byte[] bytes = ans.getBytes(StandardCharsets.UTF_8);
		int seq = 0;
		int i = 0;
		byte[] packetBytes = new byte[MAXBUFFER];
		while(i < bytes.length) {
			//System.out.println(i);
			packetBytes = new byte[MAXBUFFER];
			if(i + MAXBUFFER > bytes.length) {
				//System.out.println("Resetting array...");
				packetBytes = new byte[bytes.length % MAXBUFFER];
			}
			//System.out.println("Packing bits..");
			for(int pack = 0; pack < MAXBUFFER; pack++) {
				if(i < bytes.length) {
					packetBytes[pack] = bytes[i];
					i++;
				}
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
				if(receivedHeader.isFIN() && receivedHeader.isNACK()) {
					System.out.println("Disconnecting...");
					disconnect();
				}
				if(receivedHeader.isSYN()) {
					state = 1;
					packetReceivedBuffer.clear();
				}
				if (state == 3 && receivedHeader.isFIN()) {
					if(receivedHeader.isNACK()) {
						System.out.println("Disconnecting...");
						disconnect();
					}
					Collections.sort(packetReceivedBuffer);
					System.out.println(packetReceivedBuffer.size());
					ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
					for (RTPPacket p : packetReceivedBuffer) {
						if (p.getData() != null) {
							outputStream.write(p.getData());
						}
					}
					System.out.println("Switching to send...");
					seqNum = 0;
					createFileFromByteArray(outputStream.toByteArray());	
					//server send state//
				}
				if (state == 3) {
					if(receivedRTPPacket.getHeader().getChecksum() == receivedRTPPacket.calculateChecksum()) {
						System.out.printf("Received PacketSeqNum... %d\n",receivedRTPPacket.getHeader().getseqNum());
						System.out.printf("Current Seq Num: %d\n",seqNum);
						if(seqNum == receivedRTPPacket.getHeader().getseqNum()) {
							RTPHeader responseHeader = new RTPHeader(srcPort, recvPacket.getPort(), 0); //0 for now
							responseHeader.setseqNum(seqNum);
							responseHeader.setACK(true);
							System.out.printf("Sent ACK for... %d\n",seqNum);
							RTPPacket responsePacket = new RTPPacket(responseHeader, null);
							responsePacket.updateChecksum();
							if(isBuffer(receivedRTPPacket.getHeader().getseqNum()) && !receivedRTPPacket.getHeader().isNACK()) {
								System.out.println("Added!");
								packetReceivedBuffer.add(receivedRTPPacket);
							}
							seqNum += 1;
							byte[] packetBytes = responsePacket.getEntireByteArray();
							sendPacket = new DatagramPacket(packetBytes, packetBytes.length, dstAddress, 2001);
							sendSocket.send(sendPacket);
						} else if (seqNum != 0) {
							RTPHeader responseHeader = new RTPHeader(srcPort, recvPacket.getPort(), 0); //0 for now
							responseHeader.setseqNum(seqNum - 1);
							responseHeader.setACK(true);
							System.out.printf("Sent ACK for... %d\n",seqNum);
							RTPPacket responsePacket = new RTPPacket(responseHeader, null);
							responsePacket.updateChecksum();
							byte[] packetBytes = responsePacket.getEntireByteArray();
							sendPacket = new DatagramPacket(packetBytes, packetBytes.length, dstAddress, 2001);
							sendSocket.send(sendPacket);
						}
					} 
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
				//e.printStackTrace();
				e.getMessage();
			}

		}
	}


	private void printWaitStatus(RTPWindow window) {
		int waitCounter = 0;
		String waitString = "";
		for (int i = 0; i < window.getrList().length; i++) {
			if (window.getrList()[i] == false) {
				waitCounter++;
				waitString += "" + i + ",";
			}
		}
		System.out.println("waitCounter: " + waitCounter + " waitString: " + waitString);
	}
	//TODO: IMPLEMENT GO BACK N
	//need more states? probably
	public void sendFilePackets() throws Exception {
		packetReceivedBuffer.clear();
		seqNum = 0;
		System.out.println("Packet list size: ");
		System.out.println(packetList.size());
		RTPWindow window = new RTPWindow(0,0,packetList.size());
		if(packetList.size() < wSize) {
			window.setMax(packetList.size() - 1);
		} else {
			window.setMax(this.wSize - 1);
		}
			//Todo go back n
		boolean finished = false;
		int tries = 0;
		while(!finished) {
				//send N packets
			for (int i = window.getMin(); i < (int) (Math.min(window.getMax() + 1, packetList.size())); i++) {
				RTPPacket constantine = packetList.get(i);
				System.out.printf("Sent a packet: %d\n",constantine.getHeader().getseqNum());
				sendRPacket(constantine.getPacketByteArray(),constantine.getHeader());
			}
			boolean wait = true;
			long startTime = System.nanoTime();
			while(wait && ((System.nanoTime() - startTime) < 200000)) {
				System.out.println(System.nanoTime() - startTime);
				try {
					//System.out.println("waiting...");
					DatagramPacket packet = new DatagramPacket(new byte[MAXBUFFER],MAXBUFFER);
					recvSocket.receive(packet);
					byte[] receivedData = new byte[packet.getLength()];
					receivedData = Arrays.copyOfRange(packet.getData(),0,packet.getLength());
					RTPPacket receivedP = new RTPPacket(receivedData);
					RTPHeader receivedH = receivedP.getHeader();
					System.out.println("Received Packet!");
					//System.out.println(receivedH.getseqNum());
					//System.out.println(receivedH.isSYN());
					if(receivedH.isFIN() && receivedH.isNACK()) {
						//we go to disconnect immediately
						disconnect();
					}
					if(receivedH.isACK()) { //TIMEOUT IMPLEMENTATION
					//we want to update window pointers
						tries = 0; //reset
						System.out.println("Received ACK!");
						//System.out.println("ps: " + packetList.size() + " wm: " + window.getMax());
						if(packetList.size() - 1 == receivedP.getHeader().getseqNum()) {
							System.out.println("Finished sending!");
							finished = true;
							RTPHeader rtpHeader = new RTPHeader();
							rtpHeader.setFIN(true);
							//rtpHeader.setSYN(true);
							rtpHeader.setseqNum(packetList.size());
							//System.out.println(rtpHeader.getseqNum());
							RTPPacket rtpPacket = new RTPPacket(rtpHeader, null);
							rtpPacket.updateChecksum();
							//System.out.println(rtpPacket.getHeader().getChecksum());
							//System.out.println(new String(rtpPacket.getEntireByteArray()));
							sendRPacket(rtpPacket.getEntireByteArray(),rtpPacket.getHeader());
							wait = false;
							finished = true;
							System.out.println("Go back to listen state?");
							seqNum = 0;
							listen();
						}
						System.out.println(receivedP.getHeader().getseqNum());
						System.out.println("Update window!");
						wait = updateWindowSend(receivedP, window);
					}
				} catch(Exception e) {
					//System.out.println("caught exception 1");
					//e.printStackTrace();
					//System.out.println(e.getMessage());
					break;
				}
			}			
		}
	}

	public boolean updateWindowSend(RTPPacket packet, RTPWindow window) {
		if(seqNum <= packet.getHeader().getseqNum()) {
			seqNum = packet.getHeader().getseqNum() + 1;
			window.setMin(packet.getHeader().getseqNum() + 1);
			int max = (window.getMin() + wSize > packetList.size()) ? packetList.size() - 1 : window.getMin() + wSize - 1;
			//if(max == packetList.size() - 1) {
			//	window.setMin(packetList.size() - wSize);
			//}
			window.setMax(max);
			return false;
		}
		return true;
	}

	public boolean timeout(RTPPacket packet) {
		return (((int) ((System.currentTimeMillis()/1000) % 3600)) - packet.getHeader().getTimestamp()) > 2*rtt;
	}

	public void disconnect() {
		System.out.println("Closing connection with the client...");
		int tries = 0;
		while(true) {
			//Now we send the finACK
			try {
				System.out.println("Sending Fin ACK");
				RTPHeader rtpHeader = new RTPHeader();
				RTPPacket rtpPacket = new RTPPacket(rtpHeader, null);
				rtpPacket.updateChecksum();
				sendRPacket(rtpPacket.getEntireByteArray(),rtpPacket.getHeader());

				//Now we receive the ACK from Client
				DatagramPacket p = new DatagramPacket(new byte[MAXBUFFER],MAXBUFFER);
				recvSocket.receive(p);
				byte[] rd = new byte[p.getLength()];
				rd = Arrays.copyOfRange(p.getData(),0,p.getLength());
				RTPPacket rp = new RTPPacket(rd);
				RTPHeader rh = rp.getHeader();
				//receive the final ack. Close everything and sockets
				if(rh.isACK()) {
					System.out.println("Client closed...");
					state = 1;
					break;
				}
		//end the connection
			} catch(Exception e) {
				tries += 1;
				if(tries == 3) {
					System.out.println("Client may have crashed, forcibly ending connection");
					state = 1;
					break;
				}
				System.out.println("Error. Resending Fin bit...");
			}
		}
		state = 1;
		listen();
	}

	public boolean isBuffer(int s) {
		for(RTPPacket p : packetReceivedBuffer) {
			if(p.getHeader().getseqNum() == s) {
				return false;
			}
		}
		System.out.println("Adding to buffer!");
		return true;
	}

	public static void main(String args[]) throws Exception {
		RTPServer server = new RTPServer(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
		server.recvPacket = new DatagramPacket(new byte[MAXBUFFER], MAXBUFFER);
		server.state = 1;
		server.listen();
	}
}
