/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.docs.confluence;

import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.mylyn.internal.wikitext.core.util.css.CssParser;
import org.eclipse.mylyn.internal.wikitext.core.util.css.CssRule;
import org.eclipse.mylyn.wikitext.core.parser.Attributes;
import org.eclipse.mylyn.wikitext.core.parser.DocumentBuilder;
import org.eclipse.mylyn.wikitext.core.parser.LinkAttributes;
import org.eclipse.mylyn.wikitext.core.parser.builder.AbstractXmlDocumentBuilder;
import org.eclipse.mylyn.wikitext.core.util.FormattingXMLStreamWriter;
import org.eclipse.mylyn.wikitext.core.util.XmlStreamWriter;

/**
 * A builder that can emit <a href="http://www.docbook.org/">Docbook</a>
 *
 */
public class DocBookDocumentBuilder extends AbstractXmlDocumentBuilder {

	private static final Pattern PERCENTAGE = Pattern.compile("(\\d+)%"); //$NON-NLS-1$

	private static final Pattern CSS_CLASS_INLINE = Pattern.compile("(^|\\s+)inline(\\s+|$)"); //$NON-NLS-1$

	private static Set<Integer> entityReferenceToUnicode = new HashSet<Integer>();
	static {
		entityReferenceToUnicode.add(215);
		entityReferenceToUnicode.add(8211);
		entityReferenceToUnicode.add(8212);
		entityReferenceToUnicode.add(8220);
		entityReferenceToUnicode.add(8221);
		entityReferenceToUnicode.add(8216);
		entityReferenceToUnicode.add(8217);

	}

//	private String bookTitle;

	private String namespace = "http://docbook.org/ns/docbook"; //$NON-NLS-1$

	private final Map<String, String> acronyms = new HashMap<String, String>();

	private int headingLevel = 0;

	private final Stack<BlockDescription> blockDescriptions = new Stack<BlockDescription>();

    private final Stack<HeadingDescription> headingDescriptions = new Stack<HeadingDescription>();

	private boolean automaticGlossary = true;

    public DocBookDocumentBuilder(Writer out) {
		super(out);
	}

	public DocBookDocumentBuilder(XmlStreamWriter writer) {
		super(writer);
	}

	protected XmlStreamWriter createXmlStreamWriter(Writer out) {
		XmlStreamWriter writer = super.createXmlStreamWriter(out);
		return new FormattingXMLStreamWriter(writer) {
			@Override
			protected boolean preserveWhitespace(String elementName) {
				return elementName.equals("programlisting") || elementName.equals("code") || elementName.startsWith("literal"); //$NON-NLS-1$ //$NON-NLS-2$
			}
		};
	}

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	@Override
	public void acronym(String text, String definition) {
		String previousDef = acronyms.put(text, definition);
		if (previousDef != null && previousDef.length() > definition.length()) {
			acronyms.put(text, previousDef);
		}
		writer.writeStartElement("glossterm"); //$NON-NLS-1$
		characters(text);
		writer.writeEndElement();
	}

	@Override
	public void link(Attributes attributes, String href, final String text) {
		link(attributes, href, new ContentEmitter() {
			public void emit() {
				writer.writeCharacters(text);
			}
		});
	}

	private void link(Attributes attributes, String href, ContentEmitter emitter) {
		ensureBlockElementsOpen();
		if (href.startsWith("#")) { //$NON-NLS-1$
			if (href.length() > 1) {
				writer.writeStartElement("link"); //$NON-NLS-1$
				writer.writeAttribute("linkend", href.substring(1)); //$NON-NLS-1$
				emitter.emit();
				writer.writeEndElement(); // link
			} else {
				emitter.emit();
			}
		} else {
			writer.writeStartElement("ulink"); //$NON-NLS-1$
			writer.writeAttribute("url", href); //$NON-NLS-1$
			emitter.emit();
			writer.writeEndElement(); // ulink
		}
	}

    private void transformId(String id) {
        
    }

    private interface ContentEmitter {
		public void emit();
	}

	@Override
	public void beginBlock(BlockType type, Attributes attributes) {
		if (headingLevel == 0) {
			beginHeading(1, new Attributes());
			endHeading();
		}

		String elementName;
		String[] elementNames = null;
		boolean allowTitle = false;
		boolean closeElementsOnBlockStart = false;
        BlockDescription previousBlock = null;
		if (!blockDescriptions.isEmpty()) {
			previousBlock = blockDescriptions.peek();
		}

		switch (type) {
		case BULLETED_LIST:
			elementName = "itemizedlist"; //$NON-NLS-1$
			break;
		case NUMERIC_LIST:
			elementName = "orderedlist"; //$NON-NLS-1$
			break;
		case DEFINITION_LIST:
			elementName = "variablelist"; //$NON-NLS-1$

			//			variablelist
			//				varlistentry+
			//					term+
			//					listitem
			//
			break;
		case DEFINITION_TERM:

			BlockDescription blockDescription = findBlockDescription(BlockType.DEFINITION_LIST);
			if (blockDescription.entrySize > 0) {
				endBlockEntry(blockDescription);
			}
			openBlockEntry(blockDescription, new String[] { "varlistentry" }); //$NON-NLS-1$

			elementName = "term"; //$NON-NLS-1$
			break;
		case DEFINITION_ITEM:
			elementName = "listitem"; //$NON-NLS-1$
			elementNames = new String[] { "para" }; //$NON-NLS-1$
			closeElementsOnBlockStart = true;
			break;
		case FOOTNOTE:
		case PARAGRAPH:
			elementName = "para"; //$NON-NLS-1$
			break;
		case CODE:
			elementName = "programlisting"; //$NON-NLS-1$
			break;
		case PREFORMATTED:
			elementName = "literallayout"; //$NON-NLS-1$
			break;
		case QUOTE:
			elementName = "blockquote"; //$NON-NLS-1$
			break;
		case LIST_ITEM:
			elementName = "listitem"; //$NON-NLS-1$
			elementNames = new String[] { "para" }; //$NON-NLS-1$
			closeElementsOnBlockStart = true;
			break;
		case TABLE:
			elementName = "informaltable"; //$NON-NLS-1$
			break;
		case TABLE_CELL_HEADER:
			elementName = "th"; //$NON-NLS-1$
			break;
		case TABLE_CELL_NORMAL:
			elementName = "td"; //$NON-NLS-1$
			break;
		case TABLE_ROW:
			elementName = "tr"; //$NON-NLS-1$
			break;
		case INFORMATION:
			elementName = "important"; //$NON-NLS-1$
			allowTitle = true;
			break;
		case NOTE:
			elementName = "note"; //$NON-NLS-1$
			allowTitle = true;
			break;
		case WARNING:
			elementName = "warning"; //$NON-NLS-1$
			allowTitle = true;
			break;
		case TIP:
			elementName = "tip"; //$NON-NLS-1$
			allowTitle = true;
			break;
		case PANEL:
			elementName = "note"; // docbook has nothing better for 'note' //$NON-NLS-1$
			allowTitle = true;
			break;
		case DIV:
			elementName = null;
			break;
		default:
			throw new IllegalStateException(type.name());
		}

		int blockSize;
		if (elementName != null) {
			blockSize = 1;

			if (previousBlock != null && previousBlock.closeElementsOnBlockStart) {
				endBlockEntry(previousBlock);
			}

			writer.writeStartElement(elementName);
			applyAttributes(attributes);

			if (elementNames != null) {
				for (String name : elementNames) {
					writer.writeStartElement(name);
				}
			}

			if (allowTitle && attributes.getTitle() != null) {
				writer.writeStartElement("title"); //$NON-NLS-1$
				writer.writeCharacters(attributes.getTitle());
				writer.writeEndElement();
			}
		} else {
			blockSize = 0;
		}
		blockDescriptions.push(new BlockDescription(type, blockSize, elementNames, closeElementsOnBlockStart));
	}

	@Override
	public void endBlock() {
		final BlockDescription blockDescription = blockDescriptions.pop();
		int size = blockDescription.size + blockDescription.entrySize;
		for (int x = 0; x < size; ++x) {
			writer.writeEndElement();
		}
	}

	private void endBlockEntry(BlockDescription blockDescription) {
		for (int x = 0; x < blockDescription.entrySize; ++x) {
			writer.writeEndElement();
		}
		blockDescription.entrySize = 0;
	}

	private void openBlockEntry(BlockDescription blockDescription, String[] entry) {
		for (String ent : entry) {
			writer.writeStartElement(ent);
		}
		blockDescription.entrySize += entry.length;
	}

	@Override
	public void beginHeading(int level, Attributes attributes) {
		closeSections(Math.max(level - 1, 0));

		while (headingLevel < level) {
			headingLevel++;

            if (headingLevel == 1) {
                writer.writeStartElement("chapter"); //$NON-NLS-1$
                writer.writeNamespace("", namespace);

            } else {
                writer.writeStartElement("section"); //$NON-NLS-1$
            }
			if (attributes != null) {
				applyAttributes(attributes);
				attributes = null;
			}

            headingDescriptions.push(new HeadingDescription(level, attributes));
		}

		writer.writeStartElement("title"); //$NON-NLS-1$
	}

	@Override
	public void endHeading() {
		writer.writeEndElement(); // title
	}

	@Override
	public void beginDocument() {
		baseInHead = false;
	}

	@Override
	public void beginSpan(SpanType type, Attributes attributes) {
		ensureBlockElementsOpen();
		switch (type) {
		case BOLD:
		case STRONG:
			writer.writeStartElement("emphasis"); //$NON-NLS-1$
			writer.writeAttribute("role", "bold"); //$NON-NLS-1$ //$NON-NLS-2$
			break;
		case CITATION:
			writer.writeStartElement("citation"); //$NON-NLS-1$
			break;
		case CODE:
			writer.writeStartElement("code"); //$NON-NLS-1$
			break;
		case DELETED:
			writer.writeStartElement("emphasis"); //$NON-NLS-1$
			writer.writeAttribute("role", "del"); //$NON-NLS-1$ //$NON-NLS-2$
			break;
		case EMPHASIS:
			writer.writeStartElement("emphasis"); //$NON-NLS-1$
			break;
		case INSERTED:
			writer.writeStartElement("emphasis"); //$NON-NLS-1$
			writer.writeAttribute("role", "ins"); //$NON-NLS-1$ //$NON-NLS-2$
			break;
		case UNDERLINED:
			writer.writeStartElement("emphasis"); //$NON-NLS-1$
			writer.writeAttribute("role", "underline"); //$NON-NLS-1$ //$NON-NLS-2$
			break;
		case ITALIC:
			writer.writeStartElement("emphasis"); //$NON-NLS-1$
			writer.writeAttribute("role", "italic"); //$NON-NLS-1$ //$NON-NLS-2$
			break;
		case QUOTE:
			writer.writeStartElement("quote"); //$NON-NLS-1$
			break;
		case SPAN:
			writer.writeStartElement("phrase"); //$NON-NLS-1$
			break;
		case SUBSCRIPT:
			writer.writeStartElement("subscript"); //$NON-NLS-1$
			break;
		case SUPERSCRIPT:
			writer.writeStartElement("superscript"); //$NON-NLS-1$
			break;
		case MONOSPACE:
			writer.writeStartElement("literal"); //$NON-NLS-1$
			break;
		case LINK: {
			LinkAttributes linkAttributes = (LinkAttributes) attributes;
			String href = linkAttributes.getHref();
			if (href.startsWith("#")) { //$NON-NLS-1$
				writer.writeStartElement("link"); //$NON-NLS-1$
				if (href.length() > 1) {
					writer.writeAttribute("linkend", href.substring(1)); //$NON-NLS-1$
				}
			} else {
				writer.writeStartElement("ulink"); //$NON-NLS-1$
				writer.writeAttribute("url", href); //$NON-NLS-1$
			}
		}
			break;
		default:
			Logger.getLogger(DocBookDocumentBuilder.class.getName()).warning("No docbook mapping for " + type); //$NON-NLS-1$
			writer.writeStartElement("phrase"); //$NON-NLS-1$
			break;
		}
		applyAttributes(attributes);
	}

	private void applyAttributes(Attributes attributes) {
        if (attributes.getId() != null) {
            writer.writeAttribute("id", attributes.getId()); //$NON-NLS-1$
        }
        if (attributes.getLanguage() != null) {
            writer.writeAttribute("language", attributes.getLanguage()); //$NON-NLS-1$
        }
	}

	@Override
	public void endDocument() {
		closeSections(0);

		writeGlossaryAppendix();

//		writer.writeEndElement(); // book
		writer.writeEndDocument();

		acronyms.clear();
	}

	private void closeSections(int toLevel) {
		if (toLevel < 0) {
			toLevel = 0;
		}
		while (headingLevel > toLevel) {
			writer.writeEndElement();
			--headingLevel;
            headingDescriptions.pop();
		}
	}

	private void writeGlossaryAppendix() {
		if (!acronyms.isEmpty() && automaticGlossary) {
			writer.writeStartElement("appendix"); //$NON-NLS-1$
			writer.writeAttribute("id", "glossary"); //$NON-NLS-1$ //$NON-NLS-2$
			writer.writeStartElement("title"); //$NON-NLS-1$
			writer.writeAttribute("id", "glossary-end"); //$NON-NLS-1$ //$NON-NLS-2$
			writer.writeCharacters("Glossary"); //$NON-NLS-1$
			writer.writeEndElement(); // title
			writer.writeStartElement("glosslist"); //$NON-NLS-1$

			for (Map.Entry<String, String> glossEntry : new TreeMap<String, String>(acronyms).entrySet()) {

				writer.writeStartElement("glossentry"); //$NON-NLS-1$

				writer.writeStartElement("glossterm"); //$NON-NLS-1$
				writer.writeCharacters(glossEntry.getKey());
				writer.writeEndElement(); // glossterm

				writer.writeStartElement("glossdef"); //$NON-NLS-1$
				writer.writeStartElement("para"); //$NON-NLS-1$
				writer.writeCharacters(glossEntry.getValue());
				writer.writeEndElement(); // para
				writer.writeEndElement(); // glossdef

				writer.writeEndElement(); // glossentry
			}
			writer.writeEndElement(); // glosslist
			writer.writeEndElement(); // appendix
		}
	}

	@Override
	public void endSpan() {
		writer.writeEndElement();
	}

	@Override
	public void characters(String text) {
		ensureBlockElementsOpen();
		super.characters(text);
	}

	@Override
	public void charactersUnescaped(String literal) {
		ensureBlockElementsOpen();
		// note: this *may* have HTML tags in it
		writer.writeLiteral(literal);
		//		Logger.getLogger(DocBookDocumentBuilder.class.getName()).warning("HTML literal not supported in DocBook");
	}

	private void ensureBlockElementsOpen() {
		if (!blockDescriptions.isEmpty()) {
			BlockDescription blockDescription = blockDescriptions.peek();
			if (blockDescription.entrySize == 0 && blockDescription.nestedElementNames != null) {
				openBlockEntry(blockDescription, blockDescription.nestedElementNames);
			}
		}
	}

	@Override
	public void entityReference(String entity) {
		ensureBlockElementsOpen();
		if (entity.startsWith("#")) { //$NON-NLS-1$
			String numeric = entity.substring(1);
			int base = 10;
			if (numeric.startsWith("x")) { //$NON-NLS-1$
				numeric = entity.substring(1);
				base = 16;
			}
			int unicodeValue = Integer.parseInt(numeric, base);
			if (entityReferenceToUnicode.contains(unicodeValue)) {
				writer.writeCharacters("" + ((char) unicodeValue)); //$NON-NLS-1$
				return;
			}
		}
		writer.writeEntityRef(entity);
	}

	@Override
	public void image(Attributes attributes, String url) {
		ensureBlockElementsOpen();
		String cssClass = attributes.getCssClass();
		boolean inlined = false;
		if (cssClass != null && CSS_CLASS_INLINE.matcher(cssClass).find()) {
			inlined = true;
		}
		emitImage(attributes, url, inlined);
	}

	private void emitImage(Attributes attributes, String url, boolean inline) {
		// see http://www.docbook.org/tdg/en/html/imagedata-x.html
		ensureBlockElementsOpen();
		writer.writeStartElement(inline ? "inlinemediaobject" : "mediaobject"); //$NON-NLS-1$ //$NON-NLS-2$
		applyAttributes(attributes);
		writer.writeStartElement("imageobject"); //$NON-NLS-1$
		writer.writeEmptyElement("imagedata"); //$NON-NLS-1$
		writer.writeAttribute("fileref", makeUrlAbsolute(url)); //$NON-NLS-1$
		String cssStyle = attributes.getCssStyle();
		if (cssStyle != null) {
			String width = null;
			String depth = null;
			Iterator<CssRule> ruleIterator = new CssParser().createRuleIterator(cssStyle);
			while (ruleIterator.hasNext()) {
				CssRule rule = ruleIterator.next();
				if ("width".equals(rule.name)) { //$NON-NLS-1$
					width = rule.value;
				} else if ("height".equals(rule.name)) { //$NON-NLS-1$
					depth = rule.value;
				}
			}
			if (width != null) {
				Matcher matcher = PERCENTAGE.matcher(width);
				if (matcher.matches()) {
					writer.writeAttribute("scale", matcher.group(1)); //$NON-NLS-1$
				} else {
					writer.writeAttribute("width", width); //$NON-NLS-1$
					if (depth != null) {
						writer.writeAttribute("depth", depth); //$NON-NLS-1$
					}
				}
			}
		}
		writer.writeEndElement(); // imageobject
		writer.writeEndElement(); // inlinemediaobject or mediaobject
	}

	@Override
	public void imageLink(Attributes linkAttributes, final Attributes imageAttributes, String href,
			final String imageUrl) {
		link(linkAttributes, href, new ContentEmitter() {
			public void emit() {
				emitImage(imageAttributes, imageUrl, true);
			}
		});
	}

	@Override
	public void lineBreak() {
		ensureBlockElementsOpen();
		// no equivalent in DocBook.
		characters("\n"); //$NON-NLS-1$
	}

	private BlockDescription findBlockDescription(BlockType type) {
		for (int x = blockDescriptions.size() - 1; x >= 0; --x) {
			BlockDescription blockDescription = blockDescriptions.get(x);
			if (blockDescription.type == type) {
				return blockDescription;
			}
		}
		return null;
	}

    private static class BlockDescription {
        BlockType type;

        int size;

        int entrySize; // the size of an entry, if it is open, otherwise 0

        final String[] nestedElementNames;

        final boolean closeElementsOnBlockStart;

        public BlockDescription(DocumentBuilder.BlockType type, int size, String[] nestedElementNames, boolean closeElementsOnBlockStart) {
            this.size = size;
            this.entrySize = nestedElementNames == null ? 0 : nestedElementNames.length;
            this.type = type;
            this.nestedElementNames = nestedElementNames;
            this.closeElementsOnBlockStart = closeElementsOnBlockStart;
        }
    }

    private static class HeadingDescription {

        int level;
        Attributes attributes;

        private HeadingDescription(int level, Attributes attributes) {
            this.level = level;
            this.attributes = attributes;
        }
    }

	/**
	 * Indicate if this builder should generate an automatic glossary if acronyms are used. When the automatic glossary
	 * is enabled and acronyms are used in the document, then an <code>appendix</code> with title 'Glossary' is added to
	 * the document, with a <code>glosslist</code> generated for all of the acronyms that appear in the document. The
	 * default is true.
	 */
	public boolean isAutomaticGlossary() {
		return automaticGlossary;
	}

	/**
	 * Indicate if this builder should generate an automatic glossary if acronyms are used. When the automatic glossary
	 * is enabled and acronyms are used in the document, then an <code>appendix</code> with title 'Glossary' is added to
	 * the document, with a <code>glosslist</code> generated for all of the acronyms that appear in the document. The
	 * default is true.
	 */
	public void setAutomaticGlossary(boolean automaticGlossary) {
		this.automaticGlossary = automaticGlossary;
	}

}
