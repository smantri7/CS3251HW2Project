#keeps track of the window pointers, and whether packets are received or not, etc
class RTPWindow:
	def __init__(self,mini,maxi,numPackets):
		self.mini = mini
		self.maxi = maxi
		#A list of booleans with index referring to pointer of window and T/F if packet received
		self.rList = []
		for i in range(numPackets):
			self.rList.append(False)

	def setMax(self,val):
		self.maxi = val

	def getMax(self):
		return self.maxi

	def getMin(self):
		return self.mini

	def setMin(self,val):
		self.mini = val

	def getList(self):
		return self.rList

	def setList(self,pos,val):
		self.rList[pos] = val
		