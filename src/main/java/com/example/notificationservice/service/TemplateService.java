package com.example.notificationservice.service;

import com.example.notificationservice.dto.TemplateDto;
import com.example.notificationservice.entity.NotificationTemplate;
import com.example.notificationservice.enums.TemplateType;
import com.example.notificationservice.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateService {

    private final NotificationTemplateRepository templateRepository;

    @Transactional
    @CacheEvict(value = "templates", key = "#dto.name")
    public TemplateDto createTemplate(TemplateDto dto) {
        if (templateRepository.existsByName(dto.getName())) {
            throw new IllegalArgumentException("Template with name " + dto.getName() + " already exists");
        }

        NotificationTemplate template = NotificationTemplate.builder()
                .name(dto.getName())
                .type(dto.getType())
                .subject(dto.getSubject())
                .body(dto.getBody())
                .variables(dto.getVariables())
                .build();

        NotificationTemplate saved = templateRepository.save(template);
        log.info("Template created: {}", saved.getName());
        return mapToDto(saved);
    }

    public Page<TemplateDto> getAllTemplates(Pageable pageable) {
        return templateRepository.findAll(pageable).map(this::mapToDto);
    }

    @Cacheable(value = "templates", key = "#name")
    public Optional<TemplateDto> getTemplateByName(String name) {
        log.debug("Fetching template from database: {}", name);
        return templateRepository.findByName(name).map(this::mapToDto);
    }

    @Transactional
    @CacheEvict(value = "templates", key = "#name")
    public Optional<TemplateDto> updateTemplate(String name, TemplateDto dto) {
        return templateRepository.findByName(name).map(template -> {
            template.setType(dto.getType());
            template.setSubject(dto.getSubject());
            template.setBody(dto.getBody());
            template.setVariables(dto.getVariables());

            NotificationTemplate updated = templateRepository.save(template);
            log.info("Template updated: {}", name);
            return mapToDto(updated);
        });
    }

    @Transactional
    @CacheEvict(value = "templates", key = "#name")
    public boolean deleteTemplate(String name) {
        return templateRepository.findByName(name).map(template -> {
            templateRepository.delete(template);
            log.info("Template deleted: {}", name);
            return true;
        }).orElse(false);
    }

    private TemplateDto mapToDto(NotificationTemplate entity) {
        return TemplateDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(entity.getType())
                .subject(entity.getSubject())
                .body(entity.getBody())
                .variables(entity.getVariables())
                .build();
    }
}