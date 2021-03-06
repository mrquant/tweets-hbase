package master.structures;

import java.text.Collator;

public class HashtagRankEntry implements Comparable<HashtagRankEntry>{
	
	public String language;
	public String hashtag;
	public int count;
	
	
	public HashtagRankEntry(String language, String hashtag, int count) {
		this.language = language;
		this.hashtag = hashtag;
		this.count = count;
	}

	@Override
	/**
	 * Order in descending count and then alphabetic order
	 */
	public int compareTo(HashtagRankEntry o) {
		if(this.count != o.count) {
			return o.count - this.count;
		} else {
			Collator comp = Collator.getInstance();
			return comp.compare(this.hashtag, o.hashtag);
		}
	}
	
	@Override
	public boolean equals(Object obj) {
		
		if(!obj.getClass().equals(this.getClass())) {
			return false;
		}
		
		HashtagRankEntry o = (HashtagRankEntry) obj;
		
		return this.language.equals(o.language) && this.hashtag.equals(o.hashtag) && this.count == o.count;
	}
	
	@Override
	public String toString() {
		return "HashtagRankEntry<" + this.language + ", " + this.hashtag + ", " + this.count + ">";
	}

}
