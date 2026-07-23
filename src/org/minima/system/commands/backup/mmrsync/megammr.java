package org.minima.system.commands.backup.mmrsync;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;

import org.minima.database.MinimaDB;
import org.minima.database.cascade.Cascade;
import org.minima.database.txpowtree.TxPowTree;
import org.minima.objects.Coin;
import org.minima.objects.CoinProof;
import org.minima.objects.IBD;
import org.minima.objects.TxBlock;
import org.minima.objects.TxPoW;
import org.minima.objects.base.MiniByte;
import org.minima.objects.base.MiniData;
import org.minima.objects.base.MiniNumber;
import org.minima.objects.mmr.MMR;
import org.minima.objects.mmr.MMRData;
import org.minima.objects.mmr.MMRProof;
import org.minima.objects.mmr.MegaMMR;
import org.minima.system.Main;
import org.minima.system.commands.Command;
import org.minima.system.commands.CommandException;
import org.minima.system.commands.CommandRunner;
import org.minima.system.params.GeneralParams;
import org.minima.utils.MiniFile;
import org.minima.utils.MiniFormat;
import org.minima.utils.MiniUtil;
import org.minima.utils.MinimaLogger;
import org.minima.utils.json.JSONObject;

public class megammr extends Command {

	public megammr() {
		super("megammr","(action:) (file:) - Get Info on or Import / Export the MegaMMR data");
	}
	
	@Override
	public String getFullHelp() {
		return "\nmegammr\n"
				+ "\n"
				+ "View information about your MegaMMR. Export and Import complete MegaMMR data.\n"
				+ "\n"
				+ "You must be running -megammr.\n"
				+ "\n"
				+ "action: (optional)\n"
				+ "    info   : Shows info about your MegaMMR.\n"
				+ "    export : Export a MegaMMR data file.\n"
				+ "    import : Import a MegaMMR data file.\n"
				+ "\n"
				+ "file: (optional)\n"
				+ "    Use with export and import.\n"
				+ "\n"
				+ "Examples:\n"
				+ "\n"
				+ "megammr\n"
				+ "\n"
				+ "megammr action:export\n"
				+ "\n"
				+ "megammr action:export file:thefile\n"
				+ "\n"
				+ "megammr action:import file:thefile\n"
				;
	}
	
	@Override
	public ArrayList<String> getValidParams(){
		return new ArrayList<>(Arrays.asList(new String[]{"action","file"}));
	}
	
	@Override
	public JSONObject runCommand() throws Exception{
		JSONObject ret = getJSONReply();

		String action = getParam("action","info");
		
		MegaMMR megammr = MinimaDB.getDB().getMegaMMR();
		
		if(action.equals("info")) {
		
			JSONObject resp = new JSONObject();
			resp.put("enabled", GeneralParams.IS_MEGAMMR);
			resp.put("mmr", megammr.getMMR().toJSON(false));
			resp.put("coins", megammr.getAllCoins().size());
			
			//Put the details in the response..
			ret.put("response", resp);
		
		}else if(action.equals("export")) {
			
			if(!GeneralParams.IS_MEGAMMR) {
				throw new CommandException("MegaMMR not enabled");
			}
			
			//Get the file
			String file = getParam("file","");
			if(file.equals("")) {
				//file = "megammr-backup-"+System.currentTimeMillis()+".bak";
				file = "megammr_"+MiniUtil.DATEFORMAT.format(new Date())+".megammr";
			}
			
			//Create the file
			File backupfile = MiniFile.createBaseFile(file);
			
			//get the MMR and IBD..
			if(backupfile.exists()) {
				backupfile.delete();
			}
			backupfile.createNewFile();
			
			IBD ibd = new IBD();
			
			//Lock the DB for read access..
			MinimaDB.getDB().readLock(true);
			
			try {
				
				//Create an IBD
				ibd.createCompleteIBD();
				
				MegaMMRBackup mmrbackup = new MegaMMRBackup(megammr, ibd);
				
				//Now write to it..
				FileOutputStream fos 		= new FileOutputStream(backupfile);
				BufferedOutputStream bos 	= new BufferedOutputStream(fos, 65536);
				DataOutputStream fdos 		= new DataOutputStream(bos);
				
				//And write it..
				mmrbackup.writeDataStream(fdos);
				
				//flush
				fdos.flush();
				bos.flush();
				fos.flush();
				
				fdos.close();
				bos.close();
				fos.close();
				
			}catch(Exception exc) {
				
				//Unlock DB
				MinimaDB.getDB().readLock(false);
				
				throw new CommandException(exc.toString());
			}
			
			//Unlock DB
			MinimaDB.getDB().readLock(false);
			
			JSONObject resp = new JSONObject();
			resp.put("megammrtip", megammr.getMMR().getBlockTime());
			resp.put("ibdtip", ibd.getTreeTip());
			resp.put("backup", backupfile.getAbsolutePath());
			resp.put("size", MiniFormat.formatSize(backupfile.length()));
			
			//Put the details in the response..
			ret.put("response", resp);
		
		}else if(action.equals("import")) {
			
			if(!GeneralParams.IS_MEGAMMR) {
				throw new CommandException("MegaMMR not enabled");
			}
			
			String file = getParam("file","");
			if(file.equals("")) {
				throw new CommandException("MUST specify a file to restore from");
			}
			
			//Does it exist..
			File restorefile = MiniFile.createBaseFile(file);
			if(!restorefile.exists()) {
				throw new CommandException("Restore file doesn't exist : "+restorefile.getAbsolutePath());
			}
			
			//Pre-flight - can this device hold the STREAMED working set at all..
			//Since the fork's streaming import (below) the peak is only:
			//  the MegaMMR object graph (which a running -megammr node holds anyway)
			//  + the cascade (~10-20MB worst case) + ONE batch of 256 TxBlocks
			//- NOT the whole decoded MegaMMR+IBD graph the upstream import needed
			//(several x the file size; it OOM-killed phone imports uncatchably).
			//The file length is only an upper bound on the MegaMMR portion, so this
			//gate is deliberately loose - the catch(Throwable) below is the real net.
			long filelen = restorefile.length();
			Runtime rt   = Runtime.getRuntime();
			long usedmem = rt.totalMemory() - rt.freeMemory();
			long freemem = rt.maxMemory() - usedmem;
			MinimaLogger.log("MegaMMR import pre-flight.. file:"+MiniFormat.formatSize(filelen)
					+" heapfree:"+MiniFormat.formatSize(freemem)
					+" heapmax:"+MiniFormat.formatSize(rt.maxMemory()));
			if(filelen > freemem) {
				throw new CommandException("MegaMMR file too large for this device's memory.. file "
						+MiniFormat.formatSize(filelen)+" exceeds the "
						+MiniFormat.formatSize(freemem)+" free heap even before decoding");
			}
			if(filelen * 2 > freemem) {
				MinimaLogger.log("WARNING: MegaMMR file is large relative to free heap - "
						+"import will abort cleanly if the decoded MegaMMR does not fit");
			}

			//STREAMING IMPORT (fork change - upstream loaded the ENTIRE MegaMMRBackup,
			//i.e. MegaMMR + every IBD TxBlock, into RAM at once).
			//File format (MegaMMRBackup.writeDataStream):
			//  [MiniNumber version] [MegaMMR] [IBD: cascade-flag byte, cascade?, count, TxBlocks...]
			//We read it in that order and dispatch the TxBlocks to the TxPoWProcessor in
			//BATCHES so the full block list never exists in memory:
			//  - batch #1 carries the Cascade (processor installs it because the tree/cascade
			//    were just reset); later batches carry none and attach to the retained tip
			//  - restore=true on EVERY postProcessIBD call (archiveResetReady sets mRestoring,
			//    which would otherwise silently drop the message) and we poll
			//    isIBDProcessFinished() between batches (the flag is reset per call)
			//  - resetFirstIBDTimer() defuses the processor's 5-min/3-hour "chain tip up to
			//    date" gate that would otherwise silently drop batches 2..K for recent chains
			DataInputStream dis = null;
			try {
				dis = new DataInputStream(new BufferedInputStream(new FileInputStream(restorefile), 65536));

				//Backup format version (currently 1)
				MiniNumber version = MiniNumber.ReadFromStream(dis);

				//Read the FULL MegaMMR - this part must fit in RAM (a running -megammr
				//node holds the same structure live, so if the node can run megammr at
				//all, this fits). Progress is logged every 250k coins (Logs tab).
				MinimaLogger.log("Loading MegaMMR portion.. (file "+MiniFormat.formatSize(filelen)+")");
				MegaMMR loadedmega = new MegaMMR();
				loadedmega.readDataStream(dis);
				MinimaLogger.log("MegaMMR loaded.. coins:"+loadedmega.getAllCoins().size());

				//Reset chain state ONCE, before any IBD batch - wipes archive/txpow DBs,
				//cascade and tree so batch #1's cascade installs and its first block
				//becomes the tree root. NEVER call this between batches.
				Main.getInstance().archiveResetReady(false);

				//Install the imported MegaMMR as the live one BEFORE the IBD is processed -
				//recalculateTree() feeds cascading blocks into it via megammr.addBlock
				MinimaDB.getDB().getMegaMMR().clear();
				MinimaDB.getDB().hardSetMegaMMR(loadedmega);

				//Defuse the "chain tip up to date" gate (see comment block above)
				Main.getInstance().getTxPoWProcessor().resetFirstIBDTimer();

				//IBD header: cascade flag [+ cascade], then the total block count
				Cascade cascade = null;
				if(MiniByte.ReadFromStream(dis).isTrue()) {
					cascade = new Cascade();
					cascade.readDataStream(dis);
				}
				int totalblocks = MiniNumber.ReadFromStream(dis).getAsInt();
				MinimaLogger.log("Streaming IBD.. blocks:"+totalblocks
						+(cascade!=null ? " (with cascade)" : " (no cascade)"));

				//256 matches the processor's own internal recalculateTree cadence
				int BATCH_SIZE  = 256;
				int blocksdone  = 0;
				boolean first   = true;
				while(blocksdone < totalblocks) {

					//Build one small batch - blocks MUST stay contiguous and in file
					//order or processSyncBlock throws 'Invalid SyncBlock as NO PARENT!'
					IBD batch = new IBD();
					if(first && cascade != null) {
						batch.setCascade(cascade);
					}
					int n = Math.min(BATCH_SIZE, totalblocks - blocksdone);
					for(int i=0;i<n;i++) {
						batch.getTxBlocks().add(TxBlock.ReadFromStream(dis));
					}
					blocksdone += n;

					//Dispatch to the processor thread (the ONLY safe way in - the
					//per-block processSyncBlock is private and not thread-safe) and
					//wait for it to finish before reading the next batch, so at most
					//one batch is in memory at a time
					Main.getInstance().getTxPoWProcessor().postProcessIBD(batch, "0x00", true);
					while(!Main.getInstance().getTxPoWProcessor().isIBDProcessFinished()) {
						Thread.sleep(100);
					}

					first = false;

					//Progress every ~10 batches (visible live in the Logs tab)
					if((blocksdone % (BATCH_SIZE*10)) == 0 || blocksdone == totalblocks) {
						MinimaLogger.log("IBD blocks processed.. "+blocksdone+"/"+totalblocks);
					}
				}

				//The cascade is installed in the DB now - drop our reference
				cascade = null;

				dis.close();
				dis = null;

			}catch(Throwable exc) {
				//Throwable so an OutOfMemoryError (an Error - a plain catch(Exception)
				//let it KILL the whole process) reports cleanly instead. A truncated
				//file surfaces here too as an EOFException from the streamed reads.
				//NB the chain DBs may be part-imported at this point - restart + retry.
				if(dis != null) {
					try{ dis.close(); }catch(Exception ignore) {}
				}
				System.gc();
				throw new CommandException("MegaMMR import failed : "+exc
						+" .. RESTART Minima before retrying");
			}

			try {

			//Quick clean
			MinimaLogger.log("System memory clean..");
			System.gc();
			
			//Get the tree
			TxPowTree tree = MinimaDB.getDB().getTxPoWTree();
			TxPoW topblock = tree.getTip().getTxPoW();
			MinimaLogger.log("Current Top Block : "+topblock.getBlockNumber());
			
			//Now check..
			TxPoW rootblock = tree.getRoot().getTxPoW();
			MinimaLogger.log("Current Tree Root : "+rootblock.getBlockNumber());
			
			//And the Mega MMR
			MegaMMR currentmega = MinimaDB.getDB().getMegaMMR();
			MinimaLogger.log("Current MegaMMR Tip : "+currentmega.getMMR().getBlockTime());
			
			//Get all your coin proofs..
			MinimaLogger.log("Get all your CoinProofs");
			MegaMMRSyncData mydata 		 = megammrsync.getMyDetails();
			ArrayList<CoinProof> cproofs = megammrsync.getAllCoinProofs(mydata);
			
			//Import all YOUR coin proofs..
			MinimaLogger.log("Transfer your CoinProofs.. "+cproofs.size());
			for(CoinProof cp : cproofs) {
				
				//Convert to MiniData..
				MiniData cpdata = MiniData.getMiniDataVersion(cp);
				
				//Coin Import..
				JSONObject coinproofresp = CommandRunner.getRunner().runSingleCommand("coinimport track:true data:"+cpdata.to0xString());
				
//				if(!(boolean)coinproofresp.get("status")) {
//					MinimaLogger.log("Fail Import : "+coinproofresp.getString("error")+" @ "+cp.toJSON());
//				}
			}

			}catch(Throwable exc) {
				//Throwable so an OutOfMemoryError mid-import reports instead of
				//killing the process - the DB may be part-imported at this point
				System.gc();
				throw new CommandException("MegaMMR import failed mid-import : "+exc
						+" .. RESTART Minima before retrying");
			}

			JSONObject resp = new JSONObject();
			resp.put("message", "MegaMMR import finished.. please restart");
			ret.put("response", resp);
			
			//Don't do the usual shutdown hook
			Main.getInstance().setHasShutDown();
			
			//And NOW shut down..
			Main.getInstance().shutdownFinalProcs();
			
			//Now shutdown and save everything
			MinimaDB.getDB().saveAllDB();
			
			//And NOW shut down..
			Main.getInstance().stopMessageProcessor();
			
			//Tell listener..
			Main.getInstance().NotifyMainListenerOfShutDown();
		
		}else if(action.equals("integrity")) {
			
			String file = getParam("file");
			
			//Does it exist..
			File restorefile = MiniFile.createBaseFile(file);
			if(!restorefile.exists()) {
				throw new CommandException("MegaMMR file doesn't exist : "+restorefile.getAbsolutePath());
			}
			
			//Load it in..
			MegaMMRBackup mmrback = new MegaMMRBackup();
			
			MinimaLogger.log("Load MegaMMR.. "+MiniFormat.formatSize(restorefile.length()));
			MiniFile.loadObjectSlow(restorefile, mmrback);
			
			BigInteger weight = checkMegaMMR(mmrback);
			
			IBD ibd 			= mmrback.getIBD();
			TxPoW cascade 		= ibd.getCascade().getTip().getTxPoW();
			MiniNumber casctip 	= cascade.getBlockNumber();
			int casclen 		= ibd.getTxBlocks().size();
			MiniNumber chaintip	= casctip.add(new MiniNumber(casclen));
			
			JSONObject resp = new JSONObject();
			resp.put("cascadetip", casctip);
			resp.put("cascadedate", new Date(cascade.getTimeMilli().getAsLong()).toString());
			resp.put("chaintip", chaintip);
			resp.put("weight", weight.toString());
			ret.put("response", resp);
		}
		
		return ret;
	}
	
	@Override
	public Command getFunction() {
		return new megammr();
	}

	public static BigInteger checkMegaMMR(File zMegaMMR) throws CommandException{
		//Load it in..
		MegaMMRBackup mmrback = new MegaMMRBackup();
		
		MinimaLogger.log("Load MegaMMR.. "+MiniFormat.formatSize(zMegaMMR.length()));
		MiniFile.loadObjectSlow(zMegaMMR, mmrback);
	
		return checkMegaMMR(mmrback);
	}
	
	public static BigInteger checkMegaMMR(MegaMMRBackup mmrback) throws CommandException{
		
		//Get the mmr
		MegaMMR mega 	= mmrback.getMegaMMR();
		MMR mmr 		= mmrback.getMegaMMR().getMMR();
		
		//Check the IBD
		IBD ibd = mmrback.getIBD();
		MinimaLogger.log("Check IBD..");
		boolean validibd = ibd.checkValidData();
		if(!validibd) {
			throw new CommandException("Invalid IBD");
		}
		
		//Check start and end.. This is where the MEGA MMR finishes..
		MiniNumber lastblock = mmr.getBlockTime();
		
		//Load the IBD into the MMR..
		ArrayList<TxBlock> blocks = mmrback.getIBD().getTxBlocks();
		for(TxBlock block : blocks) {
			
			//Check is the next in line.. 
			MiniNumber blknum = block.getTxPoW().getBlockNumber(); 
			if(!blknum.isEqual(lastblock.increment())) {
				throw new CommandException("Invalid block number.. not incremental.. last_in_mega:"+lastblock+" new_block:"+blknum);
			}
			
			//Store for later
			lastblock = blknum;
			
			//Add to the MegaMMR..
			mega.addBlock(block);
		}
		
		//You can finalize as no more being added
		mmr.finalizeSet();
		
		MinimaLogger.log("Now check all coin proofs..");
		
		//Now check integrity
		Hashtable<String,Coin> allcoins = mmrback.getMegaMMR().getAllCoins();
		int size = allcoins.size();
		
		Collection<Coin> coincollection = allcoins.values();
		Iterator<Coin> coiniterator = coincollection.iterator();
		
		int maxcheck = 0;
		while(coiniterator.hasNext()) {
			Coin coin = coiniterator.next();
			
			//Create the MMRData Leaf Node..
			MMRData mmrdata 	= MMRData.CreateMMRDataLeafNode(coin, coin.getAmount());
			MMRProof mmrproof 	= null;
			try {
				
				//Get the proof..
				mmrproof = mmr.getProof(coin.getMMREntryNumber());
			
			}catch(Exception exc) {
				throw new CommandException("Error chcking coin @ "+coin.toJSON()+" "+exc);
			}
			
			//Now check the proof..
			boolean valid = mmr.checkProofTimeValid(coin.getMMREntryNumber(), mmrdata, mmrproof);
			
			if(!valid) {
				throw new CommandException("INVALID Coin proof! @ "+coin.toJSON().toString());
			}
			
			maxcheck++;
			if(maxcheck % 5000 == 0) {
				MinimaLogger.log("Checking coins @ "+maxcheck+" / "+size);
			}
		}
		
		MinimaLogger.log("All coins checked "+maxcheck+" / "+size);
		
		return ibd.getTotalWeight();
	}
	
	public static void main(String[] zArgs) throws Exception {
		checkMegaMMR(new File("./bin/minima_megammr.mmr"));
	}
}
