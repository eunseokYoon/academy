import { Link } from "react-router-dom";
import { ACADEMY_NAME } from "../../shared/branding";
import { LegalSection } from "./LegalLayout";
import { OPERATOR, PRIVACY_OFFICER, PRIVACY_PHONE } from "./legalInfo";

/**
 * C-3. 이용약관. 앱 `terms_page.dart` 와 문구가 같아야 한다.
 * 운영 사업자·연락처는 2026-10-03에 학원이 확정했다.
 */
export default function TermsPage() {
  return (
    <main className="mx-auto min-h-screen w-full max-w-screen-sm bg-white p-4 pb-16">
      <h1 className="text-xl font-semibold text-slate-900">이용약관</h1>

      <LegalSection title="제1조 (목적)">
        본 약관은 {OPERATOR}(이하 “학원”)이 제공하는 학습 관리 서비스 {ACADEMY_NAME}(이하
        “서비스”)의 이용 조건과 절차, 이용자와 학원의 권리·의무를 정하는 것을 목적으로 합니다.
      </LegalSection>

      <LegalSection title="제2조 (이용 대상과 계정)">
        서비스는 학원에 등록된 학생과 그 보호자, 강사만 이용할 수 있습니다. 계정은 자유 가입이
        아니라 학원이 발급한 코드로만 만들 수 있으며, 로그인 아이디는 본인 전화번호입니다.
        학생과 보호자는 서로 다른 번호를 사용해야 합니다.
      </LegalSection>

      <LegalSection title="제3조 (계정 관리)">
        가입 직후 비밀번호는 임시값이며, 변경하기 전까지는 비밀번호 변경 외의 기능을 이용할 수
        없습니다. 계정 정보를 타인에게 알려주어 발생한 문제에 대해 학원은 책임지지 않습니다.
        비밀번호를 분실한 경우 담당 강사에게 문의해 초기화받습니다.
        <br />
        학생과 보호자는 앱과 웹의 「내 정보」에서 언제든지 계정을 삭제할 수 있습니다. 삭제하면
        로그인 정보는 즉시 파기되며, 학습 기록은 개인정보처리방침에 정한 기간 동안 보관된 뒤
        파기됩니다.
      </LegalSection>

      <LegalSection title="제4조 (서비스의 내용)">
        서비스는 수업 일정과 출석, 숙제 제출과 확인, 시험 결과, 공지 열람, 질문 게시판 기능을
        제공합니다. 제공 범위는 이용자의 역할(학생·보호자·강사)에 따라 다릅니다.
      </LegalSection>

      <LegalSection title="제5조 (이용자의 의무)">
        이용자는 타인의 계정을 사용하거나 가입 코드를 학원 밖으로 전달해서는 안 되며, 서비스에서
        확인한 다른 학생의 정보를 외부에 공개해서는 안 됩니다.
      </LegalSection>

      <LegalSection title="제6조 (서비스의 중단)">
        학원은 시스템 점검, 장애, 통신 사업자의 사정 등으로 서비스 제공을 일시 중단할 수 있으며,
        사전에 알리기 어려운 경우 사후에 안내합니다.
      </LegalSection>

      <LegalSection title="제7조 (약관의 변경)">
        학원은 필요한 경우 약관을 변경할 수 있으며, 변경된 약관은 본 화면에 게시한 때부터
        효력이 발생합니다.
      </LegalSection>

      <LegalSection title="제8조 (문의처)">
        {OPERATOR} · 대표 {PRIVACY_OFFICER} · {PRIVACY_PHONE}
      </LegalSection>

      <LegalSection title="부칙">이 약관은 2026년 10월 3일부터 시행합니다.</LegalSection>

      <Link to="/login" className="mt-8 inline-block text-sm text-slate-500 underline">
        로그인으로 돌아가기
      </Link>
    </main>
  );
}
