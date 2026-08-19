package com.naztech.lending.workflow.domain;

/** Where a state sits in the life of an application. */
public enum StateType {

    /** An application may begin here. Returns land on one of these too. */
    INITIAL,

    INTERMEDIATE,

    /** Nothing leaves. The transition table holds no move out of one. */
    TERMINAL
}
