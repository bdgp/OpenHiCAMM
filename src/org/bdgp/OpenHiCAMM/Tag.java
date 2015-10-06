package org.bdgp.OpenHiCAMM;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A lightweight HTML templating DSL using Java 8 lambdas.
 * @author benwbooth
 */
public class Tag {
	private String tagName;
	private Map<String,String> attrs;
	private List<Block> blocks;
	private Writer writer;
	private String indent;
	private String currentIndent;

	private static ThreadLocal<Tag> lastTag = new ThreadLocal<Tag>();
	private static ThreadLocal<Tag> parentTag = new ThreadLocal<Tag>();
	
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

	@FunctionalInterface
	public static interface Attr {
		public Object attr(String key);
	}
	@FunctionalInterface
	public static interface Block {
		public void block();
	}
	
	public Tag(String tagName, Attr... attrs) {
		this.tagName = tagName;
		this.attrs = new LinkedHashMap<String,String>();
		this.blocks = new ArrayList<Block>();

	    Tag parentTag = Tag.parentTag.get();
        this.indent = parentTag != null? parentTag.indent : null;
        this.currentIndent = this.indent != null?
                parentTag != null && parentTag.currentIndent != null?
                    String.format("%s%s", parentTag.currentIndent, this.indent) : 
                    "" :
                null;
        this.writer = parentTag != null && parentTag.writer != null? parentTag.writer : null;

		this.attr(attrs);
		
		// if there is a last tag, write it, then set the lastTag to this tag.
		if (parentTag != null) {
            writeLastTag();
            Tag.lastTag.set(this);
		}
	}
	
	private static void writeLastTag() {
		Tag lastTag = Tag.lastTag.get();
		if (lastTag != null) {
            lastTag.write();
		}
        Tag.lastTag.remove();
	}
	
	public Tag attr(String key, Object value) {
	    this.attrs.put(key, value.toString());
	    return this;
	}
	
	public Tag attr(Attr... attrs) {
		for (Attr attr : attrs) {
            try {
                String key = attr.getClass().getDeclaredMethod("attr", String.class).getParameters()[0].getName();
                key = key.toLowerCase();
                this.attrs.put(key, attr.attr(key).toString());
            } catch (NoSuchMethodException | SecurityException e) { throw new RuntimeException(e); }
		}
	    return this;
	}
	
	public Tag with(Block block) {
	    this.blocks.add(block);
	    return this;
	}
	
	public Tag text(String text) {
	    this.blocks.add(()->Tags.text(text));
	    return this;
	}
	public Tag raw(String text) {
	    this.blocks.add(()->Tags.raw(text));
	    return this;
	}
	public Tag comment(String text) {
	    this.blocks.add(()->Tags.comment(text));
	    return this;
	}
	public Tag doctype() {
	    this.blocks.add(()->Tags.doctype());
	    return this;
	}
	public Tag doctype(String type) {
	    this.blocks.add(()->Tags.doctype(type));
	    return this;
	}

	public Tag pass() {
	    this.writer = nullWriter;
	    return this;
	}
	
	public Tag indent(String indent) {
	    this.indent = indent;
	    Tag parentTag = Tag.parentTag.get();
        this.currentIndent = this.indent != null?
                parentTag != null && parentTag.currentIndent != null?
                    String.format("%s%s", parentTag.currentIndent, this.indent) : 
                    "" :
                null;
	    return this;
	}
	public Tag indent(boolean indented) {
	    return indent(indented? "  " : null);
	}
	public Tag indent(int indented) {
	    return indent(indented > 0? Stream.generate(()->" ").limit(indented).collect(Collectors.joining()) : null);
	}

	public Tag currentIndent(String currentIndent) {
	    this.currentIndent = currentIndent;
	    return this;
	}
	
	public Tag writer(Writer writer) {
	    this.writer = writer;
	    return this;
	}
	
	public Tag write() {
        Tag lastTag = Tag.lastTag.get();
	    Tag parentTag = Tag.parentTag.get();
        try {
            if (writer != null) {
                writer.write(String.format("%s<%s", 
                        currentIndent != null? currentIndent : "", 
                        escape(this.tagName))); 

                for (Map.Entry<String,String> entry : this.attrs.entrySet()) {
                    writer.write(String.format(" %s=\"%s\"", escape(entry.getKey()), escape(entry.getValue())));
                }
            }

            if (writer != null && Tag.selfClosingTags.contains(this.tagName.toLowerCase()) && this.blocks.isEmpty()) {
                writer.write(String.format(" />%s", indent != null? String.format("%n") : ""));
            }
            else {
                if (writer != null) {
                    writer.write(String.format(">%s", indent != null? String.format("%n") : ""));
                }
                for (Block block : this.blocks) {
                    try {
                        Tag.parentTag.set(this);
                        Tag.lastTag.set(null);
                        block.block();
                        writeLastTag();
                    }
                    finally {
                        Tag.parentTag.set(parentTag);
                        Tag.lastTag.set(lastTag);
                    }
                }
                if (writer != null) {
                    writer.write(String.format("%s</%s>%s", 
                            currentIndent != null? currentIndent : "",
                            escape(this.tagName), 
                            indent != null? String.format("%n") : ""));
                }
            }
        }
        catch (IOException e) { throw new RuntimeException(e); }
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
	    this.writer = writer;
	    this.write();
	}

	public void print() {
	    Writer writer = new BufferedWriter(new OutputStreamWriter(System.out)); 
	    this.write(writer);
	}

	public String toString() {
	    StringWriter stringWriter = new StringWriter();
	    this.write(stringWriter);
	    return stringWriter.toString();
	}

	public static class Tags {
        public static void text(String text) {
            writeLastTag();
            Tag parentTag = Tag.parentTag.get();
            if (parentTag != null && parentTag.writer != null) {
                try { parentTag.writer.write(escape(text)); } 
                catch (IOException e) {throw new RuntimeException(e);}
            }
        }
        public static void raw(String text) {
            writeLastTag();
            Tag parentTag = Tag.parentTag.get();
            if (parentTag != null && parentTag.writer != null) {
                try { parentTag.writer.write(text); } 
                catch (IOException e) {throw new RuntimeException(e);}
            }
        }
        public static void comment(String text) {
            writeLastTag();
            Tag parentTag = Tag.parentTag.get();
            if (parentTag != null && parentTag.writer != null) {
                try { parentTag.writer.write(String.format("<!--%s-->", escape(text))); } 
                catch (IOException e) {throw new RuntimeException(e);}
            }
        }
        public static void doctype() { doctype("5"); }
        public static void doctype(String type) {
            writeLastTag();
            Tag parentTag = Tag.parentTag.get();
            if (parentTag != null && parentTag.writer != null) {
                try { parentTag.writer.write(doctypes.get(type)); } 
                catch (IOException e) {throw new RuntimeException(e);}
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

	public static Writer nullWriter = new OutputStreamWriter(new OutputStream() {
	    @Override public void write(int b) throws IOException { }
	});
}
