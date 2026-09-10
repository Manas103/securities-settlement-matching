package com.manas.settlementmatch.gateway;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Parses a simplified FpML-style trade confirmation.
 *
 * <p><b>Honest framing: this is FpML-style, not FpML.</b> Real FpML is a
 * large family of XML schemas (trade, confirmation, valuation, position
 * reports) with a formal XSD, namespaces, and versioned schema evolution.
 * This parser recognizes one flat, made-up element shape,
 * {@code <tradeConfirmation>} with {@code <tradeReference>},
 * {@code <instrument><isin>}, {@code <quantity>}, {@code <price>},
 * {@code <settlementDate>}, {@code <currency>}, {@code <account>}, and
 * {@code <messageId>}, none of which is validated against any XSD. It
 * exists to exercise "a trade confirmation arrives as XML with a different
 * shape than the FIX and delimited feeds", not to demonstrate FpML schema
 * conformance.
 *
 * <p>Uses {@link DocumentBuilderFactory} with external DTD and external
 * general entity processing disabled: an XML parser that trusts a
 * DOCTYPE from an untrusted upstream feed is an XXE vulnerability, and a
 * post-trade confirmation feed is exactly the kind of input this project
 * should not trust by default.
 */
public final class FpmlConfirmationParser implements TradeMessageParser {

    @Override
    public SourceFormat format() {
        return SourceFormat.FPML;
    }

    @Override
    public NormalizedTradeRecord parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new MalformedTradeMessageException("FpML-style message is blank");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(raw)));
            doc.getDocumentElement().normalize();

            String tradeRef = required(doc, "tradeReference");
            String isin = requiredNested(doc, "instrument", "isin");
            long quantity = Long.parseLong(required(doc, "quantity"));
            BigDecimal price = new BigDecimal(required(doc, "price"));
            LocalDate settlementDate = LocalDate.parse(required(doc, "settlementDate"));
            String currency = required(doc, "currency");
            String account = required(doc, "account");
            String messageId = required(doc, "messageId");

            return new NormalizedTradeRecord(tradeRef, SourceFormat.FPML, messageId, isin, quantity, price,
                    settlementDate, currency, account);
        } catch (MalformedTradeMessageException e) {
            throw e;
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new MalformedTradeMessageException("FpML-style message has an unparseable field: " + raw, e);
        } catch (Exception e) {
            throw new MalformedTradeMessageException("FpML-style message could not be parsed as XML: " + raw, e);
        }
    }

    private String required(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent().isBlank()) {
            throw new MalformedTradeMessageException("FpML-style message missing required element <" + tagName + ">");
        }
        return nodes.item(0).getTextContent().trim();
    }

    private String requiredNested(Document doc, String parentTag, String childTag) {
        NodeList parents = doc.getElementsByTagName(parentTag);
        if (parents.getLength() == 0) {
            throw new MalformedTradeMessageException("FpML-style message missing required element <" + parentTag + ">");
        }
        Element parent = (Element) parents.item(0);
        NodeList children = parent.getElementsByTagName(childTag);
        if (children.getLength() == 0 || children.item(0).getTextContent().isBlank()) {
            throw new MalformedTradeMessageException(
                    "FpML-style message missing required element <" + parentTag + "><" + childTag + ">");
        }
        return children.item(0).getTextContent().trim();
    }
}
