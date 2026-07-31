package org.minima.system.commands.backup;

import java.util.ArrayList;
import java.util.Arrays;

import org.minima.system.Main;
import org.minima.system.commands.Command;
import org.minima.system.commands.CommandException;
import org.minima.system.commands.CommandRunner;
import org.minima.system.params.GeneralParams;
import org.minima.utils.json.JSONArray;
import org.minima.utils.json.JSONObject;
import org.minima.utils.messages.Message;

public class rescue extends Command {

	public rescue() {
		super("rescue","(host:) - Perform a megammrsync with the -rescuenode parameter if specified (or 'host' param)...");
	}
	
	@Override
	public ArrayList<String> getValidParams(){
		return new ArrayList<>(Arrays.asList(new String[]{"host"}));
	}
	
	@Override
	public JSONObject runCommand() throws Exception {
		JSONObject ret = getJSONReply();
		
		String host = "";
		if(existsParam("host")) {
			host = getParam("host");
		}else if(!GeneralParams.RESCUE_MEGAMMR_NODE.equals("")) {
			host = GeneralParams.RESCUE_MEGAMMR_NODE;
		}

		//Is there a valid host
		if(host.equals("")) {
			throw new CommandException("No valid rescue / megammr node specified..");
		}
		
		Message rescue = new Message(Main.MAIN_DO_RESCUE);
		rescue.addString("host", host);
		
		//Post a message..
		Main.getInstance().PostMessage(rescue);
		
		JSONObject resp = new JSONObject();
		resp.put("host", host);
		resp.put("message", "Rescue attempt started..");
		
		ret.put("response", resp);
				
		return ret;
	}
	
	@Override
	public Command getFunction() {
		return new rescue();
	}

}
