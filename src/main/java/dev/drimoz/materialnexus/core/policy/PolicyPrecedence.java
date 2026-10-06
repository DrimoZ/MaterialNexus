package dev.drimoz.materialnexus.core.policy;

/** Where a decision came from, weakest first: Default < Global < Material < Form < Explicit Resource Override. */
public enum PolicyPrecedence {
    DEFAULT,
    GLOBAL,
    MATERIAL,
    FORM,
    EXPLICIT_RESOURCE_OVERRIDE
}
