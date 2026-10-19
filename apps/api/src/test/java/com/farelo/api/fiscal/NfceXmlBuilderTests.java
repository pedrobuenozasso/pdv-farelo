package com.farelo.api.fiscal;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NfceXmlBuilderTests {
    private final NfceXmlBuilder builder = new NfceXmlBuilder();

    @Test
    void buildsDeterministicUnsignedHomologationDraftWithEscapedTextAndValidAccessKey() throws Exception {
        NfceXmlBuilder.Draft draft = draft("Cafe & bolo <especial>", "12.50", "12.50");
        NfceXmlBuilder.Result result = builder.buildHomologation(draft);
        Document xml = parse(result.unsignedXml());

        assertEquals(44, result.accessKey().length());
        assertEquals("http://www.portalfiscal.inf.br/nfe", xml.getDocumentElement().getNamespaceURI());
        assertEquals(result.accessKey(), builder.buildHomologation(draft).accessKey());
        assertEquals("NFe" + result.accessKey(), xml.getElementsByTagName("infNFe").item(0)
                .getAttributes().getNamedItem("Id").getNodeValue());
        assertEquals("2", text(xml, "tpAmb"));
        assertEquals("65", text(xml, "mod"));
        assertEquals("NOTA FISCAL EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL",
                text(xml, "xProd"));
        assertEquals("12.50", text(xml, "vNF"));
        assertEquals("12.50", text(xml, "vPag"));
        assertFalse(result.unsignedXml().contains("Cafe &amp; bolo &lt;especial&gt;"),
                "first item is overwritten for homologation");
        assertFalse(result.unsignedXml().contains("Signature"));
        assertFalse(result.unsignedXml().contains("infNFeSupl"));
    }

    @Test
    void escapesSecondItemDescriptionInsteadOfAllowingXmlInjection() throws Exception {
        NfceXmlBuilder.Draft one = draft("Cafe", "12.50", "12.50");
        NfceXmlBuilder.Item second = new NfceXmlBuilder.Item("P2", "Bolo & <cafe>", "SEM GTIN",
                "19059090", "5102", "UN", new BigDecimal("1.0000"), new BigDecimal("5.0000"),
                new BigDecimal("5.00"), new NfceXmlBuilder.Taxes("0", "102", "07", "07"));
        NfceXmlBuilder.Draft two = new NfceXmlBuilder.Draft(one.issuer(), one.operation(), one.series(),
                one.number(), one.randomCode(), one.issuedAt(), List.of(one.items().get(0), second),
                new BigDecimal("17.50"));
        NfceXmlBuilder.Result result = builder.buildHomologation(two);
        Document xml = parse(result.unsignedXml());
        assertEquals("Bolo & <cafe>", xml.getElementsByTagName("xProd").item(1).getTextContent());
        assertTrue(result.unsignedXml().contains("Bolo &amp; &lt;cafe&gt;"));
        assertEquals("17.50", text(xml, "vNF"));
    }

    @Test
    void rejectsIncompleteTaxOrMonetaryDataBeforeCreatingXml() {
        NfceXmlBuilder.Draft good = draft("Cafe", "12.50", "12.50");
        assertThrows(IllegalArgumentException.class,
                () -> builder.buildHomologation(draft("Cafe", "0.00", "0.00")));
        assertThrows(IllegalArgumentException.class,
                () -> builder.buildHomologation(draft("Cafe", "12.51", "12.51")));
        assertThrows(IllegalArgumentException.class,
                () -> builder.buildHomologation(draft("Cafe", "12.50", "13.00")));
        NfceXmlBuilder.Item item = good.items().get(0);
        NfceXmlBuilder.Item withoutTax = new NfceXmlBuilder.Item(item.code(), item.description(), item.gtin(),
                item.ncm(), item.cfop(), item.unit(), item.quantity(), item.unitPrice(), item.total(), null);
        assertThrows(IllegalArgumentException.class,
                () -> builder.buildHomologation(new NfceXmlBuilder.Draft(good.issuer(), good.operation(),
                        good.series(), good.number(), good.randomCode(), good.issuedAt(),
                        List.of(withoutTax), good.cashPaid())));
    }

    @Test
    void rejectsInventedGtinAndMismatchedTaxRegime() {
        NfceXmlBuilder.Draft good = draft("Cafe", "12.50", "12.50");
        NfceXmlBuilder.Item item = good.items().get(0);
        NfceXmlBuilder.Item badGtin = new NfceXmlBuilder.Item(item.code(), item.description(), "7891234567895",
                item.ncm(), item.cfop(), item.unit(), item.quantity(), item.unitPrice(), item.total(),
                item.taxes());
        assertThrows(IllegalArgumentException.class, () -> builder.buildHomologation(replaceItem(good, badGtin)));
        NfceXmlBuilder.Item mismatched = new NfceXmlBuilder.Item(item.code(), item.description(), item.gtin(), item.ncm(), item.cfop(),
                item.unit(), item.quantity(), item.unitPrice(), item.total(),
                new NfceXmlBuilder.Taxes("0", "40", "07", "07"));
        assertThrows(IllegalArgumentException.class,
                () -> builder.buildHomologation(replaceItem(good, mismatched)));
        NfceXmlBuilder.Item realWorldPendingTax = new NfceXmlBuilder.Item(item.code(), item.description(),
                item.gtin(), item.ncm(), item.cfop(), item.unit(), item.quantity(), item.unitPrice(),
                item.total(), new NfceXmlBuilder.Taxes("0", "102", "99", "99"));
        assertThrows(IllegalArgumentException.class,
                () -> builder.buildHomologation(replaceItem(good, realWorldPendingTax)));
    }

    @Test
    void writesExplicitRegimeNormalIcMs40BranchWithoutAssumingSimpleRegime() throws Exception {
        NfceXmlBuilder.Draft simple = draft("Cafe", "12.50", "12.50");
        NfceXmlBuilder.Issuer issuer = new NfceXmlBuilder.Issuer(simple.issuer().cnpj(),
                simple.issuer().legalName(), simple.issuer().stateRegistration(), "3",
                simple.issuer().address());
        NfceXmlBuilder.Item original = simple.items().get(0);
        NfceXmlBuilder.Item item = new NfceXmlBuilder.Item(original.code(), original.description(),
                original.gtin(), original.ncm(), original.cfop(), original.unit(), original.quantity(),
                original.unitPrice(), original.total(), new NfceXmlBuilder.Taxes("0", "40", "07", "07"));
        NfceXmlBuilder.Draft normal = new NfceXmlBuilder.Draft(issuer, simple.operation(), simple.series(),
                simple.number(), simple.randomCode(), simple.issuedAt(), List.of(item), simple.cashPaid());
        Document xml = parse(builder.buildHomologation(normal).unsignedXml());
        assertEquals("40", text(xml, "CST"));
        assertEquals(1, xml.getElementsByTagName("ICMS40").getLength());
        assertEquals(0, xml.getElementsByTagName("ICMSSN102").getLength());
    }

    @Test
    void writesObservedFareloTaxBranchesOnlyWhenZeroAmountsAndCestAreExplicit() throws Exception {
        NfceXmlBuilder.Draft source = draft("Cafe", "12.50", "12.50");
        NfceXmlBuilder.Item item = source.items().get(0);
        NfceXmlBuilder.OtherContribution zero = new NfceXmlBuilder.OtherContribution(
                new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"));
        NfceXmlBuilder.Taxes taxes = new NfceXmlBuilder.Taxes("0", "102", "99", "99", zero, zero);
        NfceXmlBuilder.Item withCest = new NfceXmlBuilder.Item(item.code(), item.description(),
                item.gtin(), item.ncm(), "0300100", item.cfop(), item.unit(), item.quantity(),
                item.unitPrice(), item.total(), taxes);

        Document xml = parse(builder.buildHomologation(replaceItem(source, withCest)).unsignedXml());

        assertEquals("0300100", text(xml, "CEST"));
        assertEquals(1, xml.getElementsByTagName("ICMSSN102").getLength());
        assertEquals(1, xml.getElementsByTagName("PISOutr").getLength());
        assertEquals(1, xml.getElementsByTagName("COFINSOutr").getLength());
        assertEquals(0, xml.getElementsByTagName("PISNT").getLength());
        assertEquals("0.00", text(xml, "vPIS"));
        assertEquals("0.00", text(xml, "vCOFINS"));
    }

    @Test
    void rejectsUnapprovedOtherContributionAmountsAndInvalidCest() {
        NfceXmlBuilder.Draft source = draft("Cafe", "12.50", "12.50");
        NfceXmlBuilder.Item item = source.items().get(0);
        NfceXmlBuilder.OtherContribution zero = new NfceXmlBuilder.OtherContribution(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        NfceXmlBuilder.OtherContribution nonzero = new NfceXmlBuilder.OtherContribution(
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.01"));
        NfceXmlBuilder.Item badTax = new NfceXmlBuilder.Item(item.code(), item.description(),
                item.gtin(), item.ncm(), item.cfop(), item.unit(), item.quantity(),
                item.unitPrice(), item.total(),
                new NfceXmlBuilder.Taxes("0", "102", "99", "99", nonzero, zero));
        assertThrows(IllegalArgumentException.class,
                () -> builder.buildHomologation(replaceItem(source, badTax)));
        NfceXmlBuilder.Item badCest = new NfceXmlBuilder.Item(item.code(), item.description(),
                item.gtin(), item.ncm(), "123", item.cfop(), item.unit(), item.quantity(),
                item.unitPrice(), item.total(), item.taxes());
        assertThrows(IllegalArgumentException.class,
                () -> builder.buildHomologation(replaceItem(source, badCest)));
    }

    private static NfceXmlBuilder.Draft replaceItem(NfceXmlBuilder.Draft source, NfceXmlBuilder.Item item) {
        return new NfceXmlBuilder.Draft(source.issuer(), source.operation(), source.series(), source.number(),
                source.randomCode(), source.issuedAt(), List.of(item), source.cashPaid());
    }

    private static NfceXmlBuilder.Draft draft(String description, String itemTotal, String cashPaid) {
        NfceXmlBuilder.Address address = new NfceXmlBuilder.Address("Rua Exemplo", "100", "Centro",
                "3550308", "Sao Paulo", "01001000");
        NfceXmlBuilder.Issuer issuer = new NfceXmlBuilder.Issuer("11222333000181", "Empresa Exemplo LTDA",
                "123456789012", "1", address);
        NfceXmlBuilder.Item item = new NfceXmlBuilder.Item("P1", description, "SEM GTIN", "19059090",
                "5102", "UN", new BigDecimal("1.0000"), new BigDecimal("12.5000"),
                new BigDecimal(itemTotal), new NfceXmlBuilder.Taxes("0", "102", "07", "07"));
        return new NfceXmlBuilder.Draft(issuer, "VENDA", 1, 123, "12345678",
                OffsetDateTime.parse("2026-10-18T12:00:00-03:00"), List.of(item), new BigDecimal(cashPaid));
    }

    private static Document parse(String value) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(value)));
    }

    private static String text(Document doc, String tag) {
        return doc.getElementsByTagName(tag).item(0).getTextContent();
    }
}
