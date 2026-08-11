package com.safjnest.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ValidationUtilsTest {

    @Test
    public void validRequiresEveryValueToBePresent() {
        assertTrue(ValidationUtils.valid("value", 1, new Object()));
        assertFalse(ValidationUtils.valid());
        assertFalse(ValidationUtils.valid("value", null));
        assertFalse(ValidationUtils.valid("value", "  "));
    }
}
