package org.minima.system.commands.base;

import java.util.ArrayList;
import java.util.Arrays;

import org.minima.database.MinimaDB;
import org.minima.objects.Address;
import org.minima.objects.TxBlock;
import org.minima.objects.base.MiniData;
import org.minima.system.commands.Command;
import org.minima.utils.MinimaLogger;
import org.minima.utils.json.JSONObject;

public class test extends Command {

	public test() {
		super("test","test Funxtion");
	}
	
	@Override
	public ArrayList<String> getValidParams(){
		return new ArrayList<>(Arrays.asList(new String[]{"txpowid"}));
	}
	
	@Override
	public JSONObject runCommand() throws Exception {
		JSONObject ret = getJSONReply();
	
		MinimaLogger.log("Check Children..");
	
		String txpowid = getParam("txpowid");
		
		//Search for the children
		ArrayList<TxBlock> children = MinimaDB.getDB().getTxBlockDB().getChildBlocks(txpowid);
		
		MinimaLogger.log("Children found : "+children.size());
		for(TxBlock child : children) {
			MinimaLogger.log("Child : "+child.getTxPoW().getBlockNumber()+" "+child.getTxPoW().getTxPoWID());
		}
				
		return ret;
	}
	
	
		
	@Override
	public Command getFunction() {
		return new test();
	}

	public static void main(String[] zArgs) {
		
		System.out.println("Start test..");
		
		for(int i=0;i<100000;i++) {
			
			MiniData data = MiniData.getRandomData(32);
			
			String add = Address.makeMinimaAddress(data);
			int len = add.length(); 
			
			if(len != 63) {
				System.out.println(len+" "+add+" "+data.to0xString());
				//System.out.println("NOT 63! "+);
			}
			
		}
		
		System.out.println("Finish test..");
		
		
		
	}
}