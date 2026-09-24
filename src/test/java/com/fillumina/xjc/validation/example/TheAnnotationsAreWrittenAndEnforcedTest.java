package com.fillumina.xjc.validation.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.invoice.AddressType;
import com.example.invoice.CurrencyType;
import com.example.invoice.CustomerType;
import com.example.invoice.InvoiceType;
import com.example.invoice.LineType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.junit.jupiter.api.Test;

/**
 * The functional test of this wiring, and the description of what the plugin writes for
 * {@code src/main/xsd/invoice.xsd}.
 *
 * <p>The build has already run the plugin and compiled what it wrote, so a plugin that writes
 * something that does not compile fails before this test runs. What is left to check is the two
 * halves of the promise: that the annotations come from the schema, read here from the compiled
 * classes, and that Hibernate Validator enforces them, read here from the violations of an invoice
 * that breaks them.
 */
class TheAnnotationsAreWrittenAndEnforcedTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void theConstraintsOfTheSchemaAreWrittenOnTheGeneratedFields() throws Exception {
        assertNotNull(annotation(InvoiceType.class, "number", NotNull.class));
        assertEquals("[A-Z]{2}[0-9]{5}",
                annotation(InvoiceType.class, "number", Pattern.class).regexp());
        assertNotNull(annotation(InvoiceType.class, "issued", NotNull.class));

        // a required complex element is a @Valid one, so its own constraints are reached
        assertNotNull(annotation(InvoiceType.class, "customer", NotNull.class));
        assertNotNull(annotation(InvoiceType.class, "customer", jakarta.validation.Valid.class));

        // an optional element constrains nothing by being absent
        assertNull(annotation(InvoiceType.class, "note", NotNull.class));

        assertEquals("0", annotation(InvoiceType.class, "discount", DecimalMin.class).value());
        assertTrue(annotation(InvoiceType.class, "discount", DecimalMin.class).inclusive());
        assertEquals("100", annotation(InvoiceType.class, "discount", DecimalMax.class).value());

        Digits digits = annotation(InvoiceType.class, "total", Digits.class);
        assertEquals(12, digits.integer());
        assertEquals(2, digits.fraction());

        // the schema pins the boolean, so the plugin asserts it
        assertNotNull(annotation(InvoiceType.class, "paid", AssertFalse.class));

        // an attribute is constrained as an element is
        assertNotNull(annotation(InvoiceType.class, "currency", NotNull.class));
        assertEquals("[a-z]{2}(-[A-Z]{2})?",
                annotation(InvoiceType.class, "language", Pattern.class).regexp());

        assertNotNull(annotation(CustomerType.class, "id", NotNull.class));
        assertEquals("999999", annotation(CustomerType.class, "id", DecimalMax.class).value());
    }

    @Test
    void theCollectionAndItsItemsCarryDifferentConstraints() throws Exception {
        // the cardinality of the element goes on the field
        assertNotNull(annotation(InvoiceType.class, "line", NotNull.class));
        assertEquals(1, annotation(InvoiceType.class, "line", Size.class).min());
        assertEquals(10, annotation(InvoiceType.class, "line", Size.class).max());

        // and the item of a complex type is a @Valid one, on the type argument
        assertTrue(itemOf(InvoiceType.class, "line")
                .isAnnotationPresent(jakarta.validation.Valid.class));

        // an optional element with no bound carries no cardinality constraint
        assertNull(annotation(InvoiceType.class, "attachment", Size.class));
        assertTrue(itemOf(InvoiceType.class, "attachment")
                .isAnnotationPresent(jakarta.validation.Valid.class));

        // the facets of the item type of an xs:list go on the type argument too
        Size item = itemOf(InvoiceType.class, "tags").getAnnotation(Size.class);
        assertEquals(2, item.min());
        assertEquals(20, item.max());
    }

    @Test
    void theOverrideOptionReplacedTheMessageOfOneAnnotation() throws Exception {
        assertEquals("at most 200 characters",
                annotation(InvoiceType.class, "note", Size.class).message());
        assertEquals(200, annotation(InvoiceType.class, "note", Size.class).max());
    }

    @Test
    void anInvoiceThatBreaksNoRuleHasNoViolation() throws Exception {
        assertTrue(violations(validInvoice()).isEmpty(), violations(validInvoice()).toString());
    }

    @Test
    void theViolationsOfAnInvoiceAreTheOnesTheSchemaStates() throws Exception {
        InvoiceType invoice = validInvoice();
        invoice.setNumber("not a number");
        invoice.getLine().get(0).setQuantity(1000);
        invoice.setPaid(true);
        invoice.setTotal(new BigDecimal("1000000000000.00"));

        List<String> messages = violations(invoice);

        assertTrue(messages.stream().anyMatch(m -> m.startsWith("number ") && m.contains("[A-Z]{2}")),
                messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("line[0].quantity ")),
                messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("paid ")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("total ")), messages.toString());
    }

    @Test
    void theCustomerOfAnInvoiceIsValidatedThroughTheCascade() throws Exception {
        InvoiceType invoice = validInvoice();
        invoice.setCustomer(null);
        assertTrue(violations(invoice).stream().anyMatch(m -> m.startsWith("customer ")),
                violations(invoice).toString());

        InvoiceType withABadAddress = validInvoice();
        withABadAddress.getCustomer().getAddress().setZip("abc");
        assertTrue(violations(withABadAddress).stream().anyMatch(m -> m.startsWith("customer.address.zip ")),
                violations(withABadAddress).toString());

        InvoiceType withABadTag = validInvoice();
        withABadTag.getTags().set(0, "x");
        assertTrue(violations(withABadTag).stream().anyMatch(m -> m.startsWith("tags[0]")),
                violations(withABadTag).toString());
    }

    private static InvoiceType validInvoice() throws Exception {
        InvoiceType invoice = new InvoiceType();
        invoice.setNumber("AB12345");
        invoice.setIssued(date("2026-01-31"));
        invoice.setCustomer(customer());
        invoice.getLine().add(line("Consulting", 3, "120.50"));
        invoice.setTotal(new BigDecimal("361.50"));
        invoice.setPaid(false);
        invoice.setCurrency(CurrencyType.EUR);
        invoice.setLanguage("en");
        invoice.getTags().add("paid");
        invoice.getTags().add("late");
        return invoice;
    }

    private static CustomerType customer() {
        CustomerType customer = new CustomerType();
        customer.setId(42);
        customer.setName("Acme Ltd");
        customer.setEmail("billing@acme.test");
        customer.setAddress(address());
        return customer;
    }

    private static AddressType address() {
        AddressType address = new AddressType();
        address.setStreet("Main Street 1");
        address.setCity("Springfield");
        address.setZip("12345");
        address.setCountry("IT");
        return address;
    }

    private static LineType line(String description, int quantity, String unitPrice) {
        LineType line = new LineType();
        line.setDescription(description);
        line.setQuantity(quantity);
        line.setUnitPrice(new BigDecimal(unitPrice));
        return line;
    }

    private static XMLGregorianCalendar date(String value) throws Exception {
        return DatatypeFactory.newInstance().newXMLGregorianCalendar(value);
    }

    private static List<String> violations(Object instance) {
        Set<ConstraintViolation<Object>> found = VALIDATOR.validate(instance);
        List<String> messages = new ArrayList<>();
        for (ConstraintViolation<Object> violation : found) {
            messages.add(violation.getPropertyPath() + " " + violation.getMessage());
        }
        return messages;
    }

    private static <A extends Annotation> A annotation(Class<?> type, String field, Class<A> wanted)
            throws Exception {
        return type.getDeclaredField(field).getAnnotation(wanted);
    }

    /** @return the annotated type argument of a collection field, where its items are annotated. */
    private static AnnotatedType itemOf(Class<?> type, String field) throws Exception {
        AnnotatedParameterizedType collection =
                (AnnotatedParameterizedType) type.getDeclaredField(field).getAnnotatedType();
        return collection.getAnnotatedActualTypeArguments()[0];
    }
}
