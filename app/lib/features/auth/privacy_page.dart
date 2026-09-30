import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/router/app_router.dart';
import '../../core/theme/app_colors.dart';
import '../../shared/branding.dart';
import '../../shared/widgets/legal.dart';

/// C-3. 법정 고지라 로그인 전에도 읽을 수 있다.
///
/// **정본은 웹 `frontend/src/routes/auth/PrivacyPage.tsx` 다.**
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
                  const LegalDraftNotice(),
                  const LegalSection(
                    title: '1. 수집하는 항목',
                    body: Text.rich(
                      TextSpan(
                        children: [
                          TextSpan(
                            text: '학생: 이름, 전화번호, 소속 반. 보호자: 이름, 전화번호, 자녀 관계. 서비스 이용 과정에서 출석, 숙제 제출물(사진), 시험 결과, 학습 자료 열람 기록, 수업 영상 시청 기록(재생한 구간)이 생성됩니다.',
                          ),
                          TextSpan(
                            text: '\n앱을 이용하는 경우 알림 발송을 위해 기기 알림 토큰(기기를 식별하는 무작위 값)과 기기 종류(Android·iOS)가 수집됩니다.',
                          ),
                          TextSpan(
                            text: '\n주민등록번호, 주소, 이메일, 결제 정보는 수집하지 않습니다.',
                          ),
                        ],
                      ),
                    ),
                  ),
                  const LegalSection(
                    title: '2. 수집·이용 목적',
                    body: Text(
                      '수업 운영과 출결 관리, 숙제 확인과 피드백 제공, 성적 안내, 보호자 안내에만 사용합니다. 광고나 마케팅 목적으로 이용하지 않습니다. 수업 영상 시청 기록은 온라인 수강 출결 확인에만 사용합니다. 기기 알림 토큰은 공지·숙제·성적·출결 안내 알림을 보내는 데에만 사용합니다.',
                    ),
                  ),
                  const LegalSection(
                    title: '3. 보관 기간',
                    body: Text.rich(
                      TextSpan(
                        children: [
                          TextSpan(text: '재원 기간 동안 보관합니다. 퇴원 이후의 보관 기간은 '),
                          WidgetSpan(
                            alignment: PlaceholderAlignment.middle,
                            child: Pending(label: '확정 예정'),
                          ),
                          TextSpan(text: ' 입니다.'),
                          TextSpan(
                            text: '\n기기 알림 토큰은 로그아웃하면 즉시 파기합니다. 앱을 삭제한 경우 다음 알림 발송 때 무효로 확인되는 즉시 파기하며, 계정이 삭제되면 함께 파기합니다.',
                          ),
                        ],
                      ),
                    ),
                  ),
                  const LegalSection(
                    title: '4. 제3자 제공과 처리 위탁',
                    body: Text.rich(
                      TextSpan(
                        children: [
                          TextSpan(
                            text: '수집한 정보를 제3자에게 제공하지 않습니다. 다만 서비스 운영을 위해 클라우드 인프라 (서버·파일 보관)를 이용하며, 해당 사업자는 ',
                          ),
                          WidgetSpan(
                            alignment: PlaceholderAlignment.middle,
                            child: Pending(label: '확정 예정'),
                          ),
                          TextSpan(text: ' 입니다.'),
                          TextSpan(
                            text: '\n앱 알림 발송은 Google LLC(Firebase Cloud Messaging)에 위탁합니다. 알림에는 알림 종류와 학생 이름만 담기며 성적·숙제 내용은 포함되지 않습니다.',
                          ),
                        ],
                      ),
                    ),
                  ),
                  // 앱 푸시 알림(D). 「거부 방법」의 스위치는 내 정보의 「알림 받기」다.
                  const LegalSection(
                    title: '5. 개인정보의 국외 이전',
                    body: Text.rich(
                      TextSpan(
                        children: [
                          TextSpan(text: '· 이전받는 자: Google LLC(미국) · 문의처 '),
                          WidgetSpan(
                            alignment: PlaceholderAlignment.middle,
                            child: Pending(label: 'Google 문의처 확정 예정'),
                          ),
                          TextSpan(
                            text:
                                '\n· 이전 항목: 기기 알림 토큰, 알림 문구(알림 종류·학생 이름)'
                                '\n· 이전 일시·방법: 알림이 생길 때마다 네트워크로 전송'
                                '\n· 이용 목적: 앱 푸시 알림 발송'
                                '\n· 보유 기간: 발송 완료 시까지(전달되지 않은 알림은 최대 4주 ',
                          ),
                          WidgetSpan(
                            alignment: PlaceholderAlignment.middle,
                            child: Pending(label: '확인 필요'),
                          ),
                          TextSpan(
                            text: ')\n· 거부 방법: 앱 「내 정보」에서 알림을 끄면 이전되지 않으며, 알림을 꺼도 서비스 이용에 제한이 없습니다.',
                          ),
                        ],
                      ),
                    ),
                  ),
                  const LegalSection(
                    title: '6. 이용자의 권리',
                    body: Text(
                      '본인 또는 보호자는 언제든지 자신과 자녀의 정보 열람·정정·삭제를 요청할 수 있습니다. 요청은 담당 강사에게 연락해 주시면 처리합니다.',
                    ),
                  ),
                  const LegalSection(
                    title: '7. 안전성 확보 조치',
                    body: Text(
                      '비밀번호는 복호화할 수 없는 형태로 저장하며, 학생·보호자·강사의 권한에 따라 열람 범위를 분리합니다. 보호자는 본인에게 연결된 자녀의 정보만 열람할 수 있습니다.',
                    ),
                  ),
                  const LegalSection(
                    title: '8. 문의처',
                    body: Text.rich(
                      TextSpan(
                        children: [
                          TextSpan(text: '$academyName · '),
                          WidgetSpan(
                            alignment: PlaceholderAlignment.middle,
                            child: Pending(label: '담당자·연락처 확정 예정'),
                          ),
                        ],
                      ),
                    ),
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
