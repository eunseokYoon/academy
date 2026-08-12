package com.njwenglish.dto.member;

public record StudentDeleteResponse(Long deletedStudentId, boolean deletedUser,
                                    long deletedEnrollments) {
}
