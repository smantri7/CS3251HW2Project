#this is the packet class, to be used to store data and checksum value
class relpacket:
	#sets up the packet
	#If packet is corrupt ack is 1, else ack is 0
	def __init__(self,src,dest,header,data):
		self.data = data #list/array of bytes
		self.header = header
		self.src = src
		self.dest = dest

	def __init__(self,src,dest):
		self.src = src
		self.dest = dest
		self.header = None
		self.data = None

	def __init__(self,header,data):
		self.header = header
		self.data = data

	def __init__(self,barray):
		self.header = RTPHeader(barray[0:28])
		self.data = barray[29:]

	def getData():
		return self.data

	def getHeader():
		return self.value

	def setData(d):
		self.data = d

	def setHeader(v):
		self.header = v

	def checksum(self, b):
		return hashlib.md5(b).hexdigest()