#this is the client for the UDP connection (runs the client)
import socket
import sys
import hashlib
import relpacket

class client:
	#constructor class that contains IP, portnumber, and the textfile name
	def __init__(self,host,portNum,winSize):
		self.host = host
		self.portNum = portNum
		self.winSize = winSize
		self.packets = []
	#gets the message from the text file, converts to list of bits
	def transform():
		f = open(txtfile,'r')
		ans = f.read()
		f.close()
		b = bytes(ans)
		split = [b[i:i+self.winSize] for i in range(0, len(b),self.winSize)]
		for d in split:
			p = relpacket(d,checksum(d),0)
			self.packets.append(p)
		#Turn text into bytes

	#turns byte into string. Figures out if checksum is equal or not	
	def receive(self):
		#TODO

	#Starts up the client, and gives it the necessary information needed
	def connect(self):
		#TODO

	#Check if packet is corrupt
	def checkCorrupt(self):
		#TODO

	#Check if packet is duplicate
	def checkDuplicate(self):
		#TODO

	#Check if packets are in order	
	def checkOrder(self):
		#TODO

	#Check if packet is lost
	def checkLost(self):
		#TODO

if __name__ == "__main__":
	c = client(sys.argv[1],sys.argv[2],sys.argv[3])
	c.connect()