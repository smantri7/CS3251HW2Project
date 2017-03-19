public class RTPWindow {
	private int min;
	private int max;
	private int numPackets;
	private boolean[] rList;

	public RTPWindow(int min, int max, int numPackets) {
		this.min = min;
		this.max = max;
		this.numPackets = numPackets;
		this.rList = rList;

		rList = new boolean[numPackets];
		for(int i = 0; i < numPackets; i++) {
			rList[i] = false;
		}
	}

	public int getMin() {
		return min;
	}

	public int getMax() {
		return max;
	}

	public boolean[] getrList() {
		return rList;
	}

	public void setMin(int val) {
		min = val;
	}

	public void setMax(int val) {
		max = val;
	}

	public void setrList(int pos, boolean val) {
		rList[pos] = val;
	}
}