package com.example.notificationservice.service;

import com.example.notificationservice.dto.PreferenceDto;
import com.example.notificationservice.entity.NotificationPreference;
import com.example.notificationservice.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;

    public Optional<PreferenceDto> getUserPreferences(Integer userId) {
        return preferenceRepository.findByUserId(userId).map(this::mapToDto);
    }

    @Transactional
    public PreferenceDto createUserPreferences(PreferenceDto dto) {
        if (preferenceRepository.existsByUserId(dto.getUserId())) {
            throw new IllegalArgumentException("Preferences already exist for user: " + dto.getUserId());
        }

        NotificationPreference preference = NotificationPreference.builder()
                .userId(dto.getUserId())
                .emailEnabled(dto.getEmailEnabled())
                .pushEnabled(dto.getPushEnabled())
                .sseEnabled(dto.getSseEnabled())
                .emailFrequency(dto.getEmailFrequency())
                .categories(dto.getCategories())
                .build();

        NotificationPreference saved = preferenceRepository.save(preference);
        return mapToDto(saved);
    }

    @Transactional
    public PreferenceDto updateUserPreferences(PreferenceDto dto) {
        NotificationPreference preference = preferenceRepository.findByUserId(dto.getUserId())
                .orElseGet(() -> NotificationPreference.builder()
                        .userId(dto.getUserId())
                        .build());

        preference.setEmailEnabled(dto.getEmailEnabled());
        preference.setPushEnabled(dto.getPushEnabled());
        preference.setSseEnabled(dto.getSseEnabled());
        preference.setEmailFrequency(dto.getEmailFrequency());
        preference.setCategories(dto.getCategories());

        NotificationPreference saved = preferenceRepository.save(preference);
        return mapToDto(saved);
    }

    @Transactional
    public boolean deleteUserPreferences(Integer userId) {
        return preferenceRepository.findByUserId(userId)
                .map(preference -> {
                    preferenceRepository.delete(preference);
                    log.info("Deleted preferences for user: {}", userId);
                    return true;
                })
                .orElse(false);
    }

    private PreferenceDto mapToDto(NotificationPreference entity) {
        return PreferenceDto.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .emailEnabled(entity.getEmailEnabled())
                .pushEnabled(entity.getPushEnabled())
                .sseEnabled(entity.getSseEnabled())
                .emailFrequency(entity.getEmailFrequency())
                .categories(entity.getCategories())
                .build();
    }


}