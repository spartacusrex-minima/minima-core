package org.minima.database.archive;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import org.minima.objects.TxBlock;
import org.minima.objects.base.MiniData;
import org.minima.objects.base.MiniNumber;
import org.minima.utils.MinimaLogger;
import org.minima.utils.SqlDB;

public class TxBlockDB extends SqlDB {
	
	PreparedStatement SQL_INSERT_SYNCBLOCK 		= null;
	PreparedStatement SQL_FIND_SYNCBLOCK 		= null;
	PreparedStatement SQL_FIND_CHILDREN 		= null;
	
	TxBlock mLastGetBlock = null;
	TxBlock mLastAddBlock = null;
	
	public TxBlockDB() {
		super();
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
	}
	
	public synchronized void addTxBlock(TxBlock zTxBlock) {
		
		//Nice optimisation
		if(mLastAddBlock != null) {
			if(mLastAddBlock.getTxPoW().getTxPoWID().equals(zTxBlock.getTxPoW().getTxPoWID())) {
				//Allready added!
				return;
			}
		}
		
		//Store for later
		mLastAddBlock = zTxBlock;
		
		MinimaLogger.log("TxBlockDB AddBlock "+zTxBlock.getTxPoW().getBlockNumber()+" "+zTxBlock.getTxPoW().getTxPoWID());
		
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
		
		try{
			throw new Exception("STACK");
		}catch (Exception e) {
			e.printStackTrace();
		}
		
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
		
				MinimaLogger.log("TxBlockDB GetBlock "+zTxPoWID+" "+mLastGetBlock.getTxPoW().getBlockNumber());
				
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
	
	public synchronized void clearOld(MiniNumber zMinBlock) {
		
	}
	
}
