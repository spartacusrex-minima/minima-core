package org.minima.database.archive;

import java.awt.geom.GeneralPath;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;

import org.minima.objects.TxBlock;
import org.minima.objects.base.MiniData;
import org.minima.objects.base.MiniNumber;
import org.minima.system.params.GeneralParams;
import org.minima.utils.MiniUtil;
import org.minima.utils.MinimaLogger;
import org.minima.utils.SqlDB;

public class TxBlockDB extends SqlDB {
	
	PreparedStatement SQL_INSERT_SYNCBLOCK 		= null;
	PreparedStatement SQL_FIND_SYNCBLOCK 		= null;
	PreparedStatement SQL_FIND_CHILDREN 		= null;
	PreparedStatement SQL_CLEAR_OLD 			= null;
	
	TxBlock mLastGetBlock = null;
	TxBlock mLastAddBlock = null;
	
	//For FULL RAM Lookup
	ConcurrentHashMap<String, TxBlock> mTxBlockDB;
	
	public TxBlockDB() {
		super();
		
		mTxBlockDB = new ConcurrentHashMap<>();
	}
	
	@Override
	protected void createSQL() throws SQLException {
		
		//Create the various tables..
		Statement stmt = mSQLConnection.createStatement();
		
		//Create main table
		String create = "CREATE TABLE IF NOT EXISTS `syncblock` ("
						+ "  `id` bigint auto_increment,"
						+ "  `txpowid` varchar(80) NOT NULL UNIQUE,"
						+ "  `parentid` varchar(80) NOT NULL,"
						+ "  `block` bigint NOT NULL,"
						+ "  `txblock` blob NOT NULL"
						+ ")";
		
		//Run it..
		stmt.execute(create);
		
		//All done..
		stmt.close();
		
		//Create some prepared statements..
		String insert 			= "INSERT IGNORE INTO syncblock ( txpowid, parentid, block, txblock ) VALUES ( ?, ?, ? ,? )";
		SQL_INSERT_SYNCBLOCK 	= mSQLConnection.prepareStatement(insert);
		SQL_FIND_SYNCBLOCK 		= mSQLConnection.prepareStatement("SELECT txblock FROM syncblock WHERE txpowid=?");
		SQL_FIND_CHILDREN 		= mSQLConnection.prepareStatement("SELECT txblock FROM syncblock WHERE parentid=?");
		SQL_CLEAR_OLD			= mSQLConnection.prepareStatement("DELETE FROM syncblock WHERE block<?");
	}
	
	public synchronized void addTxBlock(TxBlock zTxBlock) {
		
		//FULL RAM MODE
		if(!GeneralParams.USE_SQL_TXBLOCKDB) {
			mTxBlockDB.put(zTxBlock.getTxPoW().getTxPoWID(), zTxBlock);
			return;
		}
		
		//Nice optimisation
		if(mLastAddBlock != null) {
			if(mLastAddBlock.getTxPoW().getTxPoWID().equals(zTxBlock.getTxPoW().getTxPoWID())) {
				//Allready added!
				return;
			}
		}
		
		//Store for later
		mLastAddBlock = zTxBlock;
		
		if(GeneralParams.LOG_SQL_COINTXBLOCKDB) {
			MinimaLogger.log("TxBlockDB AddBlock "+zTxBlock.getTxPoW().getBlockNumber()+" "+zTxBlock.getTxPoW().getTxPoWID());
		}
		
		try {
			//Make sure..
			checkOpen();
		
			//get the MiniData version..
			MiniData txblockdata = MiniData.getMiniDataVersion(zTxBlock);
			
			//Get the Query ready
			SQL_INSERT_SYNCBLOCK.clearParameters();
		
			//Set main params
			SQL_INSERT_SYNCBLOCK.setString(1, zTxBlock.getTxPoW().getTxPoWID());
			SQL_INSERT_SYNCBLOCK.setString(2, zTxBlock.getTxPoW().getParentID().to0xString());
			SQL_INSERT_SYNCBLOCK.setLong(3, zTxBlock.getTxPoW().getBlockNumber().getAsLong());
			SQL_INSERT_SYNCBLOCK.setBytes(4, txblockdata.getBytes());
			
			//Do it.
			SQL_INSERT_SYNCBLOCK.execute();
			
		}catch (SQLException e) {
			MinimaLogger.log(e);
		}		
	}
	
	public synchronized TxBlock getTxBlock(String zTxPoWID) {
		
		//FULL RAM MODE
		if(!GeneralParams.USE_SQL_TXBLOCKDB) {
			return mTxBlockDB.get(zTxPoWID);
		}
		
		//Nice optimisation
		if(mLastGetBlock != null) {
			if(mLastGetBlock.getTxPoW().getTxPoWID().equals(zTxPoWID)) {
				return mLastGetBlock;
			}
		}
		
		//Check last added
		if(mLastAddBlock != null) {
			if(mLastAddBlock.getTxPoW().getTxPoWID().equals(zTxPoWID)) {
				//Allready added!
				return mLastAddBlock;
			}
		}
		
		//MiniUtil.PrintStackTrace();
		
		try {
			
			//Make sure..
			checkOpen();
		
			//Set search params
			SQL_FIND_SYNCBLOCK.clearParameters();
			SQL_FIND_SYNCBLOCK.setString(1, zTxPoWID);
			
			//Run the query
			ResultSet rs = SQL_FIND_SYNCBLOCK.executeQuery();
			
			//Is there a valid result.. ?
			if(rs.next()) {
				
				//Get the details..
				byte[] syncdata 	= rs.getBytes("txblock");
				
				//Create MiniData version
				MiniData minisync = new MiniData(syncdata);
				
				//Convert
				TxBlock sb = TxBlock.convertMiniDataVersion(minisync);
				
				//SAVE IT
				mLastGetBlock = sb;
		
				if(GeneralParams.LOG_SQL_COINTXBLOCKDB) {
					MinimaLogger.log("TxBlockDB GetBlock "+zTxPoWID+" "+mLastGetBlock.getTxPoW().getBlockNumber());
				}
				
				
				return sb;
			}
			
		} catch (SQLException e) {
			MinimaLogger.log(e);
		}
		
		return null;
	}
	
	public synchronized ArrayList<TxBlock> getChildBlocks(String zTxPowID){
		
		MinimaLogger.log("TxBlockDB GetChildBlock "+zTxPowID);
		
		ArrayList<TxBlock> ret = new ArrayList<>();
		
		//FULL RAM MODE
		if(!GeneralParams.USE_SQL_TXBLOCKDB) {
			
			//Cycle through the blocks..
			Enumeration<TxBlock> allblocks = mTxBlockDB.elements();
			while(allblocks.hasMoreElements()) {
				
				TxBlock txblock = allblocks.nextElement();
				
				//Is it a child..
				if(txblock.getTxPoW().getParentID().to0xString().equals(zTxPowID)) {
					ret.add(txblock);
				}
			}
			
			return ret;
		}
		
		try {
			
			//Make sure..
			checkOpen();
		
			//Set search params
			SQL_FIND_CHILDREN.clearParameters();
			SQL_FIND_CHILDREN.setString(1, zTxPowID);
			
			//Run the query
			ResultSet rs = SQL_FIND_CHILDREN.executeQuery();
			
			//Is there a valid result.. ?
			while(rs.next()) {
				
				//Get the details..
				byte[] syncdata 	= rs.getBytes("txblock");
				
				//Create MiniData version
				MiniData minisync = new MiniData(syncdata);
				
				//Convert
				TxBlock sb = TxBlock.convertMiniDataVersion(minisync);
				
				ret.add(sb);
			}
			
		} catch (SQLException e) {
			MinimaLogger.log(e);
		}
		
		return ret;
	}
	
	public synchronized int clearOld(MiniNumber zMinBlock) {
		
		//FULL RAM MODE
		if(!GeneralParams.USE_SQL_TXBLOCKDB) {
			
			int oldsize = mTxBlockDB.size();
			
			ConcurrentHashMap<String, TxBlock> newDB = new ConcurrentHashMap();
			
			Enumeration<TxBlock> allblocks = mTxBlockDB.elements();
			while(allblocks.hasMoreElements()) {
				
				TxBlock txblock = allblocks.nextElement();
				
				if(txblock.getTxPoW().getBlockNumber().isMoreEqual(zMinBlock)) {
					newDB.put(txblock.getTxPoW().getTxPoWID(), txblock);
				}
			}
			
			mTxBlockDB = newDB;
			
			int newsize = mTxBlockDB.size();
			
			return oldsize-newsize;
		}
		
		try {
			
			//Make sure..
			checkOpen();
		
			//Set search params
			SQL_CLEAR_OLD.clearParameters();
			SQL_CLEAR_OLD.setLong(1, zMinBlock.getAsLong());
			
			//Run the query
			return SQL_CLEAR_OLD.executeUpdate();
			
		} catch (SQLException e) {
			MinimaLogger.log(e);
		}
		
		return 0;
	}
	
}
