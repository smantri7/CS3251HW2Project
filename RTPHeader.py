#RTPHeader for python
import struct

class RTPHeader:
	def __init__(self,srcPort,dstPort,seqNum,checksum): 
		self.ack = False
		self.nack = False
		self.syn = False
		self.fin = False
		self.beg = False
		self.timestamp = 0
		self.windowSizeOffset = 0
		self.srcPort = int(srcPort)
		self.dstPort = int(dstPort)
		self.seqNum = int(seqNum)
		self.checksum = int(checksum)

	def emptyHeader(self):
		self.ack = False
		self.nack = False
		self.syn = False
		self.fin = False
		self.beg = False 
		self.timestamp = 0 
		self.windowSizeOffset = 0

	def convertArray(self,headerBytes):
		self.srcPort, self.dstPort, self.seqNum, self.windowSizeOffset, self.checksum, flags, self.timestamp = struct.unpack("!IIIIIII", headerBytes)
		self.ack = ((self.rshift(flags,31)) & 0x1) != 0
		self.nack = ((self.rshift(flags,30)) & 0x1) != 0
		self.syn = ((self.rshift(flags,29)) & 0x1) != 0
		self.fin = ((self.rshift(flags,28)) & 0x1) != 0
		self.beg = ((self.rshift(flags,27)) & 0x1) != 0


	def rshift(self,val, n): 
		return (val % 0x100000000) >> n

	def getsrcPort(self):
		return self.srcPort

	def setsrcPort(self,val):
		self.srcPort = int(val)

	def getdstPort(self):
		return self.dstPort

	def setdstPort(self,val):
		self.dstPort = int(val)

	def setseqNum(self,val):
		self.seqNum = int(val)

	def getseqNum(self):
		return self.seqNum

	def getACK(self):
		return self.ack

	def setACK(self,boole):
		self.ack = boole

	def getSYN(self):
		return self.syn

	def setSYN(self,boole):
		self.syn = boole

	def getNACK(self):
		return self.nack

	def setNACK(self,boole):
		self.nack = boole

	def getFIN(self):
		return self.fin

	def setFIN(self,boole):
		self.fin = boole

	def getBEG(self):
		return self.beg

	def setBEG(self,boole):
		self.beg = boole

	def getChecksum(self):
		return self.checksum

	def setChecksum(self,val):
		self.checksum = int(val)

	def getByteArray(self):
		ackByte = 0 << 31 
		nackByte = 0 << 30
		synByte = 0 << 29
		finByte = 0 << 28
		begByte = 0 << 27
		if(self.ack):
			ackByte = 1 << 31
		if(self.nack):
			nackByte = 1 << 30
		if(self.syn):
			synByte = 1 << 29
		if(self.fin):
			finByte = 1 << 28
		if(self.beg):
			begByte = 1 << 27
		flags = ackByte | nackByte | synByte | finByte | begByte
		# ans = bytes(self.srcPort) + bytes(self.dstPort) + bytes(self.seqNum) + bytes(self.windowSizeOffset) +  bytes(flags) + bytes(self.timestamp)
		# ans = self.srcPort |
		# return ans
		# print(self.srcPort, self.dstPort, self.seqNum, self.windowSizeOffset, self.getChecksum(), flags, self.getTimestamp())
		ans = struct.pack("!IIIIIII", self.srcPort, self.dstPort, self.seqNum, self.windowSizeOffset, self.getChecksum(), flags, self.getTimestamp())
		return ans

	def getTimestamp(self):         
		return self.timestamp         

	def setTimestamp(self,time):                  
		self.timestamp = time         