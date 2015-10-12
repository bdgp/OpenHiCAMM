package org.bdgp.OpenHiCAMM;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
	private StringWriter blockWriter;
	private String indent;
	private String currentIndent;
	private boolean autoWrite;

	private static ThreadLocal<Tag> lastTag = new ThreadLocal<Tag>();
	private static ThreadLocal<Tag> parentTag = new ThreadLocal<Tag>();
	private static ThreadLocal<Writer> writer = new ThreadLocal<Writer>();
	
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
	
	public Tag(String tagName, Object... texts) {
		this.tagName = tagName;
		this.attrs = new LinkedHashMap<String,String>();
		this.blockWriter = new StringWriter();

	    Tag parentTag = Tag.parentTag.get();
        this.indent = parentTag != null? parentTag.indent : null;
        this.autoWrite = parentTag != null? parentTag.autoWrite : true;
        this.currentIndent = this.indent != null?
                parentTag != null && parentTag.currentIndent != null?
                    String.format("%s%s", parentTag.currentIndent, this.indent) : 
                    "" :
                null;

        for (Object text : texts) {
            this.with(()->T.text(text.toString()));
        }
		
		// if there is a last tag, write it, then set the lastTag to this tag.
		if (parentTag != null) {
            writeLastTag();
            Tag.lastTag.set(this);
		}
	}
	
	private static void writeLastTag() {
		Tag lastTag = Tag.lastTag.get();
		if (lastTag != null && lastTag.autoWrite) {
            lastTag.write();
		}
        Tag.lastTag.remove();
	}
	
	public Tag attr(String key, Object value) {
	    this.attrs.put(key, value.toString());
	    return this;
	}
	
	public Tag with(Block block) {
        Tag lastTag = Tag.lastTag.get();
	    Tag parentTag = Tag.parentTag.get();
	    Writer writer = Tag.writer.get();
        try {
            Tag.parentTag.set(this);
            Tag.lastTag.set(null);
            Tag.writer.set(this.blockWriter);

            block.block();
            writeLastTag();
        }
        finally {
            Tag.parentTag.set(parentTag);
            Tag.lastTag.set(lastTag);
            Tag.writer.set(writer);
        }
	    return this;
	}
	
	public Tag text(Object text) {
	    this.with(()->T.text(text.toString()));
	    return this;
	}
	public Tag raw(Object text) {
	    this.with(()->T.raw(text.toString()));
	    return this;
	}
	public Tag comment(Object text) {
	    this.with(()->T.comment(text.toString()));
	    return this;
	}
	public Tag doctype() {
	    this.with(()->T.doctype());
	    return this;
	}
	public Tag doctype(String type) {
	    this.with(()->T.doctype(type));
	    return this;
	}
	
	public Tag autoWrite(boolean autoWrite) {
	    this.autoWrite = autoWrite;
	    return this;
	}
	public Tag autoWrite() {
	    return this.autoWrite(true);
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
	public Tag indent() {
	    return indent(true);
	}
	public Tag indent(int indented) {
	    return indent(indented > 0? Stream.generate(()->" ").limit(indented).collect(Collectors.joining()) : null);
	}

	public Tag currentIndent(String currentIndent) {
	    this.currentIndent = currentIndent;
	    return this;
	}
	
	public Tag writer(Writer writer) {
	    Tag.writer.set(writer);
	    return this;
	}
	
	public Tag write() {
	    Writer writer = Tag.writer.get();
        if (writer != null) {
            try {
                writer.write(String.format("%s<%s", 
                        currentIndent != null? currentIndent : "", 
                        escape(this.tagName))); 

                for (Map.Entry<String,String> entry : this.attrs.entrySet()) {
                    writer.write(String.format(" %s=\"%s\"", escape(entry.getKey()), escape(entry.getValue())));
                }

                if (Tag.selfClosingTags.contains(this.tagName.toLowerCase()) && 
                    this.blockWriter.getBuffer().length() == 0) 
                {
                    writer.write(String.format(" />%s", indent != null? String.format("%n") : ""));
                }
                else {
                    writer.write(String.format(">%s", indent != null? String.format("%n") : ""));
                    writer.write(this.blockWriter.toString());
                    writer.write(String.format("%s</%s>%s", 
                            currentIndent != null? currentIndent : "",
                            escape(this.tagName), 
                            indent != null? String.format("%n") : ""));
                }
            }
            catch (IOException e) { throw new RuntimeException(e); }
        }
        return this;
	}
	
	public static String escape(Object obj) {
	    String str = obj.toString();
	    str = str.replaceAll("&", "&amp;");
	    str = str.replaceAll("\"", "&quot;");
	    str = str.replaceAll(">", "&gt;");
	    str = str.replaceAll("<", "&lt;");
	    return str;
	}

	public void write(Writer writer) {
	    Tag.writer.set(writer);
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

	public static class T {
        public static void text(String text) {
            writeLastTag();
            Tag parentTag = Tag.parentTag.get();
            Writer writer = Tag.writer.get();
            if (parentTag != null && writer != null) {
                String indent = parentTag.currentIndent != null && parentTag.indent != null? 
                        String.format("%s%s", parentTag.currentIndent, parentTag.indent) : "";
                String newline = parentTag.currentIndent != null && parentTag.indent != null? 
                        String.format("%n") : "";
                try { writer.write(String.format("%s%s%s", indent, escape(text), newline)); } 
                catch (IOException e) {throw new RuntimeException(e);}
            }
        }
        public static void raw(String text) {
            writeLastTag();
            Tag parentTag = Tag.parentTag.get();
            Writer writer = Tag.writer.get();
            if (parentTag != null && writer != null) {
                try { writer.write(text); } 
                catch (IOException e) {throw new RuntimeException(e);}
            }
        }
        public static void comment(String text) {
            writeLastTag();
            Tag parentTag = Tag.parentTag.get();
            Writer writer = Tag.writer.get();
            if (parentTag != null && writer != null) {
                String indent = parentTag.currentIndent != null && parentTag.indent != null? 
                        String.format("%s%s", parentTag.currentIndent, parentTag.indent) : "";
                String newline = parentTag.currentIndent != null && parentTag.indent != null? 
                        String.format("%n") : "";
                try { writer.write(String.format("%s<!--%s-->%s", indent, escape(text), newline)); } 
                catch (IOException e) {throw new RuntimeException(e);}
            }
        }
        public static void doctype() { doctype("5"); }
        public static void doctype(String type) {
            writeLastTag();
            Tag parentTag = Tag.parentTag.get();
            Writer writer = Tag.writer.get();
            if (parentTag != null && writer != null) {
                String indent = parentTag.currentIndent != null && parentTag.indent != null? 
                        String.format("%s%s", parentTag.currentIndent, parentTag.indent) : "";
                String newline = parentTag.currentIndent != null && parentTag.indent != null? 
                        String.format("%n") : "";
                try { writer.write(String.format("%s%s%s", indent, doctypes.get(type), newline)); } 
                catch (IOException e) {throw new RuntimeException(e);}
            }
        }

        public static Tag tag(String tagName, Object... texts) {return new Tag(tagName, texts);}
        public static Tag A(Object... texts) {return new Tag("a", texts);}
        public static Tag Abbr(Object... texts) {return new Tag("abbr", texts);}
        public static Tag Address(Object... texts) {return new Tag("address", texts);}
        public static Tag Area(Object... texts) {return new Tag("area", texts);}
        public static Tag Article(Object... texts) {return new Tag("article", texts);}
        public static Tag Aside(Object... texts) {return new Tag("aside", texts);}
        public static Tag Audio(Object... texts) {return new Tag("audio", texts);}
        public static Tag B(Object... texts) {return new Tag("b", texts);}
        public static Tag Base(Object... texts) {return new Tag("base", texts);}
        public static Tag Bdi(Object... texts) {return new Tag("bdi", texts);}
        public static Tag Bdo(Object... texts) {return new Tag("bdo", texts);}
        public static Tag Blockquote(Object... texts) {return new Tag("blockquote", texts);}
        public static Tag Body(Object... texts) {return new Tag("body", texts);}
        public static Tag Br(Object... texts) {return new Tag("br", texts);}
        public static Tag Button(Object... texts) {return new Tag("button", texts);}
        public static Tag Canvas(Object... texts) {return new Tag("canvas", texts);}
        public static Tag Caption(Object... texts) {return new Tag("caption", texts);}
        public static Tag Cite(Object... texts) {return new Tag("cite", texts);}
        public static Tag Code(Object... texts) {return new Tag("code", texts);}
        public static Tag Col(Object... texts) {return new Tag("col", texts);}
        public static Tag Colgroup(Object... texts) {return new Tag("colgroup", texts);}
        public static Tag Command(Object... texts) {return new Tag("command", texts);}
        public static Tag Datalist(Object... texts) {return new Tag("datalist", texts);}
        public static Tag Dd(Object... texts) {return new Tag("dd", texts);}
        public static Tag Del(Object... texts) {return new Tag("del", texts);}
        public static Tag Details(Object... texts) {return new Tag("details", texts);}
        public static Tag Dfn(Object... texts) {return new Tag("dfn", texts);}
        public static Tag Div(Object... texts) {return new Tag("div", texts);}
        public static Tag Dl(Object... texts) {return new Tag("dl", texts);}
        public static Tag Dt(Object... texts) {return new Tag("dt", texts);}
        public static Tag Em(Object... texts) {return new Tag("em", texts);}
        public static Tag Embed(Object... texts) {return new Tag("embed", texts);}
        public static Tag Fieldset(Object... texts) {return new Tag("fieldset", texts);}
        public static Tag Figcaption(Object... texts) {return new Tag("figcaption", texts);}
        public static Tag Figure(Object... texts) {return new Tag("figure", texts);}
        public static Tag Footer(Object... texts) {return new Tag("footer", texts);}
        public static Tag Form(Object... texts) {return new Tag("form", texts);}
        public static Tag H1(Object... texts) {return new Tag("h1", texts);}
        public static Tag H2(Object... texts) {return new Tag("h2", texts);}
        public static Tag H3(Object... texts) {return new Tag("h3", texts);}
        public static Tag H4(Object... texts) {return new Tag("h4", texts);}
        public static Tag H5(Object... texts) {return new Tag("h5", texts);}
        public static Tag H6(Object... texts) {return new Tag("h6", texts);}
        public static Tag Head(Object... texts) {return new Tag("head", texts);}
        public static Tag Header(Object... texts) {return new Tag("header", texts);}
        public static Tag Hgroup(Object... texts) {return new Tag("hgroup", texts);}
        public static Tag Hr(Object... texts) {return new Tag("hr", texts);}
        public static Tag Html(Object... texts) {return new Tag("html", texts);}
        public static Tag I(Object... texts) {return new Tag("i", texts);}
        public static Tag Iframe(Object... texts) {return new Tag("iframe", texts);}
        public static Tag Img(Object... texts) {return new Tag("img", texts);}
        public static Tag Input(Object... texts) {return new Tag("input", texts);}
        public static Tag Ins(Object... texts) {return new Tag("ins", texts);}
        public static Tag Kbd(Object... texts) {return new Tag("kbd", texts);}
        public static Tag Keygen(Object... texts) {return new Tag("keygen", texts);}
        public static Tag Label(Object... texts) {return new Tag("label", texts);}
        public static Tag Legend(Object... texts) {return new Tag("legend", texts);}
        public static Tag Li(Object... texts) {return new Tag("li", texts);}
        public static Tag Link(Object... texts) {return new Tag("link", texts);}
        public static Tag Map(Object... texts) {return new Tag("map", texts);}
        public static Tag Mark(Object... texts) {return new Tag("mark", texts);}
        public static Tag Menu(Object... texts) {return new Tag("menu", texts);}
        public static Tag Meta(Object... texts) {return new Tag("meta", texts);}
        public static Tag Meter(Object... texts) {return new Tag("meter", texts);}
        public static Tag Nav(Object... texts) {return new Tag("nav", texts);}
        public static Tag Noscript(Object... texts) {return new Tag("noscript", texts);}
        public static Tag Object(Object... texts) {return new Tag("object", texts);}
        public static Tag Ol(Object... texts) {return new Tag("ol", texts);}
        public static Tag Optgroup(Object... texts) {return new Tag("optgroup", texts);}
        public static Tag Option(Object... texts) {return new Tag("option", texts);}
        public static Tag Output(Object... texts) {return new Tag("output", texts);}
        public static Tag P(Object... texts) {return new Tag("p", texts);}
        public static Tag Param(Object... texts) {return new Tag("param", texts);}
        public static Tag Pre(Object... texts) {return new Tag("pre", texts);}
        public static Tag Progress(Object... texts) {return new Tag("progress", texts);}
        public static Tag Q(Object... texts) {return new Tag("q", texts);}
        public static Tag Rp(Object... texts) {return new Tag("rp", texts);}
        public static Tag Rt(Object... texts) {return new Tag("rt", texts);}
        public static Tag Ruby(Object... texts) {return new Tag("ruby", texts);}
        public static Tag S(Object... texts) {return new Tag("s", texts);}
        public static Tag Samp(Object... texts) {return new Tag("samp", texts);}
        public static Tag Script(Object... texts) {return new Tag("script", texts);}
        public static Tag Section(Object... texts) {return new Tag("section", texts);}
        public static Tag Select(Object... texts) {return new Tag("select", texts);}
        public static Tag Small(Object... texts) {return new Tag("small", texts);}
        public static Tag Source(Object... texts) {return new Tag("source", texts);}
        public static Tag Span(Object... texts) {return new Tag("span", texts);}
        public static Tag Strong(Object... texts) {return new Tag("strong", texts);}
        public static Tag Style(Object... texts) {return new Tag("style", texts);}
        public static Tag Sub(Object... texts) {return new Tag("sub", texts);}
        public static Tag Summary(Object... texts) {return new Tag("summary", texts);}
        public static Tag Sup(Object... texts) {return new Tag("sup", texts);}
        public static Tag Table(Object... texts) {return new Tag("table", texts);}
        public static Tag Tbody(Object... texts) {return new Tag("tbody", texts);}
        public static Tag Td(Object... texts) {return new Tag("td", texts);}
        public static Tag Textarea(Object... texts) {return new Tag("textarea", texts);}
        public static Tag Tfoot(Object... texts) {return new Tag("tfoot", texts);}
        public static Tag Th(Object... texts) {return new Tag("th", texts);}
        public static Tag Thead(Object... texts) {return new Tag("thead", texts);}
        public static Tag Time(Object... texts) {return new Tag("time", texts);}
        public static Tag Title(Object... texts) {return new Tag("title", texts);}
        public static Tag Tr(Object... texts) {return new Tag("tr", texts);}
        public static Tag Track(Object... texts) {return new Tag("track", texts);}
        public static Tag U(Object... texts) {return new Tag("u", texts);}
        public static Tag Ul(Object... texts) {return new Tag("ul", texts);}
        public static Tag Var(Object... texts) {return new Tag("var", texts);}
        public static Tag Video(Object... texts) {return new Tag("video", texts);}
        public static Tag Wbr(Object... texts) {return new Tag("wbr", texts);}
	}

	public static Writer nullWriter = new OutputStreamWriter(new OutputStream() {
	    @Override public void write(int b) throws IOException { }
	});
}
