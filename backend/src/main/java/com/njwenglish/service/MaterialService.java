package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.common.s3.MaterialKeys;
import com.njwenglish.common.s3.PresignedUrlProvider;
import com.njwenglish.common.security.CurrentUser;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.material.DownloadUrlResponse;
import com.njwenglish.dto.material.MaterialCreateRequest;
import com.njwenglish.dto.material.MaterialResponse;
import com.njwenglish.dto.material.MaterialUpdateRequest;
import com.njwenglish.dto.material.MaterialUploadUrlRequest;
import com.njwenglish.dto.material.MaterialUploadUrlResponse;
import com.njwenglish.dto.material.StudentMaterialResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Material;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.MaterialCategory;
import com.njwenglish.entity.enums.MaterialVisibility;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.MaterialRepository;
import com.njwenglish.repository.TeacherRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자료실 (S-8 · T-9). <b>학생 전용이다. 학부모에게 열지 마라.</b>
 * 학부모는 "자녀가 했는지 여부"만 보고, 자료 자체는 학생 화면에만 있다.
 *
 * <p>공개 범위는 차원이 둘뿐이다.
 *
 * <table>
 *   <tr><th>visibility</th><th>class_room_id</th><th>노출 대상</th></tr>
 *   <tr><td>PUBLIC</td><td>null</td><td>로그인한 전체 재원생</td></tr>
 *   <tr><td>CLASS</td><td>값 있음</td><td>해당 반 재원생만</td></tr>
 * </table>
 *
 * <p>{@code PUBLIC}은 "반 제한 없음"이지 "누구나"가 아니다. 비로그인 공개는 없다.
 */
@Service
@RequiredArgsConstructor
public class MaterialService {

    /**
     * 다운로드 URL 유효기간. 응답에 담겨 나가는 값이라 짧게 잡는다.
     * 조회용 read-expiry(10분)와 따로 두는 이유: 자료 링크는 파일이 그대로 새는 경로다.
     */
    private static final Duration DOWNLOAD_EXPIRY = Duration.ofMinutes(5);

    /**
     * 활성 배정이 없는 학생(배정 전)의 반 목록 자리에 넣는 더미다.
     * 빈 컬렉션을 IN에 넘기면 SQL 오류가 나서 목록 조회 자체가 500이 된다.
     * 존재하지 않는 id라 어떤 자료도 걸리지 않고, PUBLIC 분기만 살아남는다.
     */
    private static final List<Long> NO_CLASS_ROOM = List.of(-1L);

    private final MaterialRepository materialRepository;
    private final ClassRoomRepository classRoomRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TeacherRepository teacherRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final PresignedUrlProvider presignedUrlProvider;
    private final MaterialKeys materialKeys;

    // ---------- 학생 (S-8) ----------

    /**
     * studentId 파라미터가 없다. 본인 것만 보므로 토큰에서 학생을 찾는다.
     *
     * <p>정렬은 최신순이다. 학생은 "이번 주 자료"를 맨 위에서 찾는다.
     */
    @Transactional(readOnly = true)
    public PageResponse<StudentMaterialResponse> myMaterials(MaterialCategory category,
                                                             Pageable pageable) {
        Student me = studentAccessGuard.requireSelf();
        return PageResponse.from(materialRepository
            .findForStudent(myClassRoomIds(me.getId()), name(category), pageable)
            .map(StudentMaterialResponse::from));
    }

    /**
     * <b>발급 전에 권한을 다시 확인한다.</b> 목록에서 걸러졌다고 안심하면 안 된다 —
     * materialId를 직접 넣어 호출하면 목록을 거치지 않는다. 대상이 아니면 403이다.
     */
    @Transactional(readOnly = true)
    public DownloadUrlResponse myDownloadUrl(Long materialId) {
        Student me = studentAccessGuard.requireSelf();
        Material material = materialRepository.findWithClassRoom(materialId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!isVisibleTo(material, me.getId())) {
            throw new BusinessException(ErrorCode.ROLE_NOT_ALLOWED);
        }
        return new DownloadUrlResponse(
            presignedUrlProvider.attachmentUrl(
                material.getS3Key(), material.getFileName(), DOWNLOAD_EXPIRY),
            material.getFileName(),
            DOWNLOAD_EXPIRY.toSeconds());
    }

    // ---------- 선생님 (T-9) ----------

    @Transactional(readOnly = true)
    public PageResponse<MaterialResponse> list(Short year, Short month, Short week,
                                               MaterialCategory category, Pageable pageable) {
        return PageResponse.from(materialRepository
            .search(year, month, week, name(category), pageable)
            .map(MaterialResponse::from));
    }

    /**
     * 업로드 URL 발급. 파일은 서버를 거치지 않는다 — 클라이언트가 S3로 직접 PUT하고,
     * 서버는 등록(다음 단계)에서 발급한 s3Key가 맞는지만 대조한다.
     *
     * <p>확장자 허용 목록과 50MB 상한을 여기서 검사한다. 등록 단계에서만 검사하면
     * 이미 올라간 파일이 고아로 남는다.
     */
    @Transactional(readOnly = true)
    public MaterialUploadUrlResponse issueUploadUrl(MaterialUploadUrlRequest request) {
        Teacher teacher = currentTeacher();

        String extension = materialKeys.extensionOf(request.fileName());
        if (extension == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        if (request.bytes() > MaterialKeys.MAX_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        String s3Key = materialKeys.issue(teacher.getId(), extension, LocalDate.now());
        String contentType = materialKeys.contentTypeOf(extension);
        return new MaterialUploadUrlResponse(
            presignedUrlProvider.uploadUrl(s3Key, contentType), s3Key, contentType);
    }

    /**
     * 자료 등록. 같은 파일을 여러 반에 주려면 <b>반마다 호출</b>하고 s3Key를 공유한다
     * (학교·학년 개념이 없어 한 번에 묶을 수 없다).
     */
    @Transactional
    public MaterialResponse create(MaterialCreateRequest request) {
        Teacher teacher = currentTeacher();

        // 클라이언트가 보낸 s3Key를 그대로 믿으면 버킷 내 임의 경로를 자료로 등록할 수 있다
        if (!materialKeys.matches(request.s3Key(), teacher.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        Material material = request.visibility() == MaterialVisibility.PUBLIC
            ? publicMaterial(request, teacher)
            : classMaterial(request, teacher);
        return MaterialResponse.from(materialRepository.save(material));
    }

    @Transactional
    public MaterialResponse update(Long materialId, MaterialUpdateRequest request) {
        Material material = findMaterial(materialId);
        material.edit(
            request.title() == null ? material.getTitle() : request.title(),
            request.category() == null ? material.getCategory() : request.category(),
            request.year() == null ? material.getYear() : request.year(),
            request.month() == null ? material.getMonth() : request.month(),
            request.week() == null ? material.getWeek() : request.week());
        return MaterialResponse.from(material);
    }

    /**
     * 자료 삭제. S3 객체는 <b>그 파일을 쓰는 마지막 행일 때만</b> 지운다.
     * 여러 반에 준 자료는 s3Key를 공유하므로, 세지 않고 지우면 남은 반의 자료가 깨진다.
     */
    @Transactional
    public void delete(Long materialId) {
        Material material = findMaterial(materialId);
        String s3Key = material.getS3Key();
        materialRepository.delete(material);
        materialRepository.flush();

        if (materialRepository.countByS3Key(s3Key) == 0) {
            presignedUrlProvider.deleteQuietly(s3Key);
        }
    }

    // ---------- 내부 ----------

    /**
     * 노출 대상 판정. 두 차원을 각각 독립으로 본다.
     * <b>PUBLIC 분기를 빼면</b> 전체 공개 자료가 아무에게도 안 보인다.
     */
    private boolean isVisibleTo(Material material, Long studentId) {
        if (material.getVisibility() == MaterialVisibility.PUBLIC) {
            return true;
        }
        return material.getClassRoom() != null
            && enrollmentRepository.findActiveClassRoomIds(studentId)
                .contains(material.getClassRoom().getId());
    }

    private List<Long> myClassRoomIds(Long studentId) {
        List<Long> ids = enrollmentRepository.findActiveClassRoomIds(studentId);
        return ids.isEmpty() ? NO_CLASS_ROOM : ids;
    }

    private Material classMaterial(MaterialCreateRequest request, Teacher teacher) {
        if (request.classRoomId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        ClassRoom classRoom = classRoomRepository.findById(request.classRoomId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        return Material.forClass(request.title(), request.category(), request.s3Key(),
            request.fileName(), request.bytes(), classRoom,
            request.year(), request.month(), request.week(), teacher);
    }

    private Material publicMaterial(MaterialCreateRequest request, Teacher teacher) {
        // PUBLIC인데 반이 채워져 있으면 400이다. 통과시켜도 ck_materials_scope가 막는다
        if (request.classRoomId() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return Material.forEveryone(request.title(), request.category(), request.s3Key(),
            request.fileName(), request.bytes(),
            request.year(), request.month(), request.week(), teacher);
    }

    private Material findMaterial(Long materialId) {
        return materialRepository.findWithClassRoom(materialId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** enum 파라미터에 IS NULL을 걸면 타입 추론이 갈려서 문자열로 넘긴다. */
    private String name(MaterialCategory category) {
        return category == null ? null : category.name();
    }

    private Teacher currentTeacher() {
        return teacherRepository.findByUserId(CurrentUser.get().userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
