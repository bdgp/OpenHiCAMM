package org.bdgp.OpenHiCAMM;

public class Tag {
	private String tagName;
	private Attr[] arguments;
	private Block block;

	public static interface Attr {
		public Object arg(String key);
	}
	public static interface Block {
		public void arg();
	}
	
	// a lambda with 1 arg is an attribute pair
	// a lambda with no arguments is a nested block
	public Tag(String tagName, Attr[] args, Block block) {
		this.tagName = tagName;
		this.block = block;
		this.arguments = args;
	}
	public static Tag tag(String tagName) {return new Tag(tagName, new Attr[]{}, null);}
	public static Tag tag(String tagName, Attr... attrs) {return new Tag(tagName, attrs, null);}
	public static Tag tag(String tagName, Block block) {return new Tag(tagName, new Attr[]{}, null);}
	public static Tag tag(String tagName, Attr a1, Block block) {return new Tag(tagName, new Attr[]{a1}, block);}
	public static Tag tag(String tagName, Attr a1, Attr a2, Block block) {return new Tag(tagName, new Attr[]{a1, a2}, block);}
	public static Tag tag(String tagName, Attr a1, Attr a2, Attr a3, Block block) {return new Tag(tagName, new Attr[]{a1, a2, a3}, block);}
	public static Tag tag(String tagName, Attr a1, Attr a2, Attr a3, Attr a4, Block block) {return new Tag(tagName, new Attr[]{a1, a2, a3, a4}, block);}
	public static Tag tag(String tagName, Attr a1, Attr a2, Attr a3, Attr a4, Attr a5, Block block) {return new Tag(tagName, new Attr[]{a1, a2, a3, a4, a5}, block);}
	public static Tag tag(String tagName, Attr a1, Attr a2, Attr a3, Attr a4, Attr a5, Attr a6, Block block) {return new Tag(tagName, new Attr[]{a1, a2, a3, a4, a5, a6}, block);}
	public static Tag tag(String tagName, Attr a1, Attr a2, Attr a3, Attr a4, Attr a5, Attr a6, Attr a7, Block block) {return new Tag(tagName, new Attr[]{a1, a2, a3, a4, a5, a6, a7}, block);}
	public static Tag tag(String tagName, Attr a1, Attr a2, Attr a3, Attr a4, Attr a5, Attr a6, Attr a7, Attr a8, Block block) {return new Tag(tagName, new Attr[]{a1, a2, a3, a4, a5, a6, a7, a8}, block);}
	public static Tag tag(String tagName, Attr a1, Attr a2, Attr a3, Attr a4, Attr a5, Attr a6, Attr a7, Attr a8, Attr a9, Block block) {return new Tag(tagName, new Attr[]{a1, a2, a3, a4, a5, a6, a7, a8, a9}, block);}
	public static Tag tag(String tagName, Attr a1, Attr a2, Attr a3, Attr a4, Attr a5, Attr a6, Attr a7, Attr a8, Attr a9, Attr a10, Block block) {return new Tag(tagName, new Attr[]{a1, a2, a3, a4, a5, a6, a7, a8, a9, a10}, block);}

	static {
		tag("html", key1->"value1", key2->"value2", ()->{
			
		});
	}
}
