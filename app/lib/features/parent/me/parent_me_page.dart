import 'package:flutter/material.dart';

import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';

import 'package:academy_app/shared/lib/phone.dart';

import '../../../core/push/push_setting.dart';
import '../../../shared/widgets/account_footer.dart';
import '../../../shared/widgets/push_setting_tile.dart';
import '../../../shared/widgets/app_card.dart';
import '../../../shared/widgets/app_text_field.dart';
import '../../../shared/widgets/form_error.dart';
import '../../../shared/widgets/full_screen_loader.dart';
import '../../../shared/widgets/home_layout.dart';
import '../../../shared/widgets/reappear_reload.dart';
import '../../../shared/widgets/section.dart';
import '../../../shared/widgets/sub_page.dart';
import '../../../shared/widgets/submit_button.dart';
import 'parent_me_data.dart';

/// P-5 내 정보. 웹 정본은 `frontend/src/routes/parent/ParentMePage.tsx` 다.
/// 자녀 목록과 연락처 변경.
///
/// **로그아웃을 지우지 마라**(14-7) — 학부모의 로그아웃은 여기 하나다. 그래서
/// 내 정보를 못 받았을 때도 **로그아웃 줄은 그린다.** 웹은 받는 동안 화면 전체가
/// 「불러오는 중」이라, 망이 끊긴 채면 로그아웃할 방법이 사라진다.
class ParentMePage extends StatefulWidget {
  const ParentMePage({
    super.key,
    required this.controller,
    required this.onLogout,
    required this.onDeleteAccount,
    required this.pushSetting,
  });

  final ParentMeController controller;
  final Future<void> Function() onLogout;

  /// 계정 삭제(2026-10-03, 스토어 요구). `AuthController.deleteAccount`.
  final Future<void> Function(String password) onDeleteAccount;

  /// 알림 받기 스위치. 본문과 따로 받는다([PushSettingTile]).
  final PushSettingController pushSetting;

  @override
  State<ParentMePage> createState() => _ParentMePageState();
}

class _ParentMePageState extends State<ParentMePage>
    with ReappearReload<ParentMePage> {
  final _phone = TextEditingController();

  /// 마지막으로 코드가 넣은 값. 이것과 다르면 사람이 고친 것이다.
  String _filled = '';
  bool _edited = false;
  bool _pending = false;
  bool _done = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _phone.addListener(_onTyped);
    widget.controller.addListener(_onChanged);
    _fillFromData();
    widget.controller.load();
  }

  @override
  void didUpdateWidget(covariant ParentMePage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_onChanged);
      widget.controller.addListener(_onChanged);
      _edited = false;
      _fillFromData();
      widget.controller.load();
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_onChanged);
    _phone.dispose();
    super.dispose();
  }

  @override
  void onReappear() => widget.controller.load();

  void _onChanged() {
    if (!mounted) return;
    _fillFromData();
    setState(() {});
  }

  /// 사람이 아직 안 고쳤으면 서버의 번호를 칸에 채운다. 고치는 중에 새로고침이
  /// 와도 입력을 덮지 않는다.
  void _fillFromData() {
    final me = widget.controller.data;
    if (me == null || _edited) return;
    _setPhone(formatPhone(me.phone));
  }

  void _setPhone(String value) {
    _filled = value;
    _phone.text = value;
  }

  /// 고치기 시작하면 「변경되었습니다」를 내린다(웹과 같다).
  void _onTyped() {
    if (_phone.text == _filled) return;
    _edited = true;
    if (_done) setState(() => _done = false);
  }

  Future<void> _submit() async {
    setState(() {
      _pending = true;
      _error = null;
      _done = false;
    });
    try {
      final updated = await widget.controller.repository.changePhone(
        _phone.text,
      );
      if (!mounted) return;
      _edited = false;
      _setPhone(formatPhone(updated.phone));
      setState(() => _done = true);
      // 이름·자녀 목록도 새 응답과 맞춘다.
      widget.controller.reload();
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } catch (_) {
      if (mounted) setState(() => _error = '연락처를 변경하지 못했습니다.');
    } finally {
      if (mounted) setState(() => _pending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final me = c.data;
    return SubPageScroll(
      role: '학부모',
      onRefresh: c.refresh,
      children: [
        if (me == null) ...[
          const PageTitle(title: '내 정보'),
          if (c.status == HomeStatus.error)
            HomeErrorView(message: c.error, onRetry: c.refresh)
          else
            const SizedBox(height: 160, child: FullScreenLoader()),
        ] else ...[
          PageTitle(title: '${me.name} 님'),
          SectionHead(
            tone: SectionTone.brand,
            title: '자녀',
            count: me.children.length,
          ),
          TintBlock(
            tone: SectionTone.brand,
            children: [
              if (me.children.isEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 14,
                    vertical: 16,
                  ),
                  child: Text(
                    '연결된 자녀가 없습니다. 선생님께 문의해 주세요.',
                    style: TextStyle(
                      fontSize: 14,
                      color: AppColors.brand700.withValues(alpha: 0.7),
                    ),
                  ),
                )
              else
                for (final child in me.children)
                  Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 12,
                    ),
                    child: Text(
                      child.name,
                      style: const TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: AppColors.brand950,
                      ),
                    ),
                  ),
            ],
          ),
          const SizedBox(height: 20),
          const SectionHead(tone: SectionTone.neutral, title: '연락처 변경'),
          AppCard(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                AppTextField(
                  label: '전화번호',
                  controller: _phone,
                  hint: '이 번호가 로그인 아이디입니다. 바꾸면 다음 로그인부터 새 번호를 쓰세요.',
                  keyboardType: TextInputType.phone,
                  inputFormatters: [PhoneInputFormatter()],
                  onSubmitted: (_) {
                    if (!_pending) _submit();
                  },
                ),
                if (_error != null) ...[
                  const SizedBox(height: 12),
                  FormError(message: _error),
                ],
                if (_done) ...[
                  const SizedBox(height: 12),
                  Container(
                    key: const Key('phone-changed'),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 8,
                    ),
                    decoration: BoxDecoration(
                      color: AppColors.emerald50,
                      borderRadius: BorderRadius.circular(AppRadii.lg),
                    ),
                    child: const Text(
                      '변경되었습니다. 이제 새 번호로 로그인하세요.',
                      style: TextStyle(
                        fontSize: 14,
                        color: AppColors.emerald800,
                      ),
                    ),
                  ),
                ],
                const SizedBox(height: 12),
                SubmitButton(
                  label: '변경하기',
                  pending: _pending,
                  onPressed: _pending ? null : _submit,
                ),
              ],
            ),
          ),
        ],
        const SizedBox(height: 20),
        PushSettingTile(controller: widget.pushSetting),
        const SizedBox(height: 20),
        AccountFooter(
          logoutKey: const Key('parent-logout'),
          onLogout: widget.onLogout,
          onDeleteAccount: widget.onDeleteAccount,
        ),
      ],
    );
  }
}
