package dev.drimoz.materialnexus;

import dev.drimoz.materialnexus.core.policy.PolicyPrecedence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyPrecedenceTest {
    @Test
    void explicitOverrideIsStrongest() {
        assertTrue(PolicyPrecedence.EXPLICIT_RESOURCE_OVERRIDE.ordinal() > PolicyPrecedence.FORM.ordinal());
        assertTrue(PolicyPrecedence.FORM.ordinal() > PolicyPrecedence.MATERIAL.ordinal());
        assertTrue(PolicyPrecedence.MATERIAL.ordinal() > PolicyPrecedence.GLOBAL.ordinal());
    }
}
