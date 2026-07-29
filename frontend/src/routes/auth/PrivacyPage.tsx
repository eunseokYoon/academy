import { Link } from "react-router-dom";
import { LegalDraftNotice, LegalSection, Pending } from "./LegalLayout";

/**
 * C-3. 게시까지가 Phase 2 범위다.
 *
 * <p>법정대리인 동의 절차는 방식이 미확정이라 동의 체크박스나 이력 저장을 넣지 않는다.
 * 퇴원생 데이터 보관 기간도 미정이라 기간을 지어내지 않고 확정 대상으로 표시한다.
 */
export default function PrivacyPage() {
  return (
    <main className="mx-auto min-h-screen w-full max-w-screen-sm bg-white p-4 pb-16">
      <h1 className="text-xl font-semibold text-slate-900">개인정보처리방침</h1>
      <LegalDraftNotice />

      <LegalSection title="1. 수집하는 항목">
        학생: 이름, 전화번호, 소속 반. 보호자: 이름, 전화번호, 자녀 관계. 서비스 이용 과정에서
        출석, 숙제 제출물(사진), 시험 결과, 학습 자료 열람 기록이 생성됩니다.
        <br />
        주민등록번호, 주소, 이메일, 결제 정보는 수집하지 않습니다.
      </LegalSection>

      <LegalSection title="2. 수집·이용 목적">
        수업 운영과 출결 관리, 숙제 확인과 피드백 제공, 성적 안내, 보호자 안내에만 사용합니다.
        광고나 마케팅 목적으로 이용하지 않습니다.
      </LegalSection>

      <LegalSection title="3. 보관 기간">
        재원 기간 동안 보관합니다. 퇴원 이후의 보관 기간은 <Pending label="확정 예정" /> 입니다.
      </LegalSection>

      <LegalSection title="4. 제3자 제공과 처리 위탁">
        수집한 정보를 제3자에게 제공하지 않습니다. 다만 서비스 운영을 위해 클라우드 인프라
        (서버·파일 보관)를 이용하며, 해당 사업자는 <Pending label="확정 예정" /> 입니다.
      </LegalSection>

      <LegalSection title="5. 이용자의 권리">
        본인 또는 보호자는 언제든지 자신과 자녀의 정보 열람·정정·삭제를 요청할 수 있습니다.
        요청은 담당 강사에게 연락해 주시면 처리합니다.
      </LegalSection>

      <LegalSection title="6. 안전성 확보 조치">
        비밀번호는 복호화할 수 없는 형태로 저장하며, 학생·보호자·강사의 권한에 따라 열람 범위를
        분리합니다. 보호자는 본인에게 연결된 자녀의 정보만 열람할 수 있습니다.
      </LegalSection>

      <LegalSection title="7. 문의처">
        <Pending label="상호·담당자·연락처 확정 예정" />
      </LegalSection>

      <Link to="/login" className="mt-8 inline-block text-sm text-slate-500 underline">
        로그인으로 돌아가기
      </Link>
    </main>
  );
}
