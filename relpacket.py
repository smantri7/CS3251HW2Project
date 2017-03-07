#this is the packet class, to be used to store data and checksum value
class relpacket:
	#sets up the packet
	#If packet is corrupt ack is 1, else ack is 0
	def __init__(self,data,value,ack):
		self.data = data
		self.value = value
		self.ack = ack

	def getData():
		return self.data

	def getValue():
		return self.value

	def getAck():
		return self.ack

	def setData(d):
		self.data = d

	def setValue(v):
		self.value = v

	def setAck(a):
		self.ack = a