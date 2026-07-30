package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.s3.MaterialKeys;
import com.njwenglish.common.s3.PresignedUrlProvider;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.material.MaterialCreateRequest;
import com.njwenglish.dto.material.MaterialUploadUrlRequest;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Material;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.enums.MaterialCategory;
import com.njwenglish.entity.enums.MaterialVisibility;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.MaterialRepository;
import com.njwenglish.repository.TeacherRepository;
import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 자료실에서 조용히 새는 두 지점을 고정한다.
 *
 * <ol>
 *   <li>PUBLIC 분기 누락 — 전체 공개 자료가 아무에게도 안 보인다
 *   <li>download-url 권한 재확인 누락 — 목록에 없는 materialId로 파일이 빠져나간다
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaterialServiceTest {

    private static final String SECRET = "test-secret-value-for-hmac-signing-0123456789";

    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private ClassRoomRepository classRoomRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private StudentAccessGuard studentAccessGuard;
    @Mock
    private PresignedUrlProvider presignedUrlProvider;

    private final MaterialKeys materialKeys = new MaterialKeys(SECRET);
    private final ClassRoom myClassRoom = Fixtures.openClassRoom(3L, "고2 심화반", "HK7F2Q");
    private final ClassRoom otherClassRoom = Fixtures.openClassRoom(9L, "고1 기초반", "PQ3M8T");
    private final Student me = Fixtures.student(88L, "서동환");

    private MaterialService materialService;

    @BeforeEach
    void setUp() {
        materialService = new MaterialService(materialRepository, classRoomRepository,
            enrollmentRepository, teacherRepository, studentAccessGuard,
            presignedUrlProvider, materialKeys);
        given(studentAccessGuard.requireSelf()).willReturn(me);
        given(teacherRepository.findByUserId(any()))
            .willReturn(Optional.of(Fixtures.teacherEntity(1L)));
        Fixtures.login(Fixtures.teacher(1L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Material classMaterial(Long id, ClassRoom classRoom) {
        Material material = Material.forClass("A고 중간 기출", MaterialCategory.PAST_EXAM,
            "materials/2026/05/x.pdf", "기출.pdf", 3210544L, classRoom,
            (short) 2026, (short) 5, (short) 4, Fixtures.teacherEntity(1L));
        ReflectionTestUtils.setField(material, "id", id);
        return material;
    }

    private Material publicMaterial(Long id) {
        Material material = Material.forEveryone("여름 특강 안내", MaterialCategory.ETC,
            "materials/2026/05/y.pdf", "안내.pdf", 120000L,
            (short) 2026, (short) 5, (short) 4, Fixtures.teacherEntity(1L));
        ReflectionTestUtils.setField(material, "id", id);
        return material;
    }

    // ---------- 목록 ----------

    @Test
    @DisplayName("활성 배정이 없는 학생도 목록 조회가 깨지지 않는다 — 빈 IN 대신 더미가 들어간다")
    void 배정_전_학생도_목록을_조회할_수_있다() {
        given(enrollmentRepository.findActiveClassRoomIds(88L)).willReturn(List.of());
        given(materialRepository.findForStudent(any(), any(), any()))
            .willReturn(new PageImpl<>(List.of(publicMaterial(41L))));

        assertThat(materialService.myMaterials(null, PageRequest.of(0, 20)).items()).hasSize(1);

        // 빈 리스트를 그대로 IN에 넘기면 SQL이 깨져 목록 자체가 500이 된다
        verify(materialRepository).findForStudent(eq(List.of(-1L)), eq(null), any(Pageable.class));
    }

    @Test
    @DisplayName("목록 조회에 학생의 재원 반 목록이 그대로 넘어간다")
    void 재원_반_목록으로_조회한다() {
        given(enrollmentRepository.findActiveClassRoomIds(88L)).willReturn(List.of(3L, 7L));
        given(materialRepository.findForStudent(any(), any(), any()))
            .willReturn(Page.empty());

        materialService.myMaterials(MaterialCategory.LESSON, PageRequest.of(0, 20));

        verify(materialRepository).findForStudent(eq(List.of(3L, 7L)), eq("LESSON"),
            any(Pageable.class));
    }

    // ---------- 다운로드 URL 권한 ----------

    @Test
    @DisplayName("PUBLIC 자료는 배정이 없는 학생도 받을 수 있다")
    void PUBLIC_자료는_전체_재원생에게_열린다() {
        given(materialRepository.findWithClassRoom(41L)).willReturn(Optional.of(publicMaterial(41L)));
        given(enrollmentRepository.findActiveClassRoomIds(88L)).willReturn(List.of());
        given(presignedUrlProvider.attachmentUrl(any(), any(), any())).willReturn("https://signed");

        assertThat(materialService.myDownloadUrl(41L).downloadUrl()).isEqualTo("https://signed");
        assertThat(materialService.myDownloadUrl(41L).expiresIn()).isEqualTo(300);
    }

    @Test
    @DisplayName("내 반 자료는 받을 수 있다")
    void 내_반_자료는_받을_수_있다() {
        given(materialRepository.findWithClassRoom(41L))
            .willReturn(Optional.of(classMaterial(41L, myClassRoom)));
        given(enrollmentRepository.findActiveClassRoomIds(88L)).willReturn(List.of(3L));
        given(presignedUrlProvider.attachmentUrl(any(), any(), any())).willReturn("https://signed");

        assertThat(materialService.myDownloadUrl(41L).fileName()).isEqualTo("기출.pdf");
    }

    @Test
    @DisplayName("목록에 나오지 않는 다른 반 자료를 직접 호출하면 403이다")
    void 다른_반_자료는_403이다() {
        given(materialRepository.findWithClassRoom(41L))
            .willReturn(Optional.of(classMaterial(41L, otherClassRoom)));
        given(enrollmentRepository.findActiveClassRoomIds(88L)).willReturn(List.of(3L));

        // 목록에서 걸러졌다고 안심하면 안 된다. materialId를 직접 넣는 호출을 막아야 한다
        assertThatThrownBy(() -> materialService.myDownloadUrl(41L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROLE_NOT_ALLOWED);

        verify(presignedUrlProvider, never()).attachmentUrl(any(), any(), any());
    }

    // ---------- 업로드 ----------

    @Test
    @DisplayName("실행 가능 확장자는 업로드 URL 발급 단계에서 거부된다")
    void 실행_파일은_거부된다() {
        assertThatThrownBy(() -> materialService.issueUploadUrl(
            new MaterialUploadUrlRequest("payload.html", 1024L)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNSUPPORTED_FILE_TYPE);

        assertThatThrownBy(() -> materialService.issueUploadUrl(
            new MaterialUploadUrlRequest("virus.exe", 1024L)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    @DisplayName("50MB를 넘으면 413이다 — 등록 단계까지 가면 파일이 이미 올라가 고아가 된다")
    void 용량_초과는_413이다() {
        assertThatThrownBy(() -> materialService.issueUploadUrl(
            new MaterialUploadUrlRequest("자료.pdf", MaterialKeys.MAX_BYTES + 1)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("발급 응답의 contentType을 그대로 PUT해야 하므로 함께 내려준다")
    void 업로드_URL은_content_type을_함께_내려준다() {
        given(presignedUrlProvider.uploadUrl(any(), any())).willReturn("https://put");

        var response = materialService.issueUploadUrl(
            new MaterialUploadUrlRequest("0524_lesson.pdf", 7130316L));

        assertThat(response.contentType()).isEqualTo("application/pdf");
        assertThat(response.s3Key()).startsWith("materials/").endsWith(".pdf");
    }

    // ---------- 등록 ----------

    @Test
    @DisplayName("발급받지 않은 s3Key로 등록하면 400이다")
    void 임의_s3Key는_거부된다() {
        assertThatThrownBy(() -> materialService.create(new MaterialCreateRequest(
            "제목", MaterialCategory.LESSON, "materials/2026/05/직접만든경로.pdf", "a.pdf",
            100L, 3L, MaterialVisibility.CLASS, (short) 2026, (short) 5, (short) 4)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);

        verify(materialRepository, never()).save(any());
    }

    @Test
    @DisplayName("CLASS인데 반이 없거나 PUBLIC인데 반이 있으면 400이다")
    void 공개_범위와_반은_짝이어야_한다() {
        String s3Key = materialKeys.issue(1L, "pdf", LocalDate.now());

        assertThatThrownBy(() -> materialService.create(new MaterialCreateRequest(
            "제목", MaterialCategory.LESSON, s3Key, "a.pdf", 100L,
            null, MaterialVisibility.CLASS, (short) 2026, (short) 5, (short) 4)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);

        assertThatThrownBy(() -> materialService.create(new MaterialCreateRequest(
            "제목", MaterialCategory.LESSON, s3Key, "a.pdf", 100L,
            3L, MaterialVisibility.PUBLIC, (short) 2026, (short) 5, (short) 4)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("선생님이 고른 주차가 그대로 저장된다 — 서버가 날짜에서 계산하지 않는다")
    void 주차는_고른_값이_그대로_저장된다() {
        String s3Key = materialKeys.issue(1L, "pdf", LocalDate.now());
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(myClassRoom));
        given(materialRepository.save(any())).willAnswer(call -> call.getArgument(0));

        var response = materialService.create(new MaterialCreateRequest(
            "5월 24일 수업자료", MaterialCategory.LESSON, s3Key, "0524_lesson.pdf", 7130316L,
            3L, MaterialVisibility.CLASS, (short) 2026, (short) 5, (short) 4));

        assertThat(response.year()).isEqualTo((short) 2026);
        assertThat(response.month()).isEqualTo((short) 5);
        assertThat(response.week()).isEqualTo((short) 4);
        assertThat(response.classRoomName()).isEqualTo("고2 심화반");
    }

    // ---------- 삭제 ----------

    @Test
    @DisplayName("같은 파일을 쓰는 다른 반 자료가 남아 있으면 S3 객체를 지우지 않는다")
    void 공유된_파일은_S3에서_지우지_않는다() {
        given(materialRepository.findWithClassRoom(41L))
            .willReturn(Optional.of(classMaterial(41L, myClassRoom)));
        given(materialRepository.countByS3Key("materials/2026/05/x.pdf")).willReturn(1L);

        materialService.delete(41L);

        verify(presignedUrlProvider, never()).deleteQuietly(any());
    }

    @Test
    @DisplayName("마지막 자료를 지우면 S3 객체도 지운다")
    void 마지막_자료는_S3까지_지운다() {
        given(materialRepository.findWithClassRoom(41L))
            .willReturn(Optional.of(classMaterial(41L, myClassRoom)));
        given(materialRepository.countByS3Key("materials/2026/05/x.pdf")).willReturn(0L);

        materialService.delete(41L);

        verify(presignedUrlProvider).deleteQuietly("materials/2026/05/x.pdf");
    }
}
