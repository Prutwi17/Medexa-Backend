package com.medexa.medicine.service;

import com.medexa.common.exception.BadRequestException;
import com.medexa.common.exception.ResourceNotFoundException;
import com.medexa.medicine.MedicineClassificationRule;
import com.medexa.medicine.MedicineClassificationRuleRepository;
import com.medexa.medicine.dto.MedicineDtos.ClassificationRuleResponse;
import com.medexa.medicine.dto.MedicineDtos.ClassificationRuleUpdateRequest;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MedicineClassificationService {

    private final MedicineClassificationRuleRepository ruleRepository;

    @Transactional(readOnly = true)
    public List<ClassificationRuleResponse> listActiveRules() {
        return ruleRepository.findByActiveTrueOrderByClassificationCodeAsc()
                .stream()
                .map(ClassificationRuleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClassificationRuleResponse> listAllRules() {
        return ruleRepository.findAllByOrderByClassificationCodeAsc()
                .stream()
                .map(ClassificationRuleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MedicineClassificationRule resolveRule(String classificationCode, boolean prescriptionRequiredFallback) {
        String code = (classificationCode != null && !classificationCode.isBlank())
                ? classificationCode.trim().toUpperCase()
                : (prescriptionRequiredFallback ? "SCHEDULE_H" : "OTC");
        return ruleRepository.findByClassificationCodeIgnoreCase(code)
                .filter(MedicineClassificationRule::isActive)
                .orElseThrow(() -> new BadRequestException("Unsupported or inactive medicine classification: " + code));
    }

    @Transactional(readOnly = true)
    public Map<String, MedicineClassificationRule> loadRuleMap() {
        return ruleRepository.findAll()
                .stream()
                .collect(Collectors.toMap(
                        r -> r.getClassificationCode().toUpperCase(),
                        Function.identity(),
                        (a, b) -> a
                ));
    }

    @Transactional
    public ClassificationRuleResponse updateRule(String code, ClassificationRuleUpdateRequest request) {
        MedicineClassificationRule rule = ruleRepository.findByClassificationCodeIgnoreCase(code)
                .orElseThrow(() -> new ResourceNotFoundException("Classification rule not found: " + code));

        if (!"OTC".equalsIgnoreCase(rule.getClassificationCode()) && Boolean.FALSE.equals(request.prescriptionRequired())) {
            throw new BadRequestException("Scheduled medicine classifications must always require a valid prescription.");
        }
        if (Boolean.TRUE.equals(request.autoSubstitutionAllowed()) && rule.isPrescriptionRequired()) {
            throw new BadRequestException("Automatic substitution of prescription medicines is strictly prohibited.");
        }

        rule.setDisplayName(request.displayName().trim());
        rule.setPrescriptionRequired(Boolean.TRUE.equals(request.prescriptionRequired()));
        rule.setDefaultRxValidityDays(request.defaultRxValidityDays());
        rule.setMaxUnitsPerOrder(request.maxUnitsPerOrder());
        rule.setAutoSubstitutionAllowed(Boolean.FALSE.equals(request.prescriptionRequired())
                && Boolean.TRUE.equals(request.autoSubstitutionAllowed()));
        rule.setRequiresPharmacistLicenseCheck(Boolean.TRUE.equals(request.requiresPharmacistLicenseCheck()));
        rule.setWarningText(request.warningText() != null ? request.warningText().trim() : null);
        if (request.active() != null) {
            rule.setActive(request.active());
        }

        ruleRepository.save(rule);
        return ClassificationRuleResponse.from(rule);
    }
}
