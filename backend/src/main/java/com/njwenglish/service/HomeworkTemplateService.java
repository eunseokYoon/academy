package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.CurrentUser;
import com.njwenglish.dto.homework.HomeworkTemplateCreateRequest;
import com.njwenglish.dto.homework.HomeworkTemplateResponse;
import com.njwenglish.entity.HomeworkTemplate;
import com.njwenglish.entity.Teacher;
import com.njwenglish.repository.HomeworkTemplateRepository;
import com.njwenglish.repository.TeacherRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매주 같은 숙제("단어 시험", "본문 필사")를 반복해서 낸다. 출제 화면에서 한 번 고르면
 * 제목·설명이 채워지도록 자주 쓰는 것을 위로 올린다.
 */
@Service
@RequiredArgsConstructor
public class HomeworkTemplateService {

    private final HomeworkTemplateRepository templateRepository;
    private final TeacherRepository teacherRepository;

    @Transactional(readOnly = true)
    public List<HomeworkTemplateResponse> list() {
        return templateRepository
            .findByTeacherIdOrderByUseCountDescIdDesc(currentTeacher().getId())
            .stream()
            .map(HomeworkTemplateResponse::from)
            .toList();
    }

    @Transactional
    public HomeworkTemplateResponse create(HomeworkTemplateCreateRequest request) {
        return save(request.title(), request.description());
    }

    /** 출제 시 saveAsTemplate 체크로도 들어온다. */
    @Transactional
    public HomeworkTemplateResponse save(String title, String description) {
        HomeworkTemplate template = templateRepository.save(
            HomeworkTemplate.create(currentTeacher(), title, description));
        return HomeworkTemplateResponse.from(template);
    }

    @Transactional
    public void delete(Long templateId) {
        HomeworkTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        templateRepository.delete(template);
    }

    /**
     * 이미 출제된 숙제는 템플릿과 연결을 남기지 않는다. 사용 횟수만 올려 정렬에 반영한다.
     * 템플릿이 지워졌으면 조용히 넘어간다 — 출제 자체를 실패시킬 이유가 없다.
     */
    @Transactional
    public void increaseUseCount(Long templateId) {
        templateRepository.findById(templateId).ifPresent(HomeworkTemplate::increaseUseCount);
    }

    private Teacher currentTeacher() {
        return teacherRepository.findByUserId(CurrentUser.get().userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
