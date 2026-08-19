package com.naztech.lending.rules;

import com.naztech.lending.common.api.ApiResponse;
import com.naztech.lending.rules.dto.RuleAttributeResponse;
import com.naztech.lending.rules.dto.RuleGroupConfigResponse;
import com.naztech.lending.rules.service.RuleConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The rule configuration, read.
 *
 * <p>Read only for now. Editing rules through an API is Milestone 21's problem,
 * because a criterion that decides who may borrow needs the maker and checker
 * that milestone introduces - and shipping the write endpoint first would mean
 * shipping a way to change lending policy with a single click.
 */
@RestController
@RequestMapping("/api/v1/rules")
@Tag(name = "Rules", description = "The configured eligibility criteria")
public class RuleController {

    private final RuleConfigurationService rules;

    public RuleController(RuleConfigurationService rules) {
        this.rules = rules;
    }

    @GetMapping("/groups")
    @PreAuthorize("hasAuthority('rules.view')")
    @Operation(summary = "The configured rule groups",
            description = "Every group with its rules, each spelt out in the same words that "
                    + "appear in a decline, so a banker can see exactly what was asked.")
    public ApiResponse<List<RuleGroupConfigResponse>> groups() {
        return ApiResponse.success(rules.groups());
    }

    @GetMapping("/attributes")
    @PreAuthorize("hasAuthority('rules.view')")
    @Operation(summary = "What a rule may test",
            description = "The attribute catalogue, with the operators each data type accepts. "
                    + "A rule may only name a code that appears here.")
    public ApiResponse<List<RuleAttributeResponse>> attributes() {
        return ApiResponse.success(rules.attributes());
    }
}
