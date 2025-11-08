package com.example.notificationservice.controller;

import com.example.notificationservice.dto.TemplateDto;
import com.example.notificationservice.service.TemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class TemplateController {

    private final TemplateService templateService;

    @PostMapping
    public ResponseEntity<TemplateDto> createTemplate(@Valid @RequestBody TemplateDto templateDto) {
        log.info("Creating new template: {}", templateDto.getName());
        TemplateDto created = templateService.createTemplate(templateDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<Page<TemplateDto>> getAllTemplates(Pageable pageable) {
        log.info("Fetching all templates, page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        Page<TemplateDto> templates = templateService.getAllTemplates(pageable);
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/{name}")
    public ResponseEntity<TemplateDto> getTemplateByName(@PathVariable String name) {
        log.info("Fetching template by name: {}", name);
        return templateService.getTemplateByName(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{name}")
    public ResponseEntity<TemplateDto> updateTemplate(
            @PathVariable String name,
            @Valid @RequestBody TemplateDto templateDto) {
        log.info("Updating template: {}", name);
        return templateService.updateTemplate(name, templateDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String name) {
        log.info("Deleting template: {}", name);
        boolean deleted = templateService.deleteTemplate(name);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}