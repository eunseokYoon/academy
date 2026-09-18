import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/router/app_router.dart';
import '../../core/theme/app_colors.dart';
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
                            text:
                                '학생: 이름, 전화번호, 소속 반. 보호자: 이름, 전화번호, 자녀 관계. 서비스 이용 과정에서 출석, 숙제 제출물(사진), 시험 결과, 학습 자료 열람 기록이 생성됩니다.',
                          ),
                          TextSpan(text: '\n주민등록번호, 주소, 이메일, 결제 정보는 수집하지 않습니다.'),
                        ],
                      ),
                    ),
                  ),
                  const LegalSection(
                    title: '2. 수집·이용 목적',
                    body: Text(
                      '수업 운영과 출결 관리, 숙제 확인과 피드백 제공, 성적 안내, 보호자 안내에만 사용합니다. 광고나 마케팅 목적으로 이용하지 않습니다.',
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
                            text:
                                '수집한 정보를 제3자에게 제공하지 않습니다. 다만 서비스 운영을 위해 클라우드 인프라 (서버·파일 보관)를 이용하며, 해당 사업자는 ',
                          ),
                          WidgetSpan(
                            alignment: PlaceholderAlignment.middle,
                            child: Pending(label: '확정 예정'),
                          ),
                          TextSpan(text: ' 입니다.'),
                        ],
                      ),
                    ),
                  ),
                  const LegalSection(
                    title: '5. 이용자의 권리',
                    body: Text(
                      '본인 또는 보호자는 언제든지 자신과 자녀의 정보 열람·정정·삭제를 요청할 수 있습니다. 요청은 담당 강사에게 연락해 주시면 처리합니다.',
                    ),
                  ),
                  const LegalSection(
                    title: '6. 안전성 확보 조치',
                    body: Text(
                      '비밀번호는 복호화할 수 없는 형태로 저장하며, 학생·보호자·강사의 권한에 따라 열람 범위를 분리합니다. 보호자는 본인에게 연결된 자녀의 정보만 열람할 수 있습니다.',
                    ),
                  ),
                  const LegalSection(
                    title: '7. 문의처',
                    body: Text.rich(
                      TextSpan(
                        children: [
                          TextSpan(text: '남지원영어LAB · '),
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
                    child: _BackLink(onTap: () => context.go(AppRoutes.login)),
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

class _BackLink extends StatelessWidget {
  const _BackLink({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: const Text(
        '로그인으로 돌아가기',
        style: TextStyle(
          fontSize: 14,
          color: AppColors.slate500,
          decoration: TextDecoration.underline,
        ),
      ),
    );
  }
}
