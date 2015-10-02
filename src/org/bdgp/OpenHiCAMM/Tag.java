package org.bdgp.OpenHiCAMM;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A lightweight HTML templating DSL using Java 8 lambdas.
 * @author benwbooth
 */
public class Tag {
	private String tagName;
	private List<Attr> attrs;
	private List<Block> blocks;
	
    private static ThreadLocal<Writer> writer = new ThreadLocal<Writer>();
	private static ThreadLocal<StringBuffer> writeBuffer = new ThreadLocal<StringBuffer>();
	private static ThreadLocal<String> indent = new ThreadLocal<String>();
	private static ThreadLocal<String> currentIndent = new ThreadLocal<String>();
	
	public static Set<String> selfClosingTags = new HashSet<String>();
	static {
	    Tag.selfClosingTags.addAll(Arrays.asList(new String[]{
	            "area","base","br","col","command","embed","hr",
	            "img","input","keygen","link","meta","param","source","track","wbr"
	    }));
	}
	
	public static Map<String,String> doctypes = new HashMap<String,String>();
	static {
        doctypes.put("default", "<!DOCTYPE html>");
        doctypes.put("5", "<!DOCTYPE html>");
        doctypes.put("xml", "<?xml version=\"1.0\" encoding=\"utf-8\" ?>");
        doctypes.put("transitional", "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">");
        doctypes.put("strict", "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">");
        doctypes.put("frameset", "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Frameset//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-frameset.dtd\">");
        doctypes.put("1.1", "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">");
        doctypes.put("basic", "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML Basic 1.1//EN\" \"http://www.w3.org/TR/xhtml-basic/xhtml-basic11.dtd\">");
        doctypes.put("mobile", "<!DOCTYPE html PUBLIC \"-//WAPFORUM//DTD XHTML Mobile 1.2//EN\" \"http://www.openmobilealliance.org/tech/DTD/xhtml-mobile12.dtd\">");
        doctypes.put("ce", "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"ce-html-1.0-transitional.dtd\">");
	}

	public static interface Attr {
		public Object attr(String key);
	}
	public static interface Block {
		public void block();
	}
	
	public Tag(String tagName, Attr... attrs) {
		this.tagName = tagName;
		this.attrs = Arrays.asList(attrs);
		this.blocks = new ArrayList<Block>();

		try { this.write(); } 
		catch (IOException e) {throw new RuntimeException(e);}
	}
	
	private void write() throws IOException {
        Writer writer = Tag.writer.get();
        if (writer != null) {
            StringBuffer sb = Tag.writeBuffer.get();
            writer.write(sb.toString());
            sb.setLength(0);

            StringBuilder attrs = new StringBuilder();
            for (int i=0; i<this.attrs.size(); ++i) {
                try { 
                    String attr = this.attrs.get(i).getClass().getDeclaredMethod("attr", String.class).getParameters()[0].getName();
                    attr = attr.replaceFirst("^_", "");
                    attr = attr.replaceFirst("_$", "");
                    attr = attr.replaceAll("_", "-");
                    attr = attr.toLowerCase();
                    attrs.append(String.format("%s=\"%s\"", escape(attr), escape(this.attrs.get(i).attr(attr))));
                    if (i < this.attrs.size()-1) attrs.append(" ");
                } 
                catch (NoSuchMethodException | SecurityException e) {throw new RuntimeException(e);}
            }

            writer.write(String.format("%s<%s%s", 
                    currentIndent.get() != null? currentIndent.get() : "", 
                    escape(this.tagName), 
                    attrs)); 

            if (Tag.selfClosingTags.contains(this.tagName)) {
                sb.append(String.format(" />%s", indent.get() != null? String.format("%n") : ""));
            }
            else {
                sb.append(String.format("></%s>%s", escape(this.tagName), indent.get() != null? String.format("%n") : ""));
            }
        }
	}
	
	private void writeBlock(Block block) throws IOException {
        Writer writer = Tag.writer.get();
        if (this.blocks.size() > 0 && writer != null) {
            StringBuffer sb = Tag.writeBuffer.get();
            sb.setLength(0);
            writer.write(String.format(">%s", indent.get() != null? String.format("%n") : ""));

            String currentIndent = Tag.currentIndent.get();
            String indent = Tag.indent.get();
            if (indent != null) Tag.currentIndent.set(String.format("%s%s", Tag.currentIndent.get(), Tag.indent.get()));
            try {
                block.block();
                Tag.writer.get().write(Tag.writeBuffer.get().toString());
            }
            finally {
                Tag.writeBuffer.get().setLength(0);
                Tag.currentIndent.set(currentIndent);
            }

            writer.write(String.format("%s</%s>%s", 
                    currentIndent != null? currentIndent : "", 
                    tagName, 
                    indent != null? String.format("%n") : "")); 
        }
	}
	
	public Tag with(Block block) {
	    this.blocks.add(block);

	    try { this.writeBlock(block); } 
	    catch (IOException e) {throw new RuntimeException(e);}
	    return this;
	}

	public static String escape(Object obj) {
	    String str = obj.toString();
	    str = str.replaceAll("\"", "&quot;");
	    str = str.replaceAll(">", "&gt;");
	    str = str.replaceAll("<", "&lt;");
	    str = str.replaceAll("&", "&amp;");
	    return str;
	}

	public void write(Writer writer) {
	    this.write(writer,"  ");
	}
	public void write(Writer writer, String indent) {
	    Tag.writeBuffer.set(new StringBuffer());
	    Tag.writer.set(writer);
	    Tag.indent.set(indent);
	    Tag.currentIndent.set(indent != null? "" : null);
	    
	    try {
            this.write();
            for (Block block : this.blocks) {
                this.writeBlock(block);  
            }
            Tag.writer.get().write(Tag.writeBuffer.get().toString());
        } 
	    catch (IOException e) {throw new RuntimeException(e);}
	    finally {
            Tag.writeBuffer.get().setLength(0);
            Tag.writer.set(null);
            Tag.indent.set(null);
            Tag.currentIndent.set(null);
	    }
	}
	public void print() {
	    this.write(new BufferedWriter(new OutputStreamWriter(System.out)));
	}
	public void print(String indent) {
	    this.write(new BufferedWriter(new OutputStreamWriter(System.out)), indent);
	}
	public String toString() {
	    StringWriter stringWriter = new StringWriter();
	    this.write(stringWriter);
	    return stringWriter.toString();
	}
	public String toString(String indent) {
	    StringWriter stringWriter = new StringWriter();
	    this.write(stringWriter, indent);
	    return stringWriter.toString();
	}

	public static void text(String text) {
	    if (Tag.writer.get() != null) {
            Writer writer = Tag.writer.get();
            StringBuffer sb = Tag.writeBuffer.get();
	        try { 
                writer.write(sb.toString());
                sb.setLength(0);
	            Tag.writer.get().write(escape(text)); 
            } 
	        catch (IOException e) {throw new RuntimeException(e);}
	        finally {
                sb.setLength(0);
	        }
	    }
	}
	public static void raw(String text) {
	    if (Tag.writer.get() != null) {
            Writer writer = Tag.writer.get();
            StringBuffer sb = Tag.writeBuffer.get();
	        try { 
                writer.write(sb.toString());
                sb.setLength(0);
	            Tag.writer.get().write(text);
            } 
	        catch (IOException e) {throw new RuntimeException(e);}
	        finally {
                sb.setLength(0);
	        }
	    }
	}
	public static void comment(String text) {
	    if (Tag.writer.get() != null) {
            Writer writer = Tag.writer.get();
            StringBuffer sb = Tag.writeBuffer.get();
	        try { 
                writer.write(sb.toString());
                sb.setLength(0);
	            Tag.writer.get().write(String.format("<!--%s-->", escape(text)));
            } 
	        catch (IOException e) {throw new RuntimeException(e);}
	        finally {
                sb.setLength(0);
	        }
	    }
	}
	public static void doctype() { doctype("5"); }
	public static void doctype(String type) {
	    if (Tag.writer.get() != null) {
            Writer writer = Tag.writer.get();
            StringBuffer sb = Tag.writeBuffer.get();
	        try { 
                writer.write(sb.toString());
                sb.setLength(0);
	            Tag.writer.get().write(doctypes.get(type));
            } 
	        catch (IOException e) {throw new RuntimeException(e);}
	        finally {
                sb.setLength(0);
	        }
	    }
	}

	public static Tag tag(String tagName, Attr... args) {return new Tag(tagName, args);}
    public static Tag A(Attr... attrs) {return new Tag("a", attrs);}
    public static Tag Abbr(Attr... attrs) {return new Tag("abbr", attrs);}
    public static Tag Address(Attr... attrs) {return new Tag("address", attrs);}
    public static Tag Area(Attr... attrs) {return new Tag("area", attrs);}
    public static Tag Article(Attr... attrs) {return new Tag("article", attrs);}
    public static Tag Aside(Attr... attrs) {return new Tag("aside", attrs);}
    public static Tag Audio(Attr... attrs) {return new Tag("audio", attrs);}
    public static Tag B(Attr... attrs) {return new Tag("b", attrs);}
    public static Tag Base(Attr... attrs) {return new Tag("base", attrs);}
    public static Tag Bdi(Attr... attrs) {return new Tag("bdi", attrs);}
    public static Tag Bdo(Attr... attrs) {return new Tag("bdo", attrs);}
    public static Tag Blockquote(Attr... attrs) {return new Tag("blockquote", attrs);}
    public static Tag Body(Attr... attrs) {return new Tag("body", attrs);}
    public static Tag Br(Attr... attrs) {return new Tag("br", attrs);}
    public static Tag Button(Attr... attrs) {return new Tag("button", attrs);}
    public static Tag Canvas(Attr... attrs) {return new Tag("canvas", attrs);}
    public static Tag Caption(Attr... attrs) {return new Tag("caption", attrs);}
    public static Tag Cite(Attr... attrs) {return new Tag("cite", attrs);}
    public static Tag Code(Attr... attrs) {return new Tag("code", attrs);}
    public static Tag Col(Attr... attrs) {return new Tag("col", attrs);}
    public static Tag Colgroup(Attr... attrs) {return new Tag("colgroup", attrs);}
    public static Tag Command(Attr... attrs) {return new Tag("command", attrs);}
    public static Tag Datalist(Attr... attrs) {return new Tag("datalist", attrs);}
    public static Tag Dd(Attr... attrs) {return new Tag("dd", attrs);}
    public static Tag Del(Attr... attrs) {return new Tag("del", attrs);}
    public static Tag Details(Attr... attrs) {return new Tag("details", attrs);}
    public static Tag Dfn(Attr... attrs) {return new Tag("dfn", attrs);}
    public static Tag Div(Attr... attrs) {return new Tag("div", attrs);}
    public static Tag Dl(Attr... attrs) {return new Tag("dl", attrs);}
    public static Tag Dt(Attr... attrs) {return new Tag("dt", attrs);}
    public static Tag Em(Attr... attrs) {return new Tag("em", attrs);}
    public static Tag Embed(Attr... attrs) {return new Tag("embed", attrs);}
    public static Tag Fieldset(Attr... attrs) {return new Tag("fieldset", attrs);}
    public static Tag Figcaption(Attr... attrs) {return new Tag("figcaption", attrs);}
    public static Tag Figure(Attr... attrs) {return new Tag("figure", attrs);}
    public static Tag Footer(Attr... attrs) {return new Tag("footer", attrs);}
    public static Tag Form(Attr... attrs) {return new Tag("form", attrs);}
    public static Tag H1(Attr... attrs) {return new Tag("h1", attrs);}
    public static Tag H2(Attr... attrs) {return new Tag("h2", attrs);}
    public static Tag H3(Attr... attrs) {return new Tag("h3", attrs);}
    public static Tag H4(Attr... attrs) {return new Tag("h4", attrs);}
    public static Tag H5(Attr... attrs) {return new Tag("h5", attrs);}
    public static Tag H6(Attr... attrs) {return new Tag("h6", attrs);}
    public static Tag Head(Attr... attrs) {return new Tag("head", attrs);}
    public static Tag Header(Attr... attrs) {return new Tag("header", attrs);}
    public static Tag Hgroup(Attr... attrs) {return new Tag("hgroup", attrs);}
    public static Tag Hr(Attr... attrs) {return new Tag("hr", attrs);}
    public static Tag Html(Attr... attrs) {return new Tag("html", attrs);}
    public static Tag I(Attr... attrs) {return new Tag("i", attrs);}
    public static Tag Iframe(Attr... attrs) {return new Tag("iframe", attrs);}
    public static Tag Img(Attr... attrs) {return new Tag("img", attrs);}
    public static Tag Input(Attr... attrs) {return new Tag("input", attrs);}
    public static Tag Ins(Attr... attrs) {return new Tag("ins", attrs);}
    public static Tag Kbd(Attr... attrs) {return new Tag("kbd", attrs);}
    public static Tag Keygen(Attr... attrs) {return new Tag("keygen", attrs);}
    public static Tag Label(Attr... attrs) {return new Tag("label", attrs);}
    public static Tag Legend(Attr... attrs) {return new Tag("legend", attrs);}
    public static Tag Li(Attr... attrs) {return new Tag("li", attrs);}
    public static Tag Link(Attr... attrs) {return new Tag("link", attrs);}
    public static Tag Map(Attr... attrs) {return new Tag("map", attrs);}
    public static Tag Mark(Attr... attrs) {return new Tag("mark", attrs);}
    public static Tag Menu(Attr... attrs) {return new Tag("menu", attrs);}
    public static Tag Meta(Attr... attrs) {return new Tag("meta", attrs);}
    public static Tag Meter(Attr... attrs) {return new Tag("meter", attrs);}
    public static Tag Nav(Attr... attrs) {return new Tag("nav", attrs);}
    public static Tag Noscript(Attr... attrs) {return new Tag("noscript", attrs);}
    public static Tag Object(Attr... attrs) {return new Tag("object", attrs);}
    public static Tag Ol(Attr... attrs) {return new Tag("ol", attrs);}
    public static Tag Optgroup(Attr... attrs) {return new Tag("optgroup", attrs);}
    public static Tag Option(Attr... attrs) {return new Tag("option", attrs);}
    public static Tag Output(Attr... attrs) {return new Tag("output", attrs);}
    public static Tag P(Attr... attrs) {return new Tag("p", attrs);}
    public static Tag Param(Attr... attrs) {return new Tag("param", attrs);}
    public static Tag Pre(Attr... attrs) {return new Tag("pre", attrs);}
    public static Tag Progress(Attr... attrs) {return new Tag("progress", attrs);}
    public static Tag Q(Attr... attrs) {return new Tag("q", attrs);}
    public static Tag Rp(Attr... attrs) {return new Tag("rp", attrs);}
    public static Tag Rt(Attr... attrs) {return new Tag("rt", attrs);}
    public static Tag Ruby(Attr... attrs) {return new Tag("ruby", attrs);}
    public static Tag S(Attr... attrs) {return new Tag("s", attrs);}
    public static Tag Samp(Attr... attrs) {return new Tag("samp", attrs);}
    public static Tag Script(Attr... attrs) {return new Tag("script", attrs);}
    public static Tag Section(Attr... attrs) {return new Tag("section", attrs);}
    public static Tag Select(Attr... attrs) {return new Tag("select", attrs);}
    public static Tag Small(Attr... attrs) {return new Tag("small", attrs);}
    public static Tag Source(Attr... attrs) {return new Tag("source", attrs);}
    public static Tag Span(Attr... attrs) {return new Tag("span", attrs);}
    public static Tag Strong(Attr... attrs) {return new Tag("strong", attrs);}
    public static Tag Style(Attr... attrs) {return new Tag("style", attrs);}
    public static Tag Sub(Attr... attrs) {return new Tag("sub", attrs);}
    public static Tag Summary(Attr... attrs) {return new Tag("summary", attrs);}
    public static Tag Sup(Attr... attrs) {return new Tag("sup", attrs);}
    public static Tag Table(Attr... attrs) {return new Tag("table", attrs);}
    public static Tag Tbody(Attr... attrs) {return new Tag("tbody", attrs);}
    public static Tag Td(Attr... attrs) {return new Tag("td", attrs);}
    public static Tag Textarea(Attr... attrs) {return new Tag("textarea", attrs);}
    public static Tag Tfoot(Attr... attrs) {return new Tag("tfoot", attrs);}
    public static Tag Th(Attr... attrs) {return new Tag("th", attrs);}
    public static Tag Thead(Attr... attrs) {return new Tag("thead", attrs);}
    public static Tag Time(Attr... attrs) {return new Tag("time", attrs);}
    public static Tag Title(Attr... attrs) {return new Tag("title", attrs);}
    public static Tag Tr(Attr... attrs) {return new Tag("tr", attrs);}
    public static Tag Track(Attr... attrs) {return new Tag("track", attrs);}
    public static Tag U(Attr... attrs) {return new Tag("u", attrs);}
    public static Tag Ul(Attr... attrs) {return new Tag("ul", attrs);}
    public static Tag Var(Attr... attrs) {return new Tag("var", attrs);}
    public static Tag Video(Attr... attrs) {return new Tag("video", attrs);}
    public static Tag Wbr(Attr... attrs) {return new Tag("wbr", attrs);}
}
