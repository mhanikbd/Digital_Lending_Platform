package com.naztech.lending.application.domain;

/**
 * What kind of thing a query asks for, so a screen can offer an upload rather
 * than a text box when the answer is a document.
 */
public enum QueryType {
    INFORMATION,
    DOCUMENT,
    CLARIFICATION
}
