package com.farelo.api.fiscal;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * FARELO-172: creates an unsigned NFC-e 4.00 homologation draft from explicit
 * fiscal data. It is deliberately not a transmission document: signature,
 * CSC-dependent QR code, current XSD validation and SEFAZ authorization are
 * separate gates. No database values or tax defaults are inferred here.
 */
public final class NfceXmlBuilder {
    private static final String NS = "http://www.portalfiscal.inf.br/nfe";
    private static final String HOMOLOGATION_PRODUCT_NAME =
            "NOTA FISCAL EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL";
    private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.ofPattern("yyMM", Locale.ROOT);
    private static final DateTimeFormatter XML_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT);
    private static final String ZERO = "0.00";

    public record Issuer(String cnpj, String legalName, String stateRegistration,
                         String crt, Address address) { }

    public record Address(String street, String number, String district,
                          String cityCode, String city, String postalCode) { }

    /** CST 99 is supported only with explicit zero base, rate and amount. */
    public record OtherContribution(BigDecimal base, BigDecimal rate, BigDecimal amount) { }

    /** ICMS and contribution branches are selected explicitly by the caller. */
    public record Taxes(String origin, String icmsCode, String pisCst, String cofinsCst,
                        OtherContribution pisOther, OtherContribution cofinsOther) {
        public Taxes(String origin, String icmsCode, String pisCst, String cofinsCst) {
            this(origin, icmsCode, pisCst, cofinsCst, null, null);
        }
    }

    public record Item(String code, String description, String gtin, String ncm, String cest,
                       String cfop, String unit, BigDecimal quantity,
                       BigDecimal unitPrice, BigDecimal total, Taxes taxes) {
        public Item(String code, String description, String gtin, String ncm,
                    String cfop, String unit, BigDecimal quantity,
                    BigDecimal unitPrice, BigDecimal total, Taxes taxes) {
            this(code, description, gtin, ncm, null, cfop, unit, quantity, unitPrice, total, taxes);
        }
    }

    /** This first iteration supports only an exact cash payment, without change. */
    public record Draft(Issuer issuer, String operation, int series, int number,
                        String randomCode, OffsetDateTime issuedAt,
                        List<Item> items, BigDecimal cashPaid) { }

    public record Result(String accessKey, String unsignedXml) { }

    public Result buildHomologation(Draft draft) {
        validate(draft);
        String key = accessKey(draft);
        try {
            StringWriter out = new StringWriter();
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(out);
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("NFe");
            xml.writeDefaultNamespace(NS);
            xml.writeStartElement("infNFe");
            xml.writeAttribute("versao", "4.00");
            xml.writeAttribute("Id", "NFe" + key);
            writeIde(xml, draft, key);
            writeIssuer(xml, draft.issuer());
            for (int index = 0; index < draft.items().size(); index++) {
                writeItem(xml, draft.items().get(index), index + 1, draft.issuer().crt());
            }
            writeTotals(xml, draft.items());
            start(xml, "transp"); element(xml, "modFrete", "9"); xml.writeEndElement();
            start(xml, "pag");
            start(xml, "detPag");
            element(xml, "tPag", "01");
            element(xml, "vPag", money(draft.cashPaid()));
            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndElement(); // infNFe
            xml.writeEndElement(); // NFe
            xml.writeEndDocument();
            xml.close();
            return new Result(key, out.toString());
        } catch (XMLStreamException ex) {
            throw new IllegalStateException("Could not build NFC-e XML", ex);
        }
    }

    private static void writeIde(XMLStreamWriter xml, Draft draft, String key) throws XMLStreamException {
        start(xml, "ide");
        element(xml, "cUF", "35");
        element(xml, "cNF", draft.randomCode());
        element(xml, "natOp", draft.operation());
        element(xml, "mod", "65");
        element(xml, "serie", Integer.toString(draft.series()));
        element(xml, "nNF", Integer.toString(draft.number()));
        element(xml, "dhEmi", draft.issuedAt().format(XML_DATE));
        element(xml, "tpNF", "1");
        element(xml, "idDest", "1");
        element(xml, "cMunFG", draft.issuer().address().cityCode());
        element(xml, "tpImp", "4");
        element(xml, "tpEmis", "1");
        element(xml, "cDV", key.substring(43));
        element(xml, "tpAmb", "2");
        element(xml, "finNFe", "1");
        element(xml, "indFinal", "1");
        element(xml, "indPres", "1");
        element(xml, "procEmi", "0");
        element(xml, "verProc", "FareloOS-0.1");
        xml.writeEndElement();
    }

    private static void writeIssuer(XMLStreamWriter xml, Issuer issuer) throws XMLStreamException {
        start(xml, "emit");
        element(xml, "CNPJ", issuer.cnpj());
        element(xml, "xNome", issuer.legalName());
        start(xml, "enderEmit");
        element(xml, "xLgr", issuer.address().street());
        element(xml, "nro", issuer.address().number());
        element(xml, "xBairro", issuer.address().district());
        element(xml, "cMun", issuer.address().cityCode());
        element(xml, "xMun", issuer.address().city());
        element(xml, "UF", "SP");
        element(xml, "CEP", issuer.address().postalCode());
        element(xml, "cPais", "1058");
        element(xml, "xPais", "BRASIL");
        xml.writeEndElement();
        element(xml, "IE", issuer.stateRegistration());
        element(xml, "CRT", issuer.crt());
        xml.writeEndElement();
    }

    private static void writeItem(XMLStreamWriter xml, Item item, int number, String crt)
            throws XMLStreamException {
        start(xml, "det");
        xml.writeAttribute("nItem", Integer.toString(number));
        start(xml, "prod");
        element(xml, "cProd", item.code());
        element(xml, "cEAN", item.gtin());
        element(xml, "xProd", number == 1 ? HOMOLOGATION_PRODUCT_NAME : item.description());
        element(xml, "NCM", item.ncm());
        if (item.cest() != null) element(xml, "CEST", item.cest());
        element(xml, "CFOP", item.cfop());
        element(xml, "uCom", item.unit());
        element(xml, "qCom", quantity(item.quantity()));
        element(xml, "vUnCom", unitPrice(item.unitPrice()));
        element(xml, "vProd", money(item.total()));
        element(xml, "cEANTrib", item.gtin());
        element(xml, "uTrib", item.unit());
        element(xml, "qTrib", quantity(item.quantity()));
        element(xml, "vUnTrib", unitPrice(item.unitPrice()));
        element(xml, "indTot", "1");
        xml.writeEndElement();
        start(xml, "imposto");
        start(xml, "ICMS");
        boolean simple = "1".equals(crt);
        start(xml, simple ? "ICMSSN102" : "ICMS40");
        element(xml, "orig", item.taxes().origin());
        element(xml, simple ? "CSOSN" : "CST", item.taxes().icmsCode());
        xml.writeEndElement();
        xml.writeEndElement();
        writeContribution(xml, "PIS", item.taxes().pisCst(), item.taxes().pisOther());
        writeContribution(xml, "COFINS", item.taxes().cofinsCst(), item.taxes().cofinsOther());
        xml.writeEndElement(); // imposto
        xml.writeEndElement(); // det
    }

    private static void writeContribution(XMLStreamWriter xml, String name, String cst,
                                          OtherContribution other) throws XMLStreamException {
        start(xml, name);
        start(xml, name + ("99".equals(cst) ? "Outr" : "NT"));
        element(xml, "CST", cst);
        if (other != null) {
            element(xml, "vBC", money(other.base()));
            element(xml, "p" + name, money(other.rate()));
            element(xml, "v" + name, money(other.amount()));
        }
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private static void writeTotals(XMLStreamWriter xml, List<Item> items) throws XMLStreamException {
        String total = money(items.stream().map(Item::total).reduce(BigDecimal.ZERO, BigDecimal::add));
        start(xml, "total");
        start(xml, "ICMSTot");
        for (String tag : List.of("vBC", "vICMS", "vICMSDeson", "vFCP", "vBCST", "vST",
                "vFCPST", "vFCPSTRet")) element(xml, tag, ZERO);
        element(xml, "vProd", total);
        for (String tag : List.of("vFrete", "vSeg", "vDesc", "vII", "vIPI", "vIPIDevol",
                "vPIS", "vCOFINS", "vOutro")) element(xml, tag, ZERO);
        element(xml, "vNF", total);
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private static String accessKey(Draft draft) {
        String base = "35" + draft.issuedAt().format(KEY_DATE) + draft.issuer().cnpj()
                + "65" + String.format(Locale.ROOT, "%03d%09d", draft.series(), draft.number())
                + "1" + draft.randomCode();
        int weight = 2;
        int sum = 0;
        for (int i = base.length() - 1; i >= 0; i--) {
            sum += (base.charAt(i) - '0') * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        int check = 11 - sum % 11;
        return base + (check >= 10 ? 0 : check);
    }

    private static void validate(Draft d) {
        require(d != null, "draft is required");
        require(d.issuer() != null && d.issuer().address() != null, "issuer and address are required");
        Issuer e = d.issuer();
        digits(e.cnpj(), 14, "CNPJ");
        require(validCnpj(e.cnpj()), "invalid CNPJ check digits");
        label(e.legalName(), 2, 60, "issuer legalName");
        digits(e.stateRegistration(), 12, "stateRegistration");
        require(List.of("1", "3").contains(e.crt()), "only explicitly supported CRT 1 or 3");
        Address a = e.address();
        label(a.street(), 2, 60, "street");
        label(a.number(), 1, 60, "address number");
        label(a.district(), 2, 60, "district");
        digits(a.cityCode(), 7, "cityCode");
        require(a.cityCode().startsWith("35"), "issuer must be in São Paulo");
        label(a.city(), 2, 60, "city");
        digits(a.postalCode(), 8, "postalCode");
        label(d.operation(), 2, 60, "operation");
        require(d.series() >= 0 && d.series() <= 999, "series out of range");
        require(d.number() >= 1 && d.number() <= 999_999_999, "document number out of range");
        digits(d.randomCode(), 8, "randomCode");
        require(d.issuedAt() != null, "issuedAt is required");
        require(d.issuedAt().getNano() == 0, "issuedAt must be precise to whole seconds");
        require(d.items() != null && !d.items().isEmpty() && d.items().size() <= 990,
                "1 to 990 items are required");
        BigDecimal total = BigDecimal.ZERO;
        for (Item item : d.items()) {
            require(item != null && item.taxes() != null, "item and taxes are required");
            label(item.code(), 1, 60, "product code");
            label(item.description(), 1, 120, "product description");
            require("SEM GTIN".equals(item.gtin()), "only explicit SEM GTIN is supported until GTIN validation");
            digits(item.ncm(), 8, "NCM");
            if (item.cest() != null) digits(item.cest(), 7, "CEST");
            digits(item.cfop(), 4, "CFOP");
            label(item.unit(), 1, 6, "unit");
            decimal(item.quantity(), 4, "quantity");
            decimal(item.unitPrice(), 4, "unitPrice");
            decimal(item.total(), 2, "item total");
            require(item.quantity().precision() - item.quantity().scale() <= 11,
                    "quantity exceeds XML numeric size");
            require(item.unitPrice().precision() - item.unitPrice().scale() <= 11,
                    "unitPrice exceeds XML numeric size");
            require(item.total().precision() - item.total().scale() <= 13,
                    "item total exceeds XML numeric size");
            require(item.quantity().multiply(item.unitPrice()).setScale(2, RoundingMode.HALF_UP)
                    .compareTo(item.total()) == 0, "item amount differs from quantity times unit price");
            Taxes tax = item.taxes();
            require(tax.origin() != null && tax.origin().matches("[0-8]"), "invalid ICMS origin");
            require(("1".equals(e.crt()) ? List.of("102", "103", "300", "400")
                    : List.of("40", "41", "50")).contains(tax.icmsCode()), "unsupported ICMS code for CRT");
            validateContribution(tax.pisCst(), tax.pisOther(), "PIS");
            validateContribution(tax.cofinsCst(), tax.cofinsOther(), "COFINS");
            total = total.add(item.total());
        }
        decimal(d.cashPaid(), 2, "cashPaid");
        require(total.compareTo(d.cashPaid()) == 0, "cash payment must equal invoice total");
        require(total.precision() - total.scale() <= 13, "invoice total exceeds XML numeric size");
    }

    private static void validateContribution(String cst, OtherContribution other, String name) {
        if ("99".equals(cst)) {
            require(other != null && isExplicitZero(other.base()) && isExplicitZero(other.rate())
                    && isExplicitZero(other.amount()), name + " CST 99 requires explicit zero base/rate/amount");
        } else {
            require(List.of("04", "06", "07", "08", "09").contains(cst) && other == null,
                    "unsupported " + name + " CST or unexpected contribution amounts");
        }
    }

    private static boolean isExplicitZero(BigDecimal value) {
        return value != null && value.signum() == 0 && value.scale() <= 2;
    }

    private static boolean validCnpj(String value) {
        if (value.chars().distinct().count() == 1) return false;
        for (int length = 12; length <= 13; length++) {
            int sum = 0;
            int weight = length == 12 ? 5 : 6;
            for (int i = 0; i < length; i++) {
                sum += (value.charAt(i) - '0') * weight;
                weight = weight == 2 ? 9 : weight - 1;
            }
            int digit = sum % 11 < 2 ? 0 : 11 - sum % 11;
            if (digit != value.charAt(length) - '0') return false;
        }
        return true;
    }

    private static void digits(String text, int length, String name) {
        require(text != null && text.matches("[0-9]{" + length + "}"), name + " must have " + length + " digits");
    }

    private static void label(String text, int min, int max, String name) {
        require(text != null && text.length() >= min && text.length() <= max && text.trim().equals(text)
                && text.codePoints().allMatch(cp -> cp == 9 || cp == 10 || cp == 13
                || cp >= 32 && cp <= 0xD7FF || cp >= 0xE000 && cp <= 0xFFFD
                || cp >= 0x10000 && cp <= 0x10FFFF), name + " has invalid text");
    }

    private static void decimal(BigDecimal value, int scale, String name) {
        require(value != null && value.signum() > 0 && value.scale() <= scale,
                name + " must be positive with at most " + scale + " decimals");
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String quantity(BigDecimal value) {
        return value.setScale(4, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String unitPrice(BigDecimal value) {
        return value.setScale(4, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static void start(XMLStreamWriter xml, String tag) throws XMLStreamException {
        xml.writeStartElement(tag);
    }

    private static void element(XMLStreamWriter xml, String tag, String value) throws XMLStreamException {
        xml.writeStartElement(tag);
        xml.writeCharacters(value);
        xml.writeEndElement();
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }
}
