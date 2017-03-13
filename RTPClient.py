#this is the client for the UDP connection (runs the client)
import socket
import sys
import hashlib
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
		self.window = None
		self.rtt = None
		self.textfile = None
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
		#initialize window
		self.window = RTPWindow(0,self.winSize - 1,len(self.packets))
		if(self.winSize > len(self.packets)):
			self.window.setMax(len(self.packets))
	#turns byte into strings	
	def receive(self,p):
		#TODO
		f = open(self.textfile + "-received.txt",'a')
		f.write(str(p.getData()))
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

		h = RTPHeader(self.host,self.portNum,0,0)
		h.setSYN(True)
		tinit = time.time()
		pikmin = RTPPacket(h,0)
		temp = pikmin.getHeader().setChecksum(self.checksum(pikmin))
		pikmin.getHeader().setseqNum(bytes(pikmin.getData())[0])
		#Incase server crashes initially during 3-way handshake
		try:
			sock.sendto(pikmin,(self.host,int(self.portNum)))
			resSock = sockRecv.recvfrom(4096)
			nSent = False
		except Exception as e:
			print("Socket timed out. Server may have crashed.")
			return
		tfinal = time.time()
		self.rtt = tfinal - tinit
		sockRecv.settimeout(2*self.rtt)

		if(not(resSock[0].getHeader().getBEG()) or resSock[0].getHeader().getseqNum() == (pikmin.getHeader().getseqNum() + 1)): #you also need to check for ack to the syn with an ack number that is the ISN+1
			print("Connection Refused! Try again!")
			return
		h = RTPHeader()
		h.setACK(True)
		try:
			sock.sendto(RTPPacket(self.host,self.portNum,h,0),(self.host,self.portNum))
			resSock = sockRecv.recvfrom(4096)
		except Exception as e:
			print("Socket timed out. Server may have crashed.")
			return
		if(resSock[0].getHeader().getACK() is False):
			print("Conection Refused! Try again!!")
			return
		#Now we can start the true connection
		#################################################
		finished = False
		#make sure that we are indexing the right amount
		while(not finished):
			#selective repeat implementation
			for i in range(self.window.getMin(),self.window.getMax()):
				p = self.packets[i]
				#calc checksum and seq number of all the packets
				p.getHeader().setChecksum(self.checksum(p))
				p.getHeader().setseqNum(bytes(p.getData()[0]))
				p.getHeader().setTimestamp(time.time())
				#send each packet 
				sock.sendto(self.packets[i],(self.host,int(self.portNum)))
				#start timer in the window
			#receive the packet one at a time, make sure everything is correct

			#make sure that it doesn't timeout, otherwise server may have crashed
			try:
				packet,addr = sockRecv.recvfrom(4096)
			except Exception as e:
				print("Socket timed out. Server may have crashed.")
				return
			#check if packet is data packet or ACK/NACK etc
			#send ACK
			expected = checksum(packet)
			if(not(checkCorrupt(expected,packet)) or timeout(packet)):
				#packet is not accepted, send a nack
				h = RTPHeader()
				h.setNACK(True)
				h.setTimestamp(time.time())
				sock.sendto(RTPPacket(self.host,self.portNum,h,0),(self.host,self.portNum))
			else:
				#packet received, check if we need to move the window or not
				updateWindow(packet)
				#good packets will now be converted into data, and updated properly
				receive(packet)

	
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
		return

	def updateWindow(self,packet):
		for i in range(len(self.packets)):
			#guarantees that corrupted data packet will be dropped
			if(packet.getData() == self.packets[i].getData()):
				self.window.setList(i,True)
				if False not in self.window.getList()[self.window.getMin():self.window.getMax() + 1]:
					self.window.setMin(self.window.getMin() + self.winSize)
					self.window.setMax(self.window.getMax() + self.winSize)
					if(self.window.getMax() > len(self.packets)):
						self.window.setMax(len(self.packets))
				return


	def timeout(self,packet):
		return (time.time() - packet.getHeader().getTimestamp()) > self.rtt
	#Check if packet is corrupt
	def checkCorrupt(self,expected,packet):
		return packet.getHeader().getChecksum() == expected

	def checksum(self, aPacket): # dude use CRC32. or not. we just need to decide on one
		header = aPacket.getHeader()
		first = hashlib.md5(str(aPacket.getData())).hexdigest()
		second = hashlib.md5(aPacket.getHeader().getByteArray()).hexdigest()
		return first + second

if __name__ == "__main__":
	c = RTPClient(sys.argv[1],sys.argv[2],sys.argv[3])
	while(c.connect()):
		a = input("")
		if("transform" in str(a)):
			fname = a[10:]
			c.transform(fname)
