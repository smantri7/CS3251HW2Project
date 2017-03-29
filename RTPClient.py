#this is the client for the UDP connection (runs the client)
import socket
import sys
import zlib
from RTPHeader import RTPHeader
from RTPPacket import RTPPacket
import time
import select
from RTPWindow import RTPWindow
#networklab1 4000 5     #4000 5
class RTPClient:
	#constructor class that contains IP, portnumber, and the textfile name
	def __init__(self,host,portNum,winSize):
		self.host = host
		self.portNum = portNum
		self.winSize = winSize
		self.packets = []
		self.rtt = 1
		self.textfile = None
		self.currSeqNum = 0

	def streamToPacket(self,stream):
		packetBytes = bytearray(stream)
		headerPart = packetBytes[0:28] 
		dataPart = packetBytes[28:1028]
		#print("datapart type: " + str(type(dataPart)))
		#print(str(len(headerPart)))
		#print(str(headerPart))
		header = RTPHeader(0,0,0,0)
		header.convertArray(headerPart)
		packet = RTPPacket(header,dataPart)
		return packet
	#gets the message from the text file, converts to list of bits
	def transform(self,txtfile):
		f = open(txtfile,'r')
		self.textfile = txtfile
		ans = f.read()
		ans = ans.lower()
		f.close()
		ans = ans.encode('utf-8')
		b = bytes(ans)
		split = [b[i:i+1000] for i in range(0, len(b),1000)] #1000 byte is max packet load
		for d in split:
			header = RTPHeader(0,0,0,0) #update seqnum and checksum LATER
			p = RTPPacket(header,d)
			self.packets.append(p)
		print("Self.packets is: ",len(self.packets))
		#Turn text into bytes
	#turns byte into strings	
	def writeToFile(self,packList):
		#TODO
		f = open(self.textfile[:-4] + "-received.txt",'a')
		for p in packList:
			print(p.getHeader().getseqNum())
			if(p.getHeader().getseqNum() <= 5):
				#print(p.getHeader().getseqNum())
				print(str(p.getData()))
			f.write(str(p.getData().decode('UTF-8')))
		f.close()
	#Starts up the client, and gives it the necessary information needed
	def connect(self):
		#Check if user wants to transform
		#create socket, bind, begin the sending process
		sock = socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
		sockRecv = socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
		sock.bind((socket.gethostname(),2000))
		sockRecv.bind((socket.gethostname(),2001))
		#set the time out
		sockRecv.settimeout(1)
		#Before starting we need to get RTT with SYN bit

		h = RTPHeader(int(self.portNum) + 1,self.portNum,0,0)
		h.setSYN(True)
		pikmin = RTPPacket(h,bytearray(0))
		pikmin.getHeader().setseqNum(self.currSeqNum) #initial seq number of client
		pikmin.getHeader().setChecksum(self.checksum(pikmin)) #checksum of packet

		#Incase server crashes initially during 3-way handshake
		#Client sends syn bit and initial seq number
		#Client receives syn ack on, server init seq number
		#Client sends ack for that
		try:
			sock.sendto(pikmin.toByteStream(),(self.host,int(self.portNum)))
			resSock = sockRecv.recvfrom(1028)
		except Exception as e:
			print(e)
			print("Socket timed out. Server may have crashed.")
			return
		#calculate timeouts
		sock.settimeout(1)
		sockRecv.settimeout(1)
		packet = self.streamToPacket(resSock[0])
		if(packet.getHeader().getACK() is False or packet.getHeader().getSYN() is False or packet.getHeader().getseqNum() != (pikmin.getHeader().getseqNum() + 1)): #you also need to check for ack to the syn with an ack number that is the ISN+1
			print("Connection Refused! Try again!")
			return
		#Make sure to ACK the new
		try:
			h = RTPHeader(2000,int(self.portNum) + 1,0,0)
			h.setACK(True)
			pikmin = RTPPacket(h,bytearray(0))
			sock.sendto(pikmin.toByteStream(),(self.host,int(self.portNum)))
		except Exception as e:
			print("Connection Refused sending Error. Try again!")
			return
		print("Connection Successful!")
		self.send(sock,sockRecv)
		#receive(sockRecv)
		#Now we can start the true connection
		#################################################

	def send(self,sock,sockRecv):
		finished = False
		if(int(self.winSize) > len(self.packets)):
			window = RTPWindow(0,len(self.packets) - 1,len(self.packets))
		else:
			window = RTPWindow(0,int(self.winSize) - 1,len(self.packets))
		numPacks = 0
		#make sure that we are indexing the right amount
		while(not finished):
			#go back N implementation
			for i in range(window.getMin(),window.getMax() + 1):
				p = self.packets[i]
				#calc checksum and seq number of all the packets
				p.getHeader().setChecksum(self.checksum(p))
				p.getHeader().setseqNum(self.currSeqNum + i + 1)
				#print(p.getHeader().getseqNum())
				p.getHeader().setTimestamp(int(time.time() % 3600))
				#send each packet (convert to bytes)
				sock.sendto(p.toByteStream(),(self.host,int(self.portNum)))
				#start timer in the window
			#receive the acked packets one at a time, make sure everything is correct
			wait = True
			tries = 0
			#make sure that it doesn't timeout, otherwise server may have crashed
			startTime = time.process_time()
			while(wait and ((time.process_time() - startTime) < 1)):
				try:
					packet = sockRecv.recvfrom(1028)
					#check if packet is data packet or ACK/NACK etc
					packet = self.streamToPacket(packet[0])
					#if packet has data we go into receive state. Otherwise we go back to send state
					if(packet.getHeader().getACK() is True):
					#send ACK
						if(len(self.packets) == window.getMax() + 1): 
							print("Finished!")
							finished = True
							#send a fin bit
							h = RTPHeader(0,0,0,0)
							h.setFIN(True)
							h.setTimestamp(int(time.time() % 3600))
							p = RTPPacket(h,bytearray(0))
							sock.sendto(p.toByteStream(),(self.host,int(self.portNum)))
							break
						wait = self.updateWindowSend(packet,window)
					else: 
						print("Got to receive")
					#self.receive(sockRecv)
				#packet received, check if we need to move the window or not
				#good packets will now be converted into data, and updated properly
				except Exception as e:
					print(e)
					print("Timedout, resending")
					tries += 1
					if(tries == 3):
						print("Server may have crashed")
						return
		print("Receiving...")
		self.receive(sock,sockRecv)
	
	def receive(self,sock,sockRecv):
		if(len(self.packets) < int(self.winSize)):
			self.winSize = len(self.packets)
		self.empty_socket(sockRecv)
		self.empty_socket(sock)
		received = {}
		finished = False
		expectedSeqNum = 0
		expectedPackets = []
		while(not finished):
			try:
				p,addr = sockRecv.recvfrom(1028)
			except Exception as e:
				print(e)
				print("Socket timed out. Server may have crashed.")
				return
				#check if packet is data packet or ACK/NACK etc
				#send ACK
			packet = self.streamToPacket(p)
			print("Received packet: ", packet.getHeader().getseqNum())
			expectedPackets.append(packet)
			if(packet.getHeader().getFIN()):
				print("Got fin bit!")
				finished = True;
				break
			if(len(expectedPackets) == int(self.winSize)):
				print("Window Received!")
				ans = self.checkPackets(expectedPackets,expectedSeqNum,received,sock)
				received = ans[0]
				expectedSeqNum = ans[1]
				expectedPackets = []
			#This function will check what to do with expected
		print("Turning into file...")
		received = sorted(list(received.values()),key=lambda x:x.getHeader().getseqNum())
		self.writeToFile(received)
		##TODO SET UP CASE TO SEE IF WE ARE DONE##
		self.disconnect(sock,sockRecv)

	def checkPackets(self,expectedPackets,expectedSeqNum,received,sock):
		a = expectedSeqNum
		expectedPackets = sorted(list(expectedPackets), key=lambda p:p.getHeader().getseqNum())
		for packet in expectedPackets:
			expected = packet.getHeader().getChecksum()
			print("A: ",a)
			print("Expected: ",packet.getHeader().getseqNum())
			if(self.checkCorrupt(expected,packet) or a != packet.getHeader().getseqNum()): #IMPLEMENT TIMEOUT
					#packet is not accepted, send a nack
					#drop window

					h = RTPHeader(0,0,0,0)
					h.setNACK(True)
					h.setTimestamp(int(time.time() % 3600))
					print("Sent NACK!")
					sock.sendto(RTPPacket(h,bytearray(0)).toByteStream(),(self.host,int(self.portNum)))
					return [received,expectedSeqNum]
					#packet received, check if we need to move the window or not
					#take care of possible duplicates
					#print(len(received))
			else:
					#drops duplicates
				if(packet.getHeader().getseqNum() not in list(received.keys())):
					received[packet.getHeader().getseqNum()] = packet
				a += 1
		print("Window Accepted")
		h = RTPHeader(0,0,0,0)
		h.setACK(True)
		h.setTimestamp(int(time.time() % 3600))
		h.setseqNum(a + 1)
		print("Sending ACK FOR: ",packet.getHeader().getseqNum() + 1)
		sock.sendto(RTPPacket(h,bytearray(0)).toByteStream(),(self.host,int(self.portNum)))
		print("New A: ",a)
		return [received, a]

					#check if this is the final packet to receive
					#otherwise add normally
					#ready for next one. send syn
	
	def disconnect(self,sock,sockr):
		tries = 0
		#send disconnect (finish)
		while(True):
			h = RTPHeader(2000,int(self.portNum),0,0)
			h.setFIN(True)
			h.setTimestamp(int(time.time() % 3600))
			endPacket = RTPPacket(h,bytearray(0))
			sock.sendto(endPacket.toByteStream(),(self.host,int(self.portNum)))
		#receive disconnect
			try:
				ans,addr = sockr.recvfrom(1028)
				print("Received!")
				p = self.streamToPacket(ans)
				if(p.getHeader().getACK()):
					h = RTPHeader(2000,int(self.portNum),0,0)
					h.setACK(True)
					h.setTimestamp(int(time.time() % 3600))
					endPacket = RTPPacket(h,bytearray(0))
					sock.sendto(endPacket.toByteStream(),(self.host,int(self.portNum)))
					break; #we're done
			except Exception as e:
				tries += 1
				if(tries == 3):
					print("Socket timed out. Server may have crashed.")
					return
				print("Socket timed out. Resending...")
		#close sockets and end connection
		print("Closed connection...")
		sock.close()
		sockr.close()

	#moves your send window
	def updateWindowSend(self,packet,window):
		for x in range(len(self.packets)):
			if(self.packets[x].getHeader().getseqNum() <= packet.getHeader().getseqNum()):
				window.setList(x,True)
		if False not in window.getList()[window.getMin():window.getMax() + 1]:
			window.setMin(window.getMin() + int(self.winSize))
			window.setMax(window.getMax() + int(self.winSize))
			if(window.getMax() >= len(self.packets)):
				window.setMin(len(self.packets) - int(self.winSize))
				window.setMax(len(self.packets) - 1)
			return False
		return True

	def timeout(self,packet):
		print(self.rtt)
		print(int(time.time() % 3600))
		print(packet.getHeader().getTimestamp())
		return (int(time.time() % 3600) - packet.getHeader().getTimestamp()) > 2*self.rtt
	#Check if packet is corrupt
	def checkCorrupt(self,expected,packet):
		ei = int(expected)
		pi = int(self.checksum(packet))
		difference = ei - pi
		#print(str(difference))
		#print("ei: " + str(ei) + " pi: " + str(pi))
		return int(packet.getHeader().getChecksum()) != int(expected)

	def checksum(self, aPacket): # dude use CRC32. or not. we just need to decide on one
		crc = zlib.crc32(aPacket.getData()) & 0xffffffff
		return crc

	def empty_socket(self,sock):
		input = [sock]
		while True:
			inputready, o, e = select.select(input,[],[], 0.0)
			if len(inputready)==0: break
			for s in inputready: s.recv(1)

if __name__ == "__main__":
	c = RTPClient(sys.argv[1],sys.argv[2],sys.argv[3])
	c.transform("tests.txt")
	c.connect()