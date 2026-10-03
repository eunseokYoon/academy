import { Link } from "react-router-dom";
import { ACADEMY_NAME } from "../../shared/branding";
import { LegalSection } from "./LegalLayout";
import { OPERATOR, PRIVACY_OFFICER, PRIVACY_PHONE } from "./legalInfo";

/**
 * C-3. 개인정보처리방침. 앱 `privacy_page.dart` 와 문구가 같아야 한다.
 *
 * <p>법정대리인 동의 절차는 방식이 미확정이라 동의 체크박스나 이력 저장을 넣지 않는다.
 * 보관 기간·사업자·연락처는 2026-10-03에 학원이 확정했다(스토어 출시 준비).
 */
export default function PrivacyPage() {
  return (
    <main className="mx-auto min-h-screen w-full max-w-screen-sm bg-white p-4 pb-16">
      <h1 className="text-xl font-semibold text-slate-900">개인정보처리방침</h1>
      <p className="mt-2 text-sm text-slate-600">
        {OPERATOR}(이하 “학원”)은 {ACADEMY_NAME} 서비스를 운영하며 이용자의 개인정보를 다음과 같이
        처리합니다.
      </p>

      <LegalSection title="1. 수집하는 항목">
        학생: 이름, 전화번호, 소속 반. 보호자: 이름, 전화번호, 자녀 관계. 서비스 이용 과정에서
        출석, 숙제 제출물(사진·영상), 시험 결과, 질문 게시판에 쓴 글과 사진, 수업 영상 시청
        기록(재생한 구간)이 생성됩니다.
        <br />
        앱을 이용하는 경우 알림 발송을 위해 기기 알림 토큰(기기를 식별하는 무작위 값)과 기기
        종류(Android·iOS)가 수집됩니다.
        <br />
        주민등록번호, 주소, 이메일, 결제 정보는 수집하지 않습니다.
      </LegalSection>

      <LegalSection title="2. 수집·이용 목적">
        수업 운영과 출결 관리, 숙제 확인, 성적 안내, 보호자 안내에만 사용합니다.
        광고나 마케팅 목적으로 이용하지 않습니다. 수업 영상 시청 기록은 온라인 수강 출결 확인에만
        사용합니다. 기기 알림 토큰은 공지·숙제·성적·출결 안내 알림을
        보내는 데에만 사용합니다.
      </LegalSection>

      <LegalSection title="3. 보관 기간">
        재원 기간 동안 보관하고, 퇴원한 날로부터 12개월이 지나면 파기합니다. 숙제 제출물(사진·영상)은
        제출일로부터 12개월이 지나면 삭제됩니다.
        <br />
        계정을 삭제하면 로그인 정보(전화번호·비밀번호)는 즉시 파기합니다. 출석·숙제·성적 등 학습
        기록은 학원 운영을 위해 위 기간 동안 보관한 뒤 파기합니다.
        <br />
        기기 알림 토큰은 로그아웃하면 즉시 파기합니다. 앱을 삭제한 경우 다음 알림 발송 때 무효로
        확인되는 즉시 파기하며, 계정이 삭제되면 함께 파기합니다.
      </LegalSection>

      <LegalSection title="4. 제3자 제공과 처리 위탁">
        수집한 정보를 제3자에게 제공하지 않습니다. 다만 서비스 운영을 위해 다음 사업자에게 처리를
        위탁합니다.
        <br />
        · Amazon Web Services, Inc.: 서버 운영, 숙제 제출물·첨부 파일 보관(서울 리전)
        <br />
        · Supabase, Inc.: 데이터베이스 운영(서울 리전)
        <br />
        앱 알림 발송은 Google LLC(Firebase Cloud Messaging)에 위탁합니다. 알림에는 알림 종류와
        학생 이름만 담기며 성적·숙제 내용은 포함되지 않습니다.
      </LegalSection>

      {/* 앱 푸시 알림(D). 「거부 방법」의 스위치는 앱 내 정보 화면의 「알림 받기」다 — 지우지 마라.
          「최대 4주」는 FCM 의 기본 보관 기간(TTL 28일)이다 */}
      <LegalSection title="5. 개인정보의 국외 이전">
        · 이전받는 자: Google LLC(미국) · 문의처
        https://support.google.com/policies/contact/general_privacy_form
        <br />
        · 이전 항목: 기기 알림 토큰, 알림 문구(알림 종류·학생 이름)
        <br />
        · 이전 일시·방법: 알림이 생길 때마다 네트워크로 전송
        <br />
        · 이용 목적: 앱 푸시 알림 발송
        <br />
        · 보유 기간: 발송 완료 시까지(전달되지 않은 알림은 최대 4주)
        <br />
        · 거부 방법: 앱 「내 정보」에서 알림을 끄면 이전되지 않으며, 알림을 꺼도 서비스 이용에
        제한이 없습니다.
      </LegalSection>

      <LegalSection title="6. 이용자의 권리">
        본인 또는 보호자는 언제든지 자신과 자녀의 정보 열람·정정·삭제를 요청할 수 있습니다.
        요청은 아래 문의처로 연락해 주시면 처리합니다.
        <br />
        계정은 앱과 웹의 「내 정보」에서 「계정 삭제」를 눌러 직접 삭제할 수 있습니다.{" "}
        <Link to="/account-deletion" className="underline">
          계정 삭제 안내
        </Link>
      </LegalSection>

      <LegalSection title="7. 안전성 확보 조치">
        비밀번호는 복호화할 수 없는 형태로 저장하며, 학생·보호자·강사의 권한에 따라 열람 범위를
        분리합니다. 보호자는 본인에게 연결된 자녀의 정보만 열람할 수 있습니다.
      </LegalSection>

      <LegalSection title="8. 개인정보 보호책임자와 문의처">
        {OPERATOR} · 대표·개인정보 보호책임자 {PRIVACY_OFFICER} · {PRIVACY_PHONE}
      </LegalSection>

      <LegalSection title="9. 시행일">이 방침은 2026년 10월 3일부터 시행합니다.</LegalSection>

      <Link to="/login" className="mt-8 inline-block text-sm text-slate-500 underline">
        로그인으로 돌아가기
      </Link>
    </main>
  );
}
