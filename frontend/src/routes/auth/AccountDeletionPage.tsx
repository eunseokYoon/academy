import { Link } from "react-router-dom";
import { ACADEMY_NAME } from "../../shared/branding";
import { LegalSection } from "./LegalLayout";
import { OPERATOR, PRIVACY_OFFICER, PRIVACY_PHONE } from "./legalInfo";

/**
 * 계정 삭제 안내(2026-10-03). 구글플레이가 앱 밖에서도 열리는 삭제 안내 URL을 요구한다 —
 * 스토어 설정의 「계정 삭제 URL」에 https://njwenglish.com/account-deletion 을 적는다.
 * 로그인 없이 열린다(처리방침·약관과 같다).
 */
export default function AccountDeletionPage() {
  return (
    <main className="mx-auto min-h-screen w-full max-w-screen-sm bg-white p-4 pb-16">
      <h1 className="text-xl font-semibold text-slate-900">계정 삭제 안내</h1>
      <p className="mt-2 text-sm text-slate-600">
        {ACADEMY_NAME}({OPERATOR}) 학생·보호자 계정을 삭제하는 방법입니다.
      </p>

      <LegalSection title="직접 삭제하기">
        1. 앱 또는 웹(njwenglish.com)에 로그인합니다.
        <br />
        2. 학생은 「내 정보 · 성적」, 보호자는 「내 정보」 화면 맨 아래의 「계정 삭제」를 누릅니다.
        <br />
        3. 비밀번호를 한 번 더 입력하면 바로 삭제됩니다.
      </LegalSection>

      <LegalSection title="로그인할 수 없는 경우">
        아래 문의처로 연락해 주시면 본인 확인 후 삭제해 드립니다.
        <br />
        {OPERATOR} · {PRIVACY_OFFICER} · {PRIVACY_PHONE}
      </LegalSection>

      <LegalSection title="삭제되는 정보와 보관되는 정보">
        · 즉시 삭제: 로그인 정보(전화번호·비밀번호), 앱 알림 토큰
        <br />
        · 보관 후 삭제: 출석·숙제·성적 등 학습 기록은 학원 운영을 위해 퇴원한 날로부터 12개월 동안
        보관한 뒤 파기합니다. 숙제 제출물(사진·영상)은 제출일로부터 12개월이 지나면 삭제됩니다.
        <br />
        보호자 계정을 삭제해도 자녀의 계정과 기록은 그대로 남습니다.
      </LegalSection>

      <Link to="/privacy" className="mt-8 inline-block text-sm text-slate-500 underline">
        개인정보처리방침 보기
      </Link>
    </main>
  );
}
