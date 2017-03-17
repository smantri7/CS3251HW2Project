#this is the client for the UDP connection (runs the client)
import socket
import sys
import zlib
from RTPHeader import RTPHeader
from RTPPacket import RTPPacket
import time
import RTPWindow

class RTPClient:
	#constructor class that contains IP, portnumber, and the textfile name
	def __init__(self,host,portNum,winSize):
		self.host = host
		self.portNum = portNum
		self.winSize = winSize
		self.packets = []
		self.rtt = None
		self.textfile = None
		self.currSeqNum = 0

	def streamToPacket(self,stream):
		headerPart = bytearray(stream)[0:29] 
		dataPart = bytearray(stream)[29:]
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
		b = bytes(ans)
		split = [b[i:i+1000] for i in range(0, len(b),1000)]
		for d in split:
			header = RTPHeader(self.host,self.portNum,0,0)
			p = RTPPacket(self.host,self.portNum,header,d)
			self.packets.append(p)
		#Turn text into bytes
	#turns byte into strings	
	def writeToFile(self,packList):
		#TODO
		f = open(self.textfile + "-received.txt",'a')
		for p in packList:
			f.write(str(p.getData().decode('UTF-8')))
		f.close()
	#Starts up the client, and gives it the necessary information needed
	def connect(self):
		#TODO
		#create socket, bind, begin the sending process
		sock = socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
		sockRecv = socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
		sock.bind((socket.gethostname(),int(self.portNum)))
		sockRecv.bind((socket.gethostname(),int(self.portNum) + 1))
		#set the time out
		sockRecv.settimeout(5)
		#Before starting we need to get RTT with SYN bit

		h = RTPHeader(int(self.portNum) + 1,self.portNum,0,0)
		h.setSYN(True)
		tinit = time.time()
		pikmin = RTPPacket(h,0)
		pikmin.getHeader().setseqNum(self.currSeqNum) #initial seq number of client
		pikmin.getHeader().setChecksum(self.checksum(pikmin)) #checksum of packet
		#Incase server crashes initially during 3-way handshake
		#Client sends syn bit and initial seq number
		#Client receives syn ack on, server init seq number
		#Client sends ack for that
		try:
			sock.sendto(pikmin.toByteStream(),(self.host,int(self.portNum)))
			resSock,addr = sockRecv.recvfrom(4096)
		except Exception as e:
			print(e)
			print("Socket timed out. Server may have crashed.")
			return
		#calculate timeouts
		tfinal = time.time()
		self.rtt = tfinal - tinit
		sock.settimeout(self.rtt*2)
		sockRecv.settimeout(self.rtt*2)
		packet = stramToPacket(resSock[0])
		if(packet.getHeader().getACK() is False or packet.getHeader().getseqNum() != (pikmin.getHeader().getseqNum() + 1)): #you also need to check for ack to the syn with an ack number that is the ISN+1
			print("Connection Refused! Try again!")
			return
		#Make sure to ACK the new
		try:
			h = RTPHeader(int(self.portNum) + 1,self.portNum,0,0)
			h.setACK(True)
			pikmin = RTPPacket(self.host,self.portNum,h,0)
			sock.sendto(pikmin.toByteStream(),(self.host,self.portNum))
		except Exception as e:
			print("Connection Refused sending Error. Try again!")
			return
		send(sock)
		receive(sockRecv)
		#Now we can start the true connection
		#################################################

	def send(self,sock):
		finished = False
		if(self.winSize > len(self.packets)):
			window = RTPWindow(0,len(self.packets),len(self.packets))
		else:
			window = RTPWindow(0,self.winSize - 1,len(self.packets))
		#make sure that we are indexing the right amount
		while(not finished):
			#go back N implementation
			for i in range(window.getMin(),window.getMax()):
				p = self.packets[i]
				#calc checksum and seq number of all the packets
				p.getHeader().setChecksum(self.checksum(p))
				p.getHeader().setseqNum(self.currSeqNum + i + 1)
				p.getHeader().setTimestamp(time.time())
				#send each packet (convert to bytes)
				sock.sendto(p.toByteStream(),(self.host,int(self.portNum)))
				#start timer in the window
			#receive the acked packets one at a time, make sure everything is correct
			wait = True
			tries = 0
			#make sure that it doesn't timeout, otherwise server may have crashed
			while(wait):
				try:
					packet,addr = sock.recvfrom(4096)
				except Exception as e:
					print("Timedout, resending")
					wait = False
					tries += 1
					if(tries == 3):
						print("Server may have crashed")
						return
					break
				#check if packet is data packet or ACK/NACK etc
				#send ACK
				if(len(self.packets) == window.getMax()): 
					finished = True
					break
				packet = streamToPacket(packet)
				if(packet.getHeader().getACK()):
					wait = False 
					updateWindowSend(window)
				#packet received, check if we need to move the window or not
			
				#good packets will now be converted into data, and updated properly
	
	def receive(self,sockRecv):
		received = []
		temp = []
		finished = False
		while(not finished):
			try:
				p,addr = sockRecv.recvfrom(4096)
			except Exception as e:
				print("Socket timed out. Server may have crashed.")
				return
				#check if packet is data packet or ACK/NACK etc
				#send ACK
			packet = streamToPacket(p)
			if(checkCorrupt(packet) or timeout(packet)):
					#packet is not accepted, send a nack
				temp = []
				h = RTPHeader()
				h.setNACK(True)
				h.setTimestamp(time.time())
				sock.sendto(RTPPacket(self.host,self.portNum,h,0).toByteStream(),(self.host,self.portNum))
			else:
				#packet received, check if we need to move the window or not
				h = RTPHeader()
				h.setACK(True)
				h.setTimestamp(time.time())
				sock.sendto(RTPPacket(self.host,self.portNum,h,0).toByteStream(),(self.host,self.portNum))
				#take care of possible duplicates
				if(packet not in temp):
					temp.append(packet)
				if(packet.getFIN()):
					received += temp
					finished = True;
				if(len(temp) == self.winSize):
					received += temp
					temp = []
					#ready for next one. send syn
		received = sorted(received,key=lambda x:x.getHeader().getseqNum(),reverse=True)
		for r in received:
			sendtoFile(r)

	
	def disconnect(self,sock,sockr):
		#send disconnect (finish)
		while(True):
			h = RTPHeader()
			h.setFin(True)
			h.setTimestamp(time.time())
			sock.sendto(RTPPacket(self.host,self.portNum,h,0),(self.host,self.portNum))
		#receive disconnect
			try:
				packet,addr = sockr.recvfrom(4096)
			except Exception as e:
				print("Socket timed out. Server may have crashed.")
				return
			if(packet.getHeader().getACK() and not timeout(packet)):
				break
		#close sockets and end connection
		sock.close()
		sockr.close()

	#moves your send window
	def updateWindowSend(self,packet,window):
		for x in range(len(self.packets)):
			if(self.packets[x].getseqNum() <= packet.getseqNum()):
				window.setList(x,True)
		i = 0
		while(window.getList()[i] is True):
			i+=1
		window.setMin(window.getMin() + i)
		window.setMax(window.getMax() + i)
		if(window.getMax() >= len(self.packets)):
			window.setMax(len(self.packets))


	def timeout(self,packet):
		return (time.time() - packet.getHeader().getTimestamp()) > 2*self.rtt
	#Check if packet is corrupt
	def checkCorrupt(self,expected,packet):
		expected = self.checksum(packet)
		return packet.getHeader().getChecksum() != expected

	def checksum(self, aPacket): # dude use CRC32. or not. we just need to decide on one
		crc = zlib.crc32(bytes(aPacket.getData())) & 0xffffffff
		return crc

if __name__ == "__main__":
	c = RTPClient(sys.argv[1],sys.argv[2],sys.argv[3])
	while(c.connect()):
		a = input("")
		if("tran4sform" in str(a)):
			fname = a[10:]
			c.transform(fname)