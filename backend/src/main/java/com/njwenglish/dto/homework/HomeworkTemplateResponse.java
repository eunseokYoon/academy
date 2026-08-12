package com.njwenglish.dto.homework;

import com.njwenglish.entity.HomeworkTemplate;

public record HomeworkTemplateResponse(Long id, String title, String description, int useCount) {

    public static HomeworkTemplateResponse from(HomeworkTemplate template) {
        return new HomeworkTemplateResponse(template.getId(), template.getTitle(),
            template.getDescription(), template.getUseCount());
    }
}
