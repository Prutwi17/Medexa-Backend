package com.medexa.invoice.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.medexa.invoice.Invoice;
import com.medexa.order.OrderItem;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PdfInvoiceGenerator {

    private static final Color BRAND_GREEN = new Color(11, 110, 79);
    private static final Color SOFT_SURFACE = new Color(243, 250, 247);
    private static final Color BORDER_GRAY = new Color(216, 224, 220);
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.of("Asia/Kolkata"));

    public byte[] render(Invoice invoice, List<OrderItem> items) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, BRAND_GREEN);
            Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
            Font subLabelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.DARK_GRAY);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
            Font mutedFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

            // Header Banner
            PdfPTable headerTable = new PdfPTable(new float[]{1.4f, 1f});
            headerTable.setWidthPercentage(100);

            PdfPCell brandCell = new PdfPCell();
            brandCell.setBorder(Rectangle.NO_BORDER);
            brandCell.addElement(new Paragraph("MEDEXA", brandFont));
            brandCell.addElement(new Paragraph("Multi-Vendor Licensed Pharmacy Marketplace", mutedFont));
            headerTable.addCell(brandCell);

            PdfPCell metaCell = new PdfPCell();
            metaCell.setBorder(Rectangle.NO_BORDER);
            metaCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Paragraph invTitle = new Paragraph(
                    invoice.isTaxApplicable() ? "TAX & ORDER INVOICE" : "ORDER RECEIPT / INVOICE",
                    headingFont
            );
            invTitle.setAlignment(Element.ALIGN_RIGHT);
            metaCell.addElement(invTitle);
            Paragraph invNo = new Paragraph("Invoice #: " + invoice.getInvoiceNumber(), bodyFont);
            invNo.setAlignment(Element.ALIGN_RIGHT);
            metaCell.addElement(invNo);
            Paragraph ordNo = new Paragraph("Order #: " + invoice.getOrder().getOrderNumber(), bodyFont);
            ordNo.setAlignment(Element.ALIGN_RIGHT);
            metaCell.addElement(ordNo);
            Paragraph issued = new Paragraph("Issued: " + DATE_FMT.format(invoice.getIssuedAt()), mutedFont);
            issued.setAlignment(Element.ALIGN_RIGHT);
            metaCell.addElement(issued);
            headerTable.addCell(metaCell);
            document.add(headerTable);

            document.add(new Paragraph(" "));

            // Party Details Table (Pharmacy & Customer)
            PdfPTable partyTable = new PdfPTable(new float[]{1f, 1f});
            partyTable.setWidthPercentage(100);
            partyTable.setSpacingAfter(14f);

            PdfPCell pharmacyCell = new PdfPCell();
            pharmacyCell.setBackgroundColor(SOFT_SURFACE);
            pharmacyCell.setBorderColor(BORDER_GRAY);
            pharmacyCell.setPadding(8f);
            pharmacyCell.addElement(new Paragraph("DISPENSING PHARMACY", subLabelFont));
            pharmacyCell.addElement(new Paragraph(invoice.getPharmacyName(), headingFont));
            pharmacyCell.addElement(new Paragraph(invoice.getPharmacyAddress(), bodyFont));
            partyTable.addCell(pharmacyCell);

            PdfPCell customerCell = new PdfPCell();
            customerCell.setBackgroundColor(Color.WHITE);
            customerCell.setBorderColor(BORDER_GRAY);
            customerCell.setPadding(8f);
            customerCell.addElement(new Paragraph("BILLED / DELIVERED TO", subLabelFont));
            customerCell.addElement(new Paragraph(invoice.getCustomerName(), headingFont));
            customerCell.addElement(new Paragraph(invoice.getCustomerAddress(), bodyFont));
            customerCell.addElement(new Paragraph("Payment Ref: " + safeStr(invoice.getPaymentReference()), mutedFont));
            customerCell.addElement(new Paragraph("Payment Status: " + invoice.getPaymentStatus(), subLabelFont));
            partyTable.addCell(customerCell);

            document.add(partyTable);

            // Line Items Table
            PdfPTable itemTable = new PdfPTable(new float[]{2.4f, 0.7f, 0.9f, 0.9f, 1.0f});
            itemTable.setWidthPercentage(100);
            itemTable.setSpacingAfter(12f);

            addHeaderCell(itemTable, "Medicine & Pack", bodyFont);
            addHeaderCell(itemTable, "Qty", bodyFont);
            addHeaderCell(itemTable, "Unit Price", bodyFont);
            addHeaderCell(itemTable, "Tax", bodyFont);
            addHeaderCell(itemTable, "Item Total", bodyFont);

            for (OrderItem item : items) {
                addBodyCell(
                        itemTable,
                        item.getMedicineName() + " (" + item.getBrand() + " • " + item.getPackSize() + ")"
                                + (item.isPrescriptionRequired() ? " [Rx]" : ""),
                        bodyFont,
                        Element.ALIGN_LEFT
                );
                addBodyCell(itemTable, String.valueOf(item.getQuantity()), bodyFont, Element.ALIGN_RIGHT);
                addBodyCell(itemTable, formatInr(item.getUnitPrice()), bodyFont, Element.ALIGN_RIGHT);
                addBodyCell(itemTable, formatInr(item.getTaxAmount()), bodyFont, Element.ALIGN_RIGHT);
                addBodyCell(itemTable, formatInr(item.getLineSubtotal()), bodyFont, Element.ALIGN_RIGHT);
            }
            document.add(itemTable);

            // Totals Summary Table
            PdfPTable totalsTable = new PdfPTable(new float[]{2f, 1f});
            totalsTable.setWidthPercentage(50);
            totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalsTable.setSpacingAfter(16f);

            addTotalRow(totalsTable, "Items Subtotal", formatInr(invoice.getItemsSubtotal()), bodyFont);
            addTotalRow(totalsTable, "Discount", "-" + formatInr(invoice.getDiscountAmount()), bodyFont);
            addTotalRow(totalsTable, "Delivery Charge", formatInr(invoice.getDeliveryFee()), bodyFont);
            if (invoice.isTaxApplicable()) {
                addTotalRow(totalsTable, "Applicable Tax", formatInr(invoice.getTaxAmount()), bodyFont);
            } else {
                addTotalRow(totalsTable, "Applicable Tax", "Rs 0.00 (N/A)", bodyFont);
            }
            addTotalRow(totalsTable, "Total Paid", formatInr(invoice.getGrandTotal()), headingFont);

            document.add(totalsTable);

            if (invoice.getTaxNote() != null && !invoice.getTaxNote().isBlank()) {
                document.add(new Paragraph("Tax & Compliance Note: " + invoice.getTaxNote(), mutedFont));
            }
            document.add(new Paragraph(
                    "Generated electronically by Medexa Healthcare Marketplace.",
                    mutedFont
            ));

            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate PDF invoice.", ex);
        }
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(SOFT_SURFACE);
        cell.setBorderColor(BORDER_GRAY);
        cell.setPadding(6f);
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorderColor(BORDER_GRAY);
        cell.setPadding(6f);
        cell.setHorizontalAlignment(align);
        table.addCell(cell);
    }

    private void addTotalRow(PdfPTable table, String label, String value, Font font) {
        PdfPCell c1 = new PdfPCell(new Phrase(label, font));
        c1.setBorder(Rectangle.BOTTOM);
        c1.setBorderColor(BORDER_GRAY);
        c1.setPadding(5f);
        table.addCell(c1);

        PdfPCell c2 = new PdfPCell(new Phrase(value, font));
        c2.setBorder(Rectangle.BOTTOM);
        c2.setBorderColor(BORDER_GRAY);
        c2.setPadding(5f);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(c2);
    }

    private String formatInr(BigDecimal amount) {
        BigDecimal safe = amount != null ? amount : BigDecimal.ZERO;
        return "Rs " + String.format("%.2f", safe);
    }

    private String safeStr(String value) {
        return value != null && !value.isBlank() ? value : "Pending";
    }
}
