package org.minima.objects;

import java.io.IOException;
import java.util.ArrayList;

import org.minima.database.MinimaDB;
import org.minima.database.cascade.Cascade;
import org.minima.database.txpowtree.TxPoWTreeNode;

public class IBDStore {

	Cascade mCascade = new Cascade();
	
	ArrayList<TxBlock> mTxBlocks = new ArrayList<>();
	
	long mTimeLastCreate = 0;
	long mWaitTime	 	 = 100000;
	
	public IBDStore() {}
	
	public synchronized void createComplete() throws IOException {
		
		//Do we need to create.. ?
		long timenow = System.currentTimeMillis();
		if(System.currentTimeMillis() < (mTimeLastCreate + mWaitTime) ) {
			return;
		}
		mTimeLastCreate = timenow;
		
		//First copy the current Cascade
		mCascade = MinimaDB.getDB().getCascade().deepCopy();
	
		//And now add all the blocks.. root will be first
		mTxBlocks = new ArrayList<>();
		TxPoWTreeNode tip = MinimaDB.getDB().getTxPoWTree().getTip();
		while(tip != null) {
			mTxBlocks.add(0,tip.getTxBlock());
			tip = tip.getParent();
		}
	}
	
	public synchronized void setComplete(IBD zIBD) {
		zIBD.setCascade(mCascade);
		zIBD.setTxBlocks(mTxBlocks);
	}
}
