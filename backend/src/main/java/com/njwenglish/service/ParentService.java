package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.CurrentUser;
import com.njwenglish.common.util.PhoneNumbers;
import com.njwenglish.dto.member.ChildResponse;
import com.njwenglish.dto.member.ParentMeResponse;
import com.njwenglish.dto.member.ParentPhoneUpdateRequest;
import com.njwenglish.entity.Parent;
import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.StudentStatus;
import com.njwenglish.repository.ParentRepository;
import com.njwenglish.repository.StudentRepository;
import com.njwenglish.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParentService {

    private final ParentRepository parentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    /** 자녀 선택 드롭다운용. 퇴원한 아이는 빼고 재원 중인 자녀만 내려간다. */
    @Transactional(readOnly = true)
    public List<ChildResponse> children() {
        return childrenOf(currentParent());
    }

    @Transactional(readOnly = true)
    public ParentMeResponse me() {
        Parent parent = currentParent();
        User user = parent.getUser();
        return new ParentMeResponse(user.getId(), user.getName(), user.getPhone(),
            childrenOf(parent));
    }

    /**
     * login_id는 phone의 정규화 값이라 User.changePhone() 한 곳에서 둘을 같이 바꾼다.
     * 한쪽만 바꾸면 그 학부모는 로그인하지 못한다.
     */
    @Transactional
    public ParentMeResponse changePhone(ParentPhoneUpdateRequest request) {
        User user = userRepository.findById(CurrentUser.get().userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));

        String phone = PhoneNumbers.normalize(request.phone());
        if (phone.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (!phone.equals(user.getLoginId()) && userRepository.existsByLoginId(phone)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        user.changePhone(phone);
        return me();
    }

    private Parent currentParent() {
        return parentRepository.findByUserId(CurrentUser.get().userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private List<ChildResponse> childrenOf(Parent parent) {
        return studentRepository
            .findByParentIdAndStatusOrderByNameAsc(parent.getId(), StudentStatus.ENROLLED)
            .stream()
            .map(ChildResponse::from)
            .toList();
    }
}
