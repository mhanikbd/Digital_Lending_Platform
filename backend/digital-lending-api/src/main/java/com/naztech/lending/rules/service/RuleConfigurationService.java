package com.naztech.lending.rules.service;

import com.naztech.lending.rules.dto.RuleAttributeResponse;
import com.naztech.lending.rules.dto.RuleGroupConfigResponse;
import com.naztech.lending.rules.repository.RuleAttributeRepository;
import com.naztech.lending.rules.repository.RuleGroupRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the rule configuration.
 *
 * <p>Separate from {@link RuleEngine} on purpose. The engine decides; this
 * answers what the criteria are. A banker explaining a decline and an
 * administrator reviewing a policy both need the second, and neither should have
 * to run an evaluation to get it.
 */
@Service
public class RuleConfigurationService {

    private final RuleGroupRepository groups;
    private final RuleAttributeRepository attributes;

    public RuleConfigurationService(RuleGroupRepository groups, RuleAttributeRepository attributes) {
        this.groups = groups;
        this.attributes = attributes;
    }

    /** Every group, with its rules spelt out. */
    @Transactional(readOnly = true)
    public List<RuleGroupConfigResponse> groups() {
        return groups.findAllWithRules().stream().map(RuleGroupConfigResponse::from).toList();
    }

    /** The catalogue of what may be tested, and which operators each accepts. */
    @Transactional(readOnly = true)
    public List<RuleAttributeResponse> attributes() {
        return attributes.findAllByOrderByCodeAsc().stream()
                .map(RuleAttributeResponse::from).toList();
    }
}
