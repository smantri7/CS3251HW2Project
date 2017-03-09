#RTPHeader for python
import bitstring
import bitarray
class RTPHeader:
	def __init__(self):
		self.ack = False
		self.nack = False
		self.syn = False
		self.fin = False
		self.beg = False 
		self.timestamp = 0 
		self.windowSizeOffset = 0 
	
	def __init__(self,srcPort,dstPort,seqNum,checksum): 
		self.ack = False
		self.nack = False
		self.syn = False
		self.fin = False
		self.beg = False
		self.timestamp = 0
		self.windowSizeOffset = 0
		self.srcPort = srcPort
		self.dstPort = dstPort
		self.seqNum = seqNum
		self.checksum = checksum

	def __init__(self,array):
		self.srcPort = array[0:6]
		self.dstPort = array[7:13]
		self.seqNum = array[14:20]
		self.windowSizeOffset = array[21:27]
		self.checksum = array[28:34]
		flags = array[35:41]
		self.timestamp = array[42:48]
		self.ack = ((flags >>> 31) & 0x1) != 0
		self.nack = ((flags >>> 30) & 0x1) != 0
		self.syn = ((flags >>> 29) & 0x1) != 0
		self.fin = ((flags >>> 28) & 0x1) != 0
		self.beg = ((flags >>> 27) & 0x1) != 0

	def getsrcPort(self):
		return self.srcPort

	def setsrcPort(self,val):
		self.srcPort = val

	def getdstPort(self):
		return self.dstPort

	def setdstPort(self,val):
		self.dstPort = val

	def setseqNum(self,val):
		self.seqNum = val

	def getseqNum(self):
		return self.seqNum

	def checksum(self, b):
		return hashlib.md5(b).hexdigest()

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
		self.checksum = val

	def getByteArray(self):
		stream = bitstring.BitStream()
		stream.append(self.srcPort)
		stream.append(self.dstPort)
		stream.append(self.seqNum)
		stream.append(self.windowSizeOffset)
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
		stream.append(flags)
		stream.append(self.timestamp)
		ans = bitarray.bitarray(stream)
		return ans

	def getTimestamp(self):
		return self.timestamp

	def setTimestamp(self,time):
		self.timestamp = time