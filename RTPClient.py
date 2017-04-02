#this is the client for the UDP connection (runs the client)
import socket
import sys
import zlib
from RTPHeader import RTPHeader
from RTPPacket import RTPPacket
import time
import select
from RTPWindow import RTPWindow
import thread

#networklab1 4000 5     #4000 5

networkClient = None
commandQueue = []

def queue(item):
    global commandQueue
    commandQueue.append(item)

def dequeue():
    global commandQueue
    if len(commandQueue) < 1:
        return None
    else:
        result = commandQueue[0]
        commandQueue = commandQueue[1:]
        return result

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
                self.clientSock = None
                self.clientSockRecv = None

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
                seqNum = 0
                for d in split:
                        header = RTPHeader(0,0,0,0) #update seqnum and checksum LATER
                        header.setseqNum(seqNum)
                        p = RTPPacket(header,d)
                        self.packets.append(p)
                        seqNum += 1
                print("Self.packets is: ",len(self.packets))
                #Turn text into bytes
        #turns byte into strings        
        def writeToFile(self,packList):
                #TODO
                f = open(self.textfile[:-4] + "-received.txt",'a')
                for p in packList:
                        print(p.getHeader().getseqNum())
                        if(p.getHeader().getseqNum() >= 260):
                                #print(p.getHeader().getseqNum())
                                print(str(p.getData()))
                        f.write(str(p.getData().decode('utf-8')))
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
                pikmin.getHeader().setseqNum(0) #initial seq number of client
                pikmin.getHeader().setChecksum(self.checksum(pikmin)) #checksum of packet

                #Incase server crashes initially during 3-way handshake
                #Client sends syn bit and initial seq number
                #Client receives syn ack on, server init seq number
                #Client sends ack for that
                tries = 0
                while True:
                        sock.sendto(pikmin.toByteStream(),(self.host,int(self.portNum)))
                        try:
                                resSock = sockRecv.recvfrom(1028)
                                break
                        except Exception as e:
                                tries += 1
                                if(tries == 3):
                                        print(e)
                                        print("Socket timed out. Server may have crashed.")
                                        return
                #calculate timeouts
                sock.settimeout(2)
                sockRecv.settimeout(2)
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
                self.clientSock = sock
                self.clientSockRecv = sockRecv
                #self.send(sock,sockRecv)
                #receive(sockRecv)
                #Now we can start the true connection
                #################################################

        def sendPackets(self):
            if self.clientSock is None or self.clientSockRecv is None:
                print("client is not yet connected")
                return
            self.send(self.clientSock, self.clientSockRecv)

        def send(self,sock,sockRecv):
                finished = False
                if(int(self.winSize) > len(self.packets)):
                        window = RTPWindow(0,len(self.packets) - 1,len(self.packets))
                else:
                        window = RTPWindow(0,int(self.winSize) - 1,len(self.packets))
                numPacks = 0
                #make sure that we are indexing the right amount
                tries = 0
                while(not finished):
                        #go back N implementation
                        for i in range(window.getMin(),window.getMax() + 1):
                                p = self.packets[i]
                                #calc checksum and seq number of all the packets
                                p.getHeader().setChecksum(self.checksum(p))
                                ##########print("Sending: ",p.getHeader().getseqNum())
                                #print(p.getHeader().getseqNum())
                                #send each packet (convert to bytes)
                                sock.sendto(p.toByteStream(),(self.host,int(self.portNum)))
                                #start timer in the window
                        #receive the acked packets one at a time, make sure everything is correct
                        wait = True
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
                                                if(len(self.packets) - 1 == packet.getHeader().getseqNum()): 
                                                        print("Finished!")
                                                        finished = True
                                                        ting = 0
                                                        while(True):
                                                        #send a fin bit
                                                                h = RTPHeader(0,0,0,0)
                                                                h.setFIN(True)
                                                                p = RTPPacket(h,bytearray(0))
                                                                sock.sendto(p.toByteStream(),(self.host,int(self.portNum)))
                                                                try:
                                                                        packet = sockRecv.recvfrom(1028)
                                                                        #check if packet is data packet or ACK/NACK etc
                                                                        packet = self.streamToPacket(packet[0])
                                        #if packet has data we go into receive state. Otherwise we go back to send state
                                                                        if(packet.getHeader().getACK() is True):
                                                                                break
                                                                except Exception as e:
                                                                        print("Timedout, resending")
                                                                        ting += 1
                                                                        if(ting == 3):
                                                                                print("Server may have crashed")
                                                                                return
                                                wait = self.updateWindowSend(packet,window)
                                                tries = 0
                                        else:
                                                print("Timedout... Resending...")
                                                break
                                #packet received, check if we need to move the window or not
                                #good packets will now be converted into data, and updated properly
                                except Exception as e:
                                        print(e)
                                        print("Timedout, resending")
                                        tries += 1
                                        if(tries == 3):
                                                print("Server may have crashed")
                                                return
                                        break
                print("Receiving...")
                self.empty_socket(sockRecv)
                self.receive(sock,sockRecv)
        
        def receive(self,sock,sockRecv):
                if(len(self.packets) < int(self.winSize)):
                        self.winSize = len(self.packets)
                received = {}
                finished = False
                expectedSeqNum = 0
                tries = 0
                while(not finished):
                        rewait = False
                        try:
                                #self.empty_socket(sockRecv)
                                p,addr = sockRecv.recvfrom(1028)
                        except Exception as e:
                                tries += 1
                                if(tries == 5):
                                        print(e)
                                        print("Socket timed out. Server may have crashed.")
                                        return
                                else:
                                        rewait = True
                                #check if packet is data packet or ACK/NACK etc
                                #send ACK
                        if(not rewait):
                                packet = self.streamToPacket(p)
                                ##########print("Received packet: ", packet.getHeader().getseqNum())
                                if(packet.getHeader().getFIN()):
                                        print("Got fin bit!")
                                        finished = True;
                                        break
                                if(expectedSeqNum == packet.getHeader().getseqNum()):
                                        ans = self.checkPackets(packet,expectedSeqNum,received,sock)
                                        received = ans[0]
                                        expectedSeqNum = ans[1]
                                h = RTPHeader(0,0,0,0)
                                h.setACK(True)
                                if(expectedSeqNum != 0):
                                        h.setseqNum(int(expectedSeqNum - 1))
                                        ############print("Sending ACK FOR: ",packet.getHeader().getseqNum())
                                        sock.sendto(RTPPacket(h,bytearray(0)).toByteStream(),(self.host,int(self.portNum)))
                        #This function will check what to do with expected
                print("Turning into file...")
                received = sorted(list(received.values()),key=lambda x:x.getHeader().getseqNum())
                self.writeToFile(received)
                ##TODO SET UP CASE TO SEE IF WE ARE DONE##
                self.packets = []
                #self.disconnect(sock,sockRecv)

        def checkPackets(self,packet,expectedSeqNum,received,sock):
                expected = packet.getHeader().getChecksum()
                        #print("A: ",a)
                        #print("Expected: ",packet.getHeader().getseqNum())
                if(self.checkCorrupt(expected,packet) or packet.getHeader().getseqNum() in list(received.keys())): #drops duplicates
                        #packet is not accepted
                        return [received,expectedSeqNum]
                else:
                        received[packet.getHeader().getseqNum()] = packet
                        ###########print("Window Accepted")
                        expectedSeqNum += 1
                        return [received, expectedSeqNum]
        
        def disconnectSockets(self):
            self.disconnect(self.clientSock, self.clientSockRecv)

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
                                #self.empty_socket(sock)
                #close sockets and end connection
                print("Closed connection...")
                sock.close()
                sockr.close()

        #moves your send window
        def updateWindowSend(self,packet,window):
                curSeq = packet.getHeader().getseqNum()
                print("Acked: ", curSeq)
                if(window.getMin() <= curSeq):
                        window.setMin(curSeq);
                        window.setMax(window.getMin() + int(self.winSize))
                        if(window.getMax() >= len(self.packets)):
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
                #difference = ei - pi
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


def userInputThread():
    while True:
        print(">")
        command = input()
        queue(command)


def commandProcessThread(host, port, windowSize):
    global networkClient
    networkClient = RTPClient(host, port, windowSize)
    process = True
    while process:
        command = dequeue()
        if command is not None:
            if command.startswith("transform"):
                if not command.contains(" "):
                    print("Incorrect transform syntax")
                    continue
                targetFile = command.split(" ")[1]
                print("Transforming " + targetFile)
                networkClient.transform(targetFile)
                networkClient.sendPackets()
            elif command.startswith("disconnect"):
                networkClient.disconnectSockets()
                process = False
            else:
                print("Command not recognized")
                continue


def startThreads():
    thread.start_new_thread(userInputThread)
    thread.start_new_thread(commandProcessThread)

if __name__ == "__main__":
        # c = RTPClient(sys.argv[1],sys.argv[2],sys.argv[3])
        # c.transform("tests.txt")
        # c.connect()
        # c.sendPackets()
        startThreads()

