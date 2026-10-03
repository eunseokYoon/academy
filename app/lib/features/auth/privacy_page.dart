import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/router/app_router.dart';
import '../../core/theme/app_colors.dart';
import '../../shared/branding.dart';
import '../../shared/widgets/legal.dart';
import 'legal_info.dart';

/// C-3. 법정 고지라 로그인 전에도 읽을 수 있다.
///
/// **정본은 웹 `frontend/src/routes/auth/PrivacyPage.tsx` 다.** 사업자·연락처는 `legal_info.dart`.
/// 절 제목과 문구를 여기서 바꾸지 마라 — 두 곳에 다른 정책이 생긴다.
class PrivacyPage extends StatelessWidget {
  const PrivacyPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.slate50,
      appBar: AppBar(title: const Text('개인정보처리방침')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 40),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 384),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    '$legalOperator(이하 “학원”)은 $academyName 서비스를 운영하며 이용자의 개인정보를 다음과 같이 처리합니다.',
                    style: TextStyle(
                      fontSize: 14,
                      height: 1.6,
                      color: AppColors.slate600,
                    ),
                  ),
                  const LegalSection(
                    title: '1. 수집하는 항목',
                    body: Text(
                      '학생: 이름, 전화번호, 소속 반. 보호자: 이름, 전화번호, 자녀 관계. 서비스 이용 과정에서 출석, 숙제 제출물(사진·영상), 시험 결과, 질문 게시판에 쓴 글과 사진, 수업 영상 시청 기록(재생한 구간)이 생성됩니다.'
                      '\n앱을 이용하는 경우 알림 발송을 위해 기기 알림 토큰(기기를 식별하는 무작위 값)과 기기 종류(Android·iOS)가 수집됩니다.'
                      '\n주민등록번호, 주소, 이메일, 결제 정보는 수집하지 않습니다.',
                    ),
                  ),
                  const LegalSection(
                    title: '2. 수집·이용 목적',
                    body: Text(
                      '수업 운영과 출결 관리, 숙제 확인, 성적 안내, 보호자 안내에만 사용합니다. 광고나 마케팅 목적으로 이용하지 않습니다. 수업 영상 시청 기록은 온라인 수강 출결 확인에만 사용합니다. 기기 알림 토큰은 공지·숙제·성적·출결 안내 알림을 보내는 데에만 사용합니다.',
                    ),
                  ),
                  const LegalSection(
                    title: '3. 보관 기간',
                    body: Text(
                      '재원 기간 동안 보관하고, 퇴원한 날로부터 12개월이 지나면 파기합니다. 숙제 제출물(사진·영상)은 제출일로부터 12개월이 지나면 삭제됩니다.'
                      '\n계정을 삭제하면 로그인 정보(전화번호·비밀번호)는 즉시 파기합니다. 출석·숙제·성적 등 학습 기록은 학원 운영을 위해 위 기간 동안 보관한 뒤 파기합니다.'
                      '\n기기 알림 토큰은 로그아웃하면 즉시 파기합니다. 앱을 삭제한 경우 다음 알림 발송 때 무효로 확인되는 즉시 파기하며, 계정이 삭제되면 함께 파기합니다.',
                    ),
                  ),
                  const LegalSection(
                    title: '4. 제3자 제공과 처리 위탁',
                    body: Text(
                      '수집한 정보를 제3자에게 제공하지 않습니다. 다만 서비스 운영을 위해 다음 사업자에게 처리를 위탁합니다.'
                      '\n· Amazon Web Services, Inc.: 서버 운영, 숙제 제출물·첨부 파일 보관(서울 리전)'
                      '\n· Supabase, Inc.: 데이터베이스 운영(서울 리전)'
                      '\n앱 알림 발송은 Google LLC(Firebase Cloud Messaging)에 위탁합니다. 알림에는 알림 종류와 학생 이름만 담기며 성적·숙제 내용은 포함되지 않습니다.',
                    ),
                  ),
                  // 앱 푸시 알림(D). 「거부 방법」의 스위치는 내 정보의 「알림 받기」다.
                  // 「최대 4주」는 FCM 의 기본 보관 기간(TTL 28일)이다.
                  const LegalSection(
                    title: '5. 개인정보의 국외 이전',
                    body: Text(
                      '· 이전받는 자: Google LLC(미국) · 문의처 https://support.google.com/policies/contact/general_privacy_form'
                      '\n· 이전 항목: 기기 알림 토큰, 알림 문구(알림 종류·학생 이름)'
                      '\n· 이전 일시·방법: 알림이 생길 때마다 네트워크로 전송'
                      '\n· 이용 목적: 앱 푸시 알림 발송'
                      '\n· 보유 기간: 발송 완료 시까지(전달되지 않은 알림은 최대 4주)'
                      '\n· 거부 방법: 앱 「내 정보」에서 알림을 끄면 이전되지 않으며, 알림을 꺼도 서비스 이용에 제한이 없습니다.',
                    ),
                  ),
                  const LegalSection(
                    title: '6. 이용자의 권리',
                    body: Text(
                      '본인 또는 보호자는 언제든지 자신과 자녀의 정보 열람·정정·삭제를 요청할 수 있습니다. 요청은 아래 문의처로 연락해 주시면 처리합니다.'
                      '\n계정은 앱과 웹의 「내 정보」에서 「계정 삭제」를 눌러 직접 삭제할 수 있습니다. 안내: $accountDeletionUrl',
                    ),
                  ),
                  const LegalSection(
                    title: '7. 안전성 확보 조치',
                    body: Text(
                      '비밀번호는 복호화할 수 없는 형태로 저장하며, 학생·보호자·강사의 권한에 따라 열람 범위를 분리합니다. 보호자는 본인에게 연결된 자녀의 정보만 열람할 수 있습니다.',
                    ),
                  ),
                  const LegalSection(
                    title: '8. 개인정보 보호책임자와 문의처',
                    body: Text(
                      '$legalOperator · 대표·개인정보 보호책임자 $legalOfficer · $legalPhone',
                    ),
                  ),
                  const LegalSection(
                    title: '9. 시행일',
                    body: Text('이 방침은 2026년 10월 3일부터 시행합니다.'),
                  ),
                  Padding(
                    padding: const EdgeInsets.only(top: 32),
                    // 내 정보(P-5·S-7)에서 push 로 열렸으면 거기로 돌아간다.
                    // 로그인 화면에서는 go 로 왔으니 쌓인 게 없다.
                    child: (GoRouter.maybeOf(context)?.canPop() ?? false)
                        ? BackLink(label: '돌아가기', onTap: context.pop)
                        : BackLink(onTap: () => context.go(AppRoutes.login)),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
