package com.naztech.lending.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.naztech.lending.common.exception.BusinessException;
import com.naztech.lending.workflow.domain.RoleStateMap;
import com.naztech.lending.workflow.domain.StateTransition;
import com.naztech.lending.workflow.domain.WorkflowAction;
import com.naztech.lending.workflow.domain.WorkflowState;
import com.naztech.lending.workflow.repository.RoleStateMapRepository;
import com.naztech.lending.workflow.repository.StateTransitionRepository;
import com.naztech.lending.workflow.repository.WorkflowStateRepository;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The workflow engine.
 *
 * <p>Two gates have to agree before a button appears: the role/state map has to
 * grant the action, and the transition table has to offer a move for it. Most of
 * what follows is about the ways those two can disagree, because a grant with no
 * transition is a button that fails when pressed, and a transition with no grant
 * is a move nobody can make.
 *
 * <p>The test that matters most is the last group: that the engine refuses to
 * guess when a move is ambiguous. Guessing would send an application to the
 * wrong queue and nobody would notice until it arrived.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    private static final List<String> BRANCH_MANAGER = List.of("BM");
    private static final List<String> OPERATIONS = List.of("BOM");
    private static final List<String> ANALYST = List.of("CA");

    @Mock
    private WorkflowStateRepository states;

    @Mock
    private RoleStateMapRepository grants;

    @Mock
    private StateTransitionRepository transitions;

    private WorkflowService workflow;

    @BeforeEach
    void setUp() {
        workflow = new WorkflowService(states, grants, transitions);
    }

    @Test
    void offersAnActionOnlyWhenTheRoleIsGrantedItAndAMoveExists() {
        givenGrants("SO_RECOMMENDED", BRANCH_MANAGER,
                WorkflowAction.VIEW, WorkflowAction.RECOMMEND, WorkflowAction.RETURN);
        givenTransitions("SO_RECOMMENDED",
                transition("SO_RECOMMENDED", "BM_RECOMMENDED", WorkflowAction.RECOMMEND, "BM",
                        "Recommend to head office", false),
                transition("SO_RECOMMENDED", "BM_RETURNED", WorkflowAction.RETURN, null,
                        "Return to sourcing officer", true));

        List<WorkflowService.AvailableAction> actions =
                workflow.availableActions("SO_RECOMMENDED", BRANCH_MANAGER);

        assertThat(actions).extracting(WorkflowService.AvailableAction::action)
                .containsExactly(WorkflowAction.VIEW, WorkflowAction.RECOMMEND,
                        WorkflowAction.RETURN);
    }

    @Test
    void doesNotOfferAGrantedActionThatNoTransitionSupports() {
        // A grant with no move behind it is a button that fails when pressed.
        givenGrants("SO_RECOMMENDED", BRANCH_MANAGER,
                WorkflowAction.VIEW, WorkflowAction.APPROVE);
        givenTransitions("SO_RECOMMENDED",
                transition("SO_RECOMMENDED", "BM_RECOMMENDED", WorkflowAction.RECOMMEND, "BM",
                        "Recommend", false));

        assertThat(workflow.availableActions("SO_RECOMMENDED", BRANCH_MANAGER))
                .extracting(WorkflowService.AvailableAction::action)
                .containsExactly(WorkflowAction.VIEW);
    }

    @Test
    void doesNotOfferATransitionTheRoleWasNeverGranted() {
        givenGrants("SO_RECOMMENDED", ANALYST, WorkflowAction.VIEW);
        givenTransitions("SO_RECOMMENDED",
                transition("SO_RECOMMENDED", "BM_RECOMMENDED", WorkflowAction.RECOMMEND, "BM",
                        "Recommend", false));

        assertThat(workflow.availableActions("SO_RECOMMENDED", ANALYST))
                .extracting(WorkflowService.AvailableAction::action)
                .containsExactly(WorkflowAction.VIEW);
    }

    @Test
    void doesNotOfferAnotherRolesMoveEvenWhenTheActionIsGranted() {
        // The case the actor_role_code column exists for. Operations may
        // RECOMMEND from this state, but not by the branch manager's route.
        givenGrants("SO_RECOMMENDED", OPERATIONS, WorkflowAction.RECOMMEND);
        givenTransitions("SO_RECOMMENDED",
                transition("SO_RECOMMENDED", "BM_RECOMMENDED", WorkflowAction.RECOMMEND, "BM",
                        "Recommend to head office", false),
                transition("SO_RECOMMENDED", "BOM_RECOMMENDED", WorkflowAction.RECOMMEND, "BOM",
                        "Recommend to head office", false));

        assertThat(workflow.availableActions("SO_RECOMMENDED", OPERATIONS))
                .singleElement()
                .satisfies(action -> assertThat(action.toState()).isEqualTo("BOM_RECOMMENDED"));
    }

    @Test
    void grantsFromSeveralRolesAddUp() {
        // Holding an extra role must never take an action away.
        when(grants.findByStateCodeAndRoleCodeInAndStatus(
                anyString(), any(), anyString()))
                .thenReturn(List.of(
                        grant("BM", "SO_RECOMMENDED", WorkflowAction.RECOMMEND),
                        grant("BOM", "SO_RECOMMENDED", WorkflowAction.RETURN)));
        givenTransitions("SO_RECOMMENDED",
                transition("SO_RECOMMENDED", "BM_RECOMMENDED", WorkflowAction.RECOMMEND, "BM",
                        "Recommend", false),
                transition("SO_RECOMMENDED", "BM_RETURNED", WorkflowAction.RETURN, null,
                        "Return", true));

        assertThat(workflow.availableActions("SO_RECOMMENDED", List.of("BM", "BOM")))
                .extracting(WorkflowService.AvailableAction::action)
                .containsExactly(WorkflowAction.RECOMMEND, WorkflowAction.RETURN);
    }

    @Test
    void offersNothingToSomebodyWithNoRoles() {
        assertThat(workflow.availableActions("SO_CREATED", List.of())).isEmpty();
        assertThat(workflow.availableActions("SO_CREATED", null)).isEmpty();
    }

    @Test
    void carriesTheConfiguredLabelAndWhetherAReasonIsNeeded() {
        // The word on the button comes from configuration, so the screen, the
        // history and the notification all say the same thing.
        givenGrants("SO_RECOMMENDED", BRANCH_MANAGER, WorkflowAction.RETURN);
        givenTransitions("SO_RECOMMENDED",
                transition("SO_RECOMMENDED", "BM_RETURNED", WorkflowAction.RETURN, null,
                        "Return to sourcing officer", true));

        assertThat(workflow.availableActions("SO_RECOMMENDED", BRANCH_MANAGER))
                .singleElement()
                .satisfies(action -> {
                    assertThat(action.label()).isEqualTo("Return to sourcing officer");
                    assertThat(action.reasonRequired()).isTrue();
                    assertThat(action.toState()).isEqualTo("BM_RETURNED");
                });
    }

    @Test
    void viewAndEditNeedNoTransitionBecauseTheyMoveNothing() {
        givenGrants("CA_RECEIVED", ANALYST, WorkflowAction.VIEW, WorkflowAction.EDIT);
        givenTransitions("CA_RECEIVED");

        assertThat(workflow.availableActions("CA_RECEIVED", ANALYST))
                .extracting(WorkflowService.AvailableAction::action)
                .containsExactly(WorkflowAction.VIEW, WorkflowAction.EDIT);
        assertThat(workflow.availableActions("CA_RECEIVED", ANALYST))
                .allSatisfy(action -> assertThat(action.toState()).isNull());
    }

    @Test
    void resolvesTheOneMoveARoleCanMake() {
        givenGrants("SO_RECOMMENDED", BRANCH_MANAGER, WorkflowAction.RECOMMEND);
        when(transitions.findFromByAction("SO_RECOMMENDED", WorkflowAction.RECOMMEND))
                .thenReturn(List.of(
                        transition("SO_RECOMMENDED", "BM_RECOMMENDED", WorkflowAction.RECOMMEND,
                                "BM", "Recommend", false),
                        transition("SO_RECOMMENDED", "BOM_RECOMMENDED", WorkflowAction.RECOMMEND,
                                "BOM", "Recommend", false)));

        StateTransition resolved = workflow.resolve(
                "SO_RECOMMENDED", WorkflowAction.RECOMMEND, BRANCH_MANAGER, null);

        assertThat(resolved.getToState().getCode()).isEqualTo("BM_RECOMMENDED");
    }

    @Test
    void refusesAnActionTheRoleDoesNotHold() {
        givenGrants("SO_RECOMMENDED", ANALYST, WorkflowAction.VIEW);

        assertThatThrownBy(() -> workflow.resolve(
                "SO_RECOMMENDED", WorkflowAction.APPROVE, ANALYST, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not permit");
    }

    @Test
    void refusesAnActionTheStateCannotMake() {
        // Granted, but the state offers no such move. A different failure from
        // the one above, and it has to read differently: one is about who you
        // are, the other about where the file is.
        givenGrants("CLOSED", BRANCH_MANAGER, WorkflowAction.RECOMMEND);
        when(transitions.findFromByAction("CLOSED", WorkflowAction.RECOMMEND))
                .thenReturn(List.of());

        assertThatThrownBy(() -> workflow.resolve(
                "CLOSED", WorkflowAction.RECOMMEND, BRANCH_MANAGER, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not a move");
    }

    @Test
    void refusesToGuessWhenTwoDestinationsSurvive() {
        // The core banking result: success or failure, decided by the outcome
        // rather than by who is asking. Guessing would close a disbursement that
        // actually failed.
        givenGrants("SEND_TO_CBS", List.of("CAD"), WorkflowAction.SUBMIT);
        when(transitions.findFromByAction("SEND_TO_CBS", WorkflowAction.SUBMIT))
                .thenReturn(List.of(
                        transition("SEND_TO_CBS", "CBS_SUCCESS", WorkflowAction.SUBMIT, null,
                                "Record success", false),
                        transition("SEND_TO_CBS", "CBS_FAILED", WorkflowAction.SUBMIT, null,
                                "Record failure", true)));

        assertThatThrownBy(() -> workflow.resolve(
                "SEND_TO_CBS", WorkflowAction.SUBMIT, List.of("CAD"), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Say which");
    }

    @Test
    void acceptsANamedDestinationWhenTheMoveIsAmbiguous() {
        givenGrants("SEND_TO_CBS", List.of("CAD"), WorkflowAction.SUBMIT);
        when(transitions.findFromByAction("SEND_TO_CBS", WorkflowAction.SUBMIT))
                .thenReturn(List.of(
                        transition("SEND_TO_CBS", "CBS_SUCCESS", WorkflowAction.SUBMIT, null,
                                "Record success", false),
                        transition("SEND_TO_CBS", "CBS_FAILED", WorkflowAction.SUBMIT, null,
                                "Record failure", true)));

        assertThat(workflow.resolve("SEND_TO_CBS", WorkflowAction.SUBMIT, List.of("CAD"),
                "CBS_FAILED").getToState().getCode()).isEqualTo("CBS_FAILED");
    }

    @Test
    void refusesADestinationThatWasNeverOffered() {
        givenGrants("SEND_TO_CBS", List.of("CAD"), WorkflowAction.SUBMIT);
        when(transitions.findFromByAction("SEND_TO_CBS", WorkflowAction.SUBMIT))
                .thenReturn(List.of(
                        transition("SEND_TO_CBS", "CBS_SUCCESS", WorkflowAction.SUBMIT, null,
                                "Record success", false),
                        transition("SEND_TO_CBS", "CBS_FAILED", WorkflowAction.SUBMIT, null,
                                "Record failure", true)));

        assertThatThrownBy(() -> workflow.resolve("SEND_TO_CBS", WorkflowAction.SUBMIT,
                List.of("CAD"), "CLOSED"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not a destination");
    }

    /* ---- fixtures ------------------------------------------------------- */

    private void givenGrants(String stateCode, List<String> roles, WorkflowAction... actions) {
        List<RoleStateMap> rows = java.util.Arrays.stream(actions)
                .map(action -> grant(roles.get(0), stateCode, action))
                .toList();
        lenient().when(grants.findByStateCodeAndRoleCodeInAndStatus(stateCode, roles, "ACTIVE"))
                .thenReturn(rows);
    }

    private void givenTransitions(String stateCode, StateTransition... rows) {
        lenient().when(transitions.findFrom(stateCode)).thenReturn(List.of(rows));
    }

    /**
     * Entities with no public constructor, built by reflection.
     *
     * <p>Deliberately not solved by adding factory methods to the entities. The
     * workflow is configuration seeded by migration and read by the engine;
     * nothing in production builds a state or a transition, and adding a
     * constructor so a test can would be inventing a write path that does not
     * exist.
     */
    private static WorkflowState state(String code) {
        WorkflowState state = instantiate(WorkflowState.class);
        set(state, "code", code);
        set(state, "name", code);
        return state;
    }

    private static StateTransition transition(String from, String to, WorkflowAction action,
                                              String actorRole, String label,
                                              boolean reasonRequired) {
        StateTransition transition = instantiate(StateTransition.class);
        set(transition, "fromState", state(from));
        set(transition, "toState", state(to));
        set(transition, "action", action);
        set(transition, "actorRoleCode", actorRole);
        set(transition, "label", label);
        set(transition, "reasonRequired", reasonRequired);
        set(transition, "status", "ACTIVE");
        return transition;
    }

    private static RoleStateMap grant(String roleCode, String stateCode, WorkflowAction action) {
        RoleStateMap grant = instantiate(RoleStateMap.class);
        set(grant, "roleCode", roleCode);
        set(grant, "stateCode", stateCode);
        set(grant, "action", action);
        set(grant, "status", "ACTIVE");
        return grant;
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException impossible) {
            throw new IllegalStateException("JPA requires a no-arg constructor", impossible);
        }
    }

    private static void set(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException missing) {
            throw new IllegalStateException(
                    "%s has no field %s".formatted(target.getClass().getSimpleName(), fieldName),
                    missing);
        }
    }
}
