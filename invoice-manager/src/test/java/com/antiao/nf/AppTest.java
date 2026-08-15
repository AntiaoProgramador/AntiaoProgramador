package com.antiao.nf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppTest {

    @Test
    void shouldExtractInvoiceIdFromApiPath() {
        assertEquals(15L, App.extractInvoiceId("/api/invoices/15/confirm"));
    }

    @Test
    void shouldReturnNullForInvalidPaths() {
        assertNull(App.extractInvoiceId("/api/outros/15/confirm"));
        assertNull(App.extractInvoiceId("/api/invoices/abc/confirm"));
    }
}
