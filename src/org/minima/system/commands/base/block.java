package org.minima.system.commands.base;

import java.util.Date;

import org.minima.database.MinimaDB;
import org.minima.database.txpowtree.TxPoWTreeNode;
import org.minima.database.txpowtree.TxPowTree;
import org.minima.database.userprefs.UserDB;
import org.minima.objects.base.MiniNumber;
import org.minima.system.commands.Command;
import org.minima.system.commands.CommandException;
import org.minima.utils.MinimaLogger;
import org.minima.utils.json.JSONObject;

public class block extends Command {

	public block() {
		super("block","Simply return the current top block");
	}
	
	@Override
	public String getFullHelp() {
		return "\nblock\n"
				+ "\n"
				+ "Return the top block\n"
				+ "\n"
				+ "Examples:\n"
				+ "\n"
				+ "block\n";
	}
	
	@Override
	public JSONObject runCommand() throws Exception {
		JSONObject ret = getJSONReply();
		
		//Get the top block..
		TxPowTree tree 		= MinimaDB.getDB().getTxPoWTree();
		TxPoWTreeNode tip 	= tree.getTip();
		if(tip == null) {
			throw new CommandException("NO Blocks yet..");
		}
				
		JSONObject resp = new JSONObject();
		resp.put("block", tip.getBlockNumber().toString());
		resp.put("hash", tip.getTxPoWID());
		resp.put("timemilli", tip.getTimeMilli().toString());
		resp.put("date", new Date(tip.getTimeMilli().getAsLong()).toString());
		
		ret.put("response", resp);
		
		return ret;
	}


	/**
	 * IF you are using BLOCK as KEY USES.. 
	 */
	public synchronized static MiniNumber getCurrentBlockAsKeyUses(int zTreeKeyUses) {
		
		//Get the UserDB
		UserDB udb 			= MinimaDB.getDB().getUserDB();
		
		//The Stored Value..
		MiniNumber lastused =  udb.getLastBlockAsKeyUses();
		
		//Get the top block..
		TxPowTree tree 		= MinimaDB.getDB().getTxPoWTree();
		TxPoWTreeNode tip 	= tree.getTip();
		if(tip == null) {
			throw new IllegalArgumentException("NO BLOCKS FOUND FOR BLOCK AS KEY USES!");
		}
		
		MiniNumber topblock =  tip.getBlockNumber();
		
		//Which is HIGHER ?
		MiniNumber higher;
		if(topblock.isMore(lastused)) {
			higher = topblock;
		}else {
			higher = lastused.increment();
		}
		
		//Now DOUBLE CHECK against what the db says..
		MiniNumber treekey = new MiniNumber(zTreeKeyUses);
		if(treekey.isMore(higher)) {
			
			//This should NOT happen
			MinimaLogger.log("[!] BLOCK AS KEY USES - TreeKey DB has HIGHER VALUE!? "+zTreeKeyUses);
			
			//Increment just in case
			higher = treekey.increment();
		}
		
		//SET THIS so NEVER used again.. 
		udb.setLastBlockAsKeyUses(higher);
		
		MinimaLogger.log("BLOCK AS KEYUSES TreeKeyDB:"+zTreeKeyUses+" topblock:"+topblock+" lastused:"+lastused+" higher:"+higher);
		
		return higher;
	}
	
	@Override
	public Command getFunction() {
		return new block();
	}

}
