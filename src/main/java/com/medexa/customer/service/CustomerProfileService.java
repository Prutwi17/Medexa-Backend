package com.medexa.customer.service;

import com.medexa.common.exception.ResourceNotFoundException;
import com.medexa.customer.CustomerProfile;
import com.medexa.customer.CustomerProfileRepository;
import com.medexa.customer.dto.CustomerDtos.CustomerProfileResponse;
import com.medexa.customer.dto.CustomerDtos.UpdateCustomerProfileRequest;
import com.medexa.user.User;
import com.medexa.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerProfileService {

    private final UserRepository userRepository;
    private final CustomerProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public CustomerProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found."));
        CustomerProfile profile = profileRepository.findByUserId(userId).orElse(null);
        return CustomerProfileResponse.from(user, profile);
    }

    @Transactional
    public CustomerProfileResponse updateProfile(Long userId, UpdateCustomerProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found."));
        user.setFullName(request.fullName().trim());
        user.setPhoneNumber(trimToNull(request.phoneNumber()));
        userRepository.save(user);

        CustomerProfile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> CustomerProfile.builder().user(user).build());
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setGender(trimToNull(request.gender()));
        profile.setBloodGroup(trimToNull(request.bloodGroup()));
        profile.setAllergies(trimToNull(request.allergies()));
        profile.setChronicConditions(trimToNull(request.chronicConditions()));
        profile.setEmergencyContactName(trimToNull(request.emergencyContactName()));
        profile.setEmergencyContactPhone(trimToNull(request.emergencyContactPhone()));
        profile.setPreferredLanguage(request.preferredLanguage() != null && !request.preferredLanguage().isBlank()
                ? request.preferredLanguage().trim()
                : "English");

        profileRepository.save(profile);
        return CustomerProfileResponse.from(user, profile);
    }

    private String trimToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
