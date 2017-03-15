#this is the packet class, to be used to store data and checksum value
class RTPPacket:
	#sets up the packet
	#If packet is corrupt ack is 1, else ack is 0
	def __init__(self,header,data):
		self.data = data #list/array of bytes
		self.header = header

	def getData(self):
		return self.data

	def getHeader(self):
		return self.header

	def setData(self,d):
		self.data = d

	def setHeader(self,v):
		self.header = v

	def toByteStream(self):
		return self.getHeader().getByteArray() + bytes(self.getData()) 