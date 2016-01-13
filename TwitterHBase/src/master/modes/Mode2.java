package master.modes;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.RegexStringComparator;
import org.apache.hadoop.hbase.filter.RowFilter;
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp;
import org.apache.hadoop.hbase.util.Bytes;

import master.FileLog;
import master.KeyGenerator;
import master.RankTable;
import master.structures.HashtagRank;
import master.structures.HashtagRankEntry;

public class Mode2 {

	/**
	 * Do find the list of Top-N most used words for each language in 
	 * a time interval defined with the provided start and end timestamp.
	 * Start and end timestamp are in milliseconds.
	 * @param args mode startTS endTS N [lang1,lang2,lang3...] outputFolder
	 */
	public static void execute(String[] args) {
		if(args.length != 6) {
			System.err.println("Invalid arguments for mode 1");
			return;
		}
		
		Long startTs = Long.parseLong(args[1]);
		Long endTs = Long.parseLong(args[2]);
		int rankSize = Integer.parseInt(args[3]);
		String logPath = (args[6].endsWith(File.separator) ? args[6] : args[6] + File.separator);
		
		//Get list of languages
		List<String> languages = new ArrayList<String>();
		String[] langs;
		if(args[4].contains(",")){
			langs = args[4].split(",");
			for(String lang:langs){
				if(lang.length() != 2) {
					System.err.println("Lang parameter should have two characters.");
					return;
				} else{
					languages.add(lang.toLowerCase());
				}
			}
		} else {
			if(args[4].length() != 2) {
				System.err.println("Lang parameter should have two characters.");
				return;
			} else {
				languages.add(args[4].toLowerCase());
			}
		}
		
		// Open Rank HBase table
		HTable table;
		try {
			table = RankTable.open();
		} catch (IOException e) {
			System.err.println("Could not open HBase Rank table!");
			e.printStackTrace();
			return;
		}

		// Calculate start and end keys
		byte[] startKey = KeyGenerator.generateKey(startTs);
		byte[] endKey = KeyGenerator.generateEndKey(endTs);

		Scan scan = new Scan(startKey, endKey);
		
		// Add filter by lang
		String regExp = "";
		for(int i = 0; i<languages.size(); i++){
			regExp = (i == languages.size()-1) ? regExp + languages.get(i) +"$" : 
				regExp + languages.get(i) + "$|";
		}
		RegexStringComparator endsWithLang = new RegexStringComparator(regExp);
		RowFilter langFilter = new RowFilter(CompareOp.EQUAL, endsWithLang);
		scan.setFilter(langFilter);
		
		try {
			ResultScanner rs = table.getScanner(scan);
			
			// Create rank objects.
			HashMap<String, HashtagRank> rankings = new HashMap<String, HashtagRank>();
			HashtagRank langRank;
			for(String lang : languages){
				langRank = new HashtagRank();
				rankings.put(lang, langRank);
			}
			
			Result res = rs.next();
			while (res != null && !res.isEmpty()) {
				
				// Process each result adding its containing entries to the rank
				addEntryToRankFromResult(rankings, res);
				
				// Next result
				res = rs.next();
			}
			
			// Log results
			FileLog logger = new FileLog(logPath + "02_query2.out");
			for(HashtagRank rank : rankings.values()){
				logger.writeToFile(rank.getBestN(rankSize), startTs, endTs);
			}
			logger.cleanup();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// Close the table
		RankTable.close();		
	}
	
	public static void addEntryToRankFromResult(HashMap<String, HashtagRank> rankings, Result res) {
		try {
			
			for(int x = 1; x <= 3; x++) {
				
				Field hashtagColumnField = RankTable.class.getField(RankTable.HASHTAG_COLUMN_PREFIX + x);
				Field countColumnField = RankTable.class.getField(RankTable.COUNT_COLUMN_PREFIX + x);
				
				byte[] hashtagColumn = (byte[]) hashtagColumnField.get(null);
				byte[] countColumn = (byte[]) countColumnField.get(null);
				
				byte[] hashtagRaw = res.getValue(RankTable.CF_HASHTAGS, hashtagColumn);
				byte[] countRaw = res.getValue(RankTable.CF_COUNTS, countColumn);
				
				String key = Bytes.toString(res.getRow());
				String lang = key.substring(key.length()-2);
				
				if(hashtagRaw != null && countRaw != null) {
					rankings.get(lang).add(new HashtagRankEntry(lang, Bytes.toString(hashtagRaw), Bytes.toInt(countRaw)));
				}
				
			}
			
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
				
	}

}
