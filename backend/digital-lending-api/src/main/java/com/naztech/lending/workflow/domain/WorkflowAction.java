package com.naztech.lending.workflow.domain;

/**
 * Something a person can do to an application.
 *
 * <p>An enum here and text in the database, which is the usual trade: the
 * database constrains the column so a typo in configuration is refused, and Java
 * gets exhaustive switches. Adding an action needs both, and that is the point -
 * an action nothing can perform is worse than no action at all.
 *
 * <p>{@code VIEW} and {@code EDIT} are grants rather than moves: they appear in
 * the role/state map and never in the transition table, because looking at a
 * file does not change where it is.
 */
public enum WorkflowAction {

    VIEW(false),
    EDIT(false),

    SUBMIT(true),
    RECOMMEND(true),
    RETURN(true),
    REJECT(true),
    ALLOCATE(true),
    QUERY(true),
    APPROVE(true),
    APPROVE_WITH_CONDITION(true),
    ESCALATE(true),
    DISBURSE(true),
    CANCEL(true);

    private final boolean moves;

    WorkflowAction(boolean moves) {
        this.moves = moves;
    }

    /** True when performing this action sends the application somewhere else. */
    public boolean movesTheFile() {
        return moves;
    }
}
