import 'package:flutter/material.dart';

import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../shared/score/exam_dday_list.dart';
import '../../../shared/score/score_section_list.dart';
import '../../../core/push/push_setting.dart';
import '../../../shared/widgets/account_footer.dart';
import '../../../shared/widgets/push_setting_tile.dart';
import '../../../shared/widgets/full_screen_loader.dart';
import '../../../shared/widgets/home_layout.dart';
import '../../../shared/widgets/reappear_reload.dart';
import '../../../shared/widgets/section.dart';
import '../../../shared/widgets/sub_page.dart';
import 'student_score_data.dart';

/// S-7 「내 정보 · 성적」. 웹 정본은 `frontend/src/routes/student/StudentScorePage.tsx`.
///
/// **탭 라벨만 「성적」이고 제목은 「내 정보 · 성적」이다.** 학생의 **유일한
/// 로그아웃 경로**다(14-7) — 공용 PC 나 형제 폰에서 계정을 내려놓을 곳이 여기뿐이라,
/// 성적을 못 받았어도 맨 아래 줄은 그린다.
///
/// 학부모 화면(P-4)과 **같은 응답·같은 위젯**([ScoreSectionList])을 쓴다.
class StudentScoresPage extends StatefulWidget {
  const StudentScoresPage({
    super.key,
    required this.controller,
    required this.onLogout,
    required this.onDeleteAccount,
    required this.pushSetting,
  });

  final StudentScoreController controller;
  final Future<void> Function() onLogout;

  /// 계정 삭제(2026-10-03, 스토어 요구). `AuthController.deleteAccount`.
  final Future<void> Function(String password) onDeleteAccount;

  /// 알림 받기 스위치. 본문과 따로 받는다([PushSettingTile]).
  final PushSettingController pushSetting;

  @override
  State<StudentScoresPage> createState() => _StudentScoresPageState();
}

class _StudentScoresPageState extends State<StudentScoresPage>
    with ReappearReload<StudentScoresPage> {
  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_onChanged);
    widget.controller.load();
  }

  @override
  void didUpdateWidget(covariant StudentScoresPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_onChanged);
      widget.controller.addListener(_onChanged);
      widget.controller.load();
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_onChanged);
    super.dispose();
  }

  void _onChanged() {
    if (mounted) setState(() {});
  }

  @override
  void onReappear() => widget.controller.load();

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final data = c.data;
    return SubPageScroll(
      role: '학생',
      onRefresh: c.refresh,
      children: [
        const PageTitle(title: '내 정보 · 성적'),
        if (data == null)
          c.status == HomeStatus.error
              ? HomeErrorView(message: c.error, onRetry: c.refresh)
              : const SizedBox(height: 160, child: FullScreenLoader())
        else ...[
          _MeSection(me: data.me),
          if (data.exams.isNotEmpty) ...[
            const SizedBox(height: 20),
            ExamDdayList(schedules: data.exams),
          ],
          const SizedBox(height: 20),
          ScoreSectionList(data: data.scores),
        ],
        const SizedBox(height: 20),
        PushSettingTile(controller: widget.pushSetting),
        const SizedBox(height: 20),
        AccountFooter(
          logoutKey: const Key('student-logout'),
          onLogout: widget.onLogout,
          onDeleteAccount: widget.onDeleteAccount,
        ),
      ],
    );
  }
}

class _MeSection extends StatelessWidget {
  const _MeSection({required this.me});

  final StudentMe me;

  @override
  Widget build(BuildContext context) {
    final phone = me.phone;
    return Column(
      key: const Key('student-me'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SectionHead(tone: SectionTone.neutral, title: '내 정보'),
        TintBlock(
          tone: SectionTone.neutral,
          children: [
            Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    me.name,
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w800,
                      letterSpacing: -0.32,
                      color: AppColors.brand900,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    me.classRooms.isEmpty
                        ? '배정된 반이 없습니다'
                        : me.classRooms.join(' · '),
                    style: const TextStyle(
                      fontSize: 13,
                      color: AppColors.slate600,
                    ),
                  ),
                  if (phone != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      phone,
                      style: const TextStyle(
                        fontSize: 12,
                        color: AppColors.slate400,
                        fontFeatures: kTabularFigures,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            // 학생이 알아야 선생님께 문의해 조치가 된다.
            if (!me.parentLinked)
              Container(
                key: const Key('parent-not-linked'),
                color: AppColors.amber50,
                padding: const EdgeInsets.symmetric(
                  horizontal: 14,
                  vertical: 10,
                ),
                child: const Text(
                  '보호자 계정이 아직 연결되지 않았습니다. 선생님께 문의해 주세요.',
                  style: TextStyle(fontSize: 12, color: AppColors.amber800),
                ),
              ),
          ],
        ),
      ],
    );
  }
}
