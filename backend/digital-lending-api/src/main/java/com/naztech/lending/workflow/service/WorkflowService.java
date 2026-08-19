package com.naztech.lending.workflow.service;

import com.naztech.lending.common.exception.BusinessException;
import com.naztech.lending.common.exception.ErrorCode;
import com.naztech.lending.workflow.domain.RoleStateMap;
import com.naztech.lending.workflow.domain.StateTransition;
import com.naztech.lending.workflow.domain.WorkflowAction;
import com.naztech.lending.workflow.domain.WorkflowState;
import com.naztech.lending.workflow.repository.RoleStateMapRepository;
import com.naztech.lending.workflow.repository.StateTransitionRepository;
import com.naztech.lending.workflow.repository.WorkflowStateRepository;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The workflow engine.
 *
 * <p>Nothing in this class names a role or a state. It reads three tables - what
 * states exist, who may act in each, and which moves are legal - and answers two
 * questions: what may this person do here, and is this particular move allowed.
 * The specification is blunt about why:
 *
 * <blockquote>Do NOT hard-code role names inside workflow business logic. For
 * example, do not write {@code if (role.equals("BM"))}.</blockquote>
 *
 * <p>The consequence worth stating: a bank that adds a seventh step, a new role,
 * or a different escalation ladder does it with inserts. If any method here ever
 * needs to know what BM stands for, the design has failed.
 *
 * <p>Knows nothing about loan applications either. It is handed a state and a
 * set of roles; what is sitting in that state is the application module's
 * business.
 */
@Service
public class WorkflowService {

    private final WorkflowStateRepository states;
    private final RoleStateMapRepository grants;
    private final StateTransitionRepository transitions;

    public WorkflowService(WorkflowStateRepository states, RoleStateMapRepository grants,
                           StateTransitionRepository transitions) {
        this.states = states;
        this.grants = grants;
        this.transitions = transitions;
    }

    @Transactional(readOnly = true)
    public WorkflowState state(String code) {
        return states.findById(code).orElseThrow(() -> new BusinessException(
                ErrorCode.RESOURCE_NOT_FOUND, "No such workflow state"));
    }

    @Transactional(readOnly = true)
    public List<WorkflowState> allStates() {
        return states.findAllByOrderByDisplayOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<StateTransition> allTransitions() {
        return transitions.findAllForDisplay();
    }

    @Transactional(readOnly = true)
    public List<RoleStateMap> allGrants() {
        return grants.findAllByOrderByStateCodeAscRoleCodeAsc();
    }

    /**
     * What this person may do to a file sitting in this state.
     *
     * <p>Two things have to agree. The role/state map has to grant the action,
     * and the transition table has to offer a move for it - a grant to RECOMMEND
     * from a state nothing recommends out of is a button that would fail when
     * pressed. VIEW and EDIT are the exceptions: they change nothing, so they
     * need no transition.
     *
     * <p>This is what {@code GET /api/v1/loan-applications/{id}/available-actions}
     * answers, and the specification asks for it precisely so a screen never has
     * to work out for itself which buttons to draw.
     */
    @Transactional(readOnly = true)
    public List<AvailableAction> availableActions(String stateCode, Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return List.of();
        }

        Set<WorkflowAction> granted = grantedActions(stateCode, roleCodes);
        if (granted.isEmpty()) {
            return List.of();
        }

        List<AvailableAction> available = new java.util.ArrayList<>();

        if (granted.contains(WorkflowAction.VIEW)) {
            available.add(AvailableAction.standing(WorkflowAction.VIEW, "View"));
        }
        if (granted.contains(WorkflowAction.EDIT)) {
            available.add(AvailableAction.standing(WorkflowAction.EDIT, "Edit"));
        }

        for (StateTransition transition : transitions.findFrom(stateCode)) {
            if (!granted.contains(transition.getAction())) {
                continue;
            }
            if (!transition.isOpenTo(roleCodes)) {
                continue;
            }
            available.add(AvailableAction.moving(transition));
        }

        return List.copyOf(available);
    }

    /**
     * The move to apply, or a refusal that says which of the two gates closed.
     *
     * <p>Kept apart from {@link #availableActions} rather than implemented on
     * top of it, because they answer different questions and are asked at
     * different moments. A screen asks what to draw; this asks whether an
     * attempt that arrived a minute later is still legal. Sharing an
     * implementation would tempt somebody to trust the first answer instead of
     * asking the second.
     *
     * @param preferredToState names the destination when the state and action
     *                         offer more than one and the roles do not narrow it
     *                         to one - the core banking result, which is success
     *                         or failure by outcome rather than by who is asking
     */
    @Transactional(readOnly = true)
    public StateTransition resolve(String stateCode, WorkflowAction action,
                                   Collection<String> roleCodes, String preferredToState) {
        if (!grantedActions(stateCode, roleCodes).contains(action)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "Your role does not permit %s while the application is %s"
                            .formatted(action, stateCode));
        }

        List<StateTransition> candidates = transitions.findFromByAction(stateCode, action).stream()
                .filter(transition -> transition.isOpenTo(roleCodes))
                .toList();

        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "%s is not a move this application can make from %s".formatted(action, stateCode));
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // More than one destination survives. The caller has to say which, and
        // naming one they were not offered is refused rather than guessed at.
        if (preferredToState == null || preferredToState.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "%s from %s can lead to %s. Say which.".formatted(
                            action, stateCode,
                            candidates.stream().map(t -> t.getToState().getCode())
                                    .collect(java.util.stream.Collectors.joining(" or "))));
        }
        return candidates.stream()
                .filter(transition -> transition.getToState().getCode().equals(preferredToState))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "%s is not a destination %s can reach by %s"
                                .formatted(preferredToState, stateCode, action)));
    }

    /** True when the roles allow the action, without resolving a destination. */
    @Transactional(readOnly = true)
    public boolean permits(String stateCode, WorkflowAction action, Collection<String> roleCodes) {
        return grantedActions(stateCode, roleCodes).contains(action);
    }

    private Set<WorkflowAction> grantedActions(String stateCode, Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Set.of();
        }
        // Several roles at once, and the grants add up: holding an extra role
        // must never take an action away.
        return grants.findByStateCodeAndRoleCodeInAndStatus(stateCode, roleCodes, "ACTIVE").stream()
                .map(RoleStateMap::getAction)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * One thing a person may do, as the portal receives it.
     *
     * @param toState null for VIEW and EDIT, which move nothing
     * @param label   what the button says, from the configuration rather than
     *                from whichever team is building the screen
     */
    public record AvailableAction(WorkflowAction action, String label, String toState,
                                  boolean reasonRequired) {

        static AvailableAction standing(WorkflowAction action, String label) {
            return new AvailableAction(action, label, null, false);
        }

        static AvailableAction moving(StateTransition transition) {
            return new AvailableAction(
                    transition.getAction(),
                    transition.getLabel(),
                    transition.getToState().getCode(),
                    transition.isReasonRequired());
        }
    }

    /** The state an application starts in, for a given source channel. */
    @Transactional(readOnly = true)
    public Optional<WorkflowState> initialState(String code) {
        return states.findById(code).filter(WorkflowState::isActive);
    }
}
